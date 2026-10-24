/*
 * Scrambler - a dirt bike for Vanilla Wheels.
 * Copyright (C) 2026 Chunkworks
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.scrambler.gametest;

import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Docking;
import com.chunkworks.vanillawheels.domain.Footprint;
import com.chunkworks.vanillawheels.domain.Input;
import com.chunkworks.vanillawheels.domain.Jump;
import com.chunkworks.vanillawheels.domain.Linkage;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Scrambler on a headless server: its profile is the bike (one seat, a horse's jump, a lean, a
 * fork that steers with its wheel in it, two wheels that roll and two to build it with, red paint,
 * on either lift); six steel ingots craft its chassis; a full bar clears a two-block wall and a tap
 * does not; it leans into its turns and stands upright parked; a full jump off a ledge lands its
 * rider unhurt.
 *
 * <p>The runway is 48 long, 9 high and 15 wide, a dirt floor laid 4 deep.
 */
@GameTestHolder("scrambler")
@PrefixGameTestTemplate(false)
public final class ScramblerGameTests {
    private static final int FLOOR = 4;
    private static final ResourceLocation BIKE = ResourceLocation.fromNamespaceAndPath("scrambler", "scrambler");
    private static final Input GAS = new Input(1, 0, false, true, true);
    /** The wall's near face, x; the bikes start at x 3 and are flat out well before it. */
    private static final int WALL = 30;

    public ScramblerGameTests() {}

    private static void layFloor(GameTestHelper helper) {
        int length = (int) helper.getBounds().getXsize(), width = (int) helper.getBounds().getZsize();
        for (int x = 0; x < length; x++) {
            for (int z = 0; z < width; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }
    }

    /** effects: returns a fuelled Scrambler at (x, y, z) facing +x, in the level */
    private static Vehicle bike(GameTestHelper helper, double x, double y, double z) {
        Vehicle v = Vehicle.create(helper.getLevel(), BIKE, helper.absoluteVec(new Vec3(x, y, z)), -90.0f);
        helper.assertTrue(v != null, "the Scrambler is registered");
        v.setFuel(v.tank().capacity());
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /** effects: returns a villager in the Scrambler's seat: the server then holds the wheel, and drives by script */
    private static Villager rider(GameTestHelper helper, Vehicle v) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(villager != null, "a villager");
        villager.moveTo(v.getX(), v.getY(), v.getZ());
        helper.getLevel().addFreshEntity(villager);
        helper.assertTrue(villager.startRiding(v, true) && v.getControllingPassenger() == villager, "the villager at the bars");
        return villager;
    }

    private static double height(GameTestHelper helper, Vehicle v) {
        return v.getY() - helper.absoluteVec(new Vec3(0, FLOOR, 0)).y;
    }

    private static double nose(GameTestHelper helper, Vehicle v) {
        return helper.relativeVec(v.position()).x + v.profile().body().length() / 2.0;
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theProfileIsTheDirtBike(GameTestHelper helper) {
        Optional<VehicleProfile> found = VanillaWheels.profile(helper.getLevel().registryAccess(), BIKE).map(h -> h.value());
        helper.assertTrue(found.isPresent(), "scrambler:scrambler is in the vehicle registry");
        VehicleProfile p = found.get();
        helper.assertValueEqual(p.seats().size(), 1, "one seat");
        helper.assertTrue(p.seats().get(0).driver(), "the rider's");
        helper.assertTrue(p.jump().isPresent() && p.jump().get().strength() == Jump.AVERAGE_HORSE, "an average horse's jump");
        helper.assertTrue(p.lean().isPresent() && p.lean().get().parked() == 0.0, "it leans, and stands upright parked");
        helper.assertTrue(!p.wheels().drawn() && p.wheels().positions().size() == 2, "two wheels in a line, drawn by its own mesh");
        Linkage links = p.linkage();
        helper.assertValueEqual(links.joints().size(), 3, "a fork and two wheels");
        helper.assertTrue(links.joints().get(0).kind() == Linkage.Kind.STEER && links.joints().get(1).parent() == 0, "the front wheel rolls in the fork");
        helper.assertTrue(Math.abs(Math.toDegrees(Math.acos(links.joints().get(0).axis().y())) - 26.0) < 0.5, "the fork raked 26 degrees, along the head tube");
        helper.assertValueEqual(p.wheelParts(), 2, "two wheels to build it");
        helper.assertTrue(p.paint().flatMap(VehicleProfile.Paint::factory).isPresent(), "a factory colour: undyed, it is red as received");
        helper.assertTrue(Docking.fits(Footprint.LONG, p.body().width(), p.body().length()) && Docking.fits(Footprint.SHORT, p.body().width(), p.body().length()),
                "it fits either lift's deck");
        Vehicle v = bike(helper, 7.5, FLOOR, 7.5);
        helper.assertTrue(v.canJump(), "it jumps");
        helper.assertTrue(v.getName().getString().equals("Scrambler"), "named: " + v.getName().getString());
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theChassisCraftsFromSixSteelIngots(GameTestHelper helper) {
        var steel = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("metalsandmaterials", "steel_ingot"));
        helper.assertTrue(steel != Items.AIR, "Metals and Materials' steel ingot is here");
        // "  S" / "SSS" / "S S": a frame of six steel ingots, slots 2 to 6 and 8.
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i : new int[] {2, 3, 4, 5, 6, 8}) {
            grid.set(i, new ItemStack(steel));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "the frame of steel crafts something");
        helper.assertValueEqual(recipe.get().id(), ResourceLocation.fromNamespaceAndPath("scrambler", "scrambler_chassis"), "the Scrambler's chassis recipe");
        ItemStack result = recipe.get().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModContent.CHASSIS.get()), "a chassis: " + result);
        helper.assertValueEqual(VanillaWheels.vehicleOf(result).orElse(null), BIKE, "for the Scrambler");
        helper.succeed();
    }

    /**
     * Two Scramblers ride at a two-block wall, each asking to jump when its nose is four ticks of
     * travel from it, where a full bar is two blocks up as the nose arrives. The full bar clears it and
     * lands beyond; the tap rises under a block and is stopped.
     */
    @GameTest(template = "runway", timeoutTicks = 200)
    public void aFullBarClearsATwoBlockWallAndATapDoesNot(GameTestHelper helper) {
        layFloor(helper);
        for (int z = 0; z < 15; z++) {
            helper.setBlock(new BlockPos(WALL, FLOOR, z), Blocks.STONE);
            helper.setBlock(new BlockPos(WALL, FLOOR + 1, z), Blocks.STONE);
        }
        Vehicle full = bike(helper, 3.5, FLOOR, 4.5);
        Vehicle tap = bike(helper, 3.5, FLOOR, 10.5);
        rider(helper, full);
        rider(helper, tap);
        full.setScriptedInput(GAS);
        tap.setScriptedInput(GAS);
        boolean[] asked = new boolean[2];
        double[] highest = new double[2];
        helper.onEachTick(() -> {
            Vehicle[] bikes = {full, tap};
            for (int i = 0; i < 2; i++) {
                highest[i] = Math.max(highest[i], height(helper, bikes[i]));
                if (!asked[i] && WALL - nose(helper, bikes[i]) <= 4.0 * Math.max(0.2, Math.abs(bikes[i].speed()))) {
                    bikes[i].onPlayerJump(i == 0 ? 100 : 0);
                    asked[i] = true;
                }
            }
        });
        helper.runAfterDelay(120, () -> {
            double past = helper.relativeVec(full.position()).x;
            double held = helper.relativeVec(tap.position()).x;
            helper.assertTrue(asked[0] && asked[1], "both asked to jump");
            helper.assertTrue(past > WALL + 1 + full.profile().body().length() / 2.0, "the full bar cleared the wall: x " + past + ", highest " + highest[0]);
            helper.assertTrue(height(helper, full) < 0.05, "and landed: " + height(helper, full));
            helper.assertTrue(held < WALL, "the tap was stopped at the wall: x " + held + ", highest " + highest[1]);
            helper.assertTrue(highest[0] > 3.0, "a full bar rises over three blocks: " + highest[0]);
            helper.succeed();
        });
    }

    /** Ridden, it leans right into a right turn and left into a left; parked, it stands upright (its stand is stowed). */
    @GameTest(template = "runway", timeoutTicks = 200)
    public void itLeansIntoItsTurnsAndStandsUprightParked(GameTestHelper helper) {
        layFloor(helper);
        Vehicle ridden = bike(helper, 10.5, FLOOR, 7.5);
        Vehicle parked = bike(helper, 40.5, FLOOR, 7.5);
        rider(helper, ridden);
        // The deepest lean each way while turning: right for fifteen ticks, then left; a sample at one tick
        // would read whatever the runway's edge had done to it by then.
        double[] roll = new double[2];
        int[] turning = {0};
        helper.onEachTick(() -> {
            double r = ridden.suspension(1.0f).roll();
            if (turning[0] == 1) roll[0] = Math.max(roll[0], r);
            if (turning[0] == -1) roll[1] = Math.min(roll[1], r);
        });
        helper.startSequence()
                .thenExecute(() -> ridden.setScriptedInput(GAS))
                .thenIdle(20)
                .thenExecute(() -> {
                    ridden.setScriptedInput(new Input(1, 1, false, true, true));
                    turning[0] = 1;
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    ridden.setScriptedInput(new Input(1, -1, false, true, true));
                    turning[0] = -1;
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    turning[0] = 0;
                    helper.assertTrue(roll[0] > Math.toRadians(10), "leaning right into a right turn: " + Math.toDegrees(roll[0]));
                    helper.assertTrue(roll[1] < -Math.toRadians(10), "leaning left into a left turn: " + Math.toDegrees(roll[1]));
                    double stand = parked.suspension(1.0f).roll();
                    helper.assertTrue(Math.abs(stand) < Math.toRadians(0.5), "parked, upright: " + Math.toDegrees(stand));
                })
                .thenSucceed();
    }

    /** A full jump off a three-block ledge, a fall of over six, lands its rider and itself unhurt. The tall runway: 16 high, room for the arc. */
    @GameTest(template = "tall", timeoutTicks = 160)
    public void aFullJumpOffALedgeLandsItsRiderUnhurt(GameTestHelper helper) {
        layFloor(helper);
        for (int x = 0; x <= 12; x++) {
            for (int z = 5; z < 10; z++) {
                for (int y = FLOOR; y < FLOOR + 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        Vehicle v = bike(helper, 4.5, FLOOR + 3, 7.5);
        Villager villager = rider(helper, v);
        int condition = v.condition();
        v.setScriptedInput(GAS);
        boolean[] asked = {false};
        double[] highest = {0.0};
        helper.onEachTick(() -> {
            highest[0] = Math.max(highest[0], height(helper, v));
            if (!asked[0] && 12.0 - nose(helper, v) <= 0.5) {
                v.onPlayerJump(100);
                asked[0] = true;
            }
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(asked[0], "it jumped at the edge");
            helper.assertTrue(highest[0] > 6.0, "from over six blocks above the floor: " + highest[0]);
            helper.assertTrue(height(helper, v) < 0.05, "down on the floor: " + height(helper, v));
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth() && villager.getVehicle() == v, "the rider unhurt and aboard: " + villager.getHealth());
            helper.assertTrue(v.condition() == condition, "the bike unhurt: " + v.condition());
            helper.succeed();
        });
    }
}
