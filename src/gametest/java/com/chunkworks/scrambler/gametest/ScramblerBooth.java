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

import com.chunkworks.vanillawheels.Vehicle;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Scrambler on film, in a flat world at noon: side on in stock red and painted blue, the
 * front-left quarter; ridden in third person, leaning into a right turn and its fork steered; the
 * jump on the real jump key (devtools/booth/xkey.py, a key event as a hand makes one): the jump bar
 * filling where the experience bar was, then the bike at the top of its arc; and the jump key held
 * while driving into a turn, which never drifts it. One {@code booth: PASS} or {@code booth: FAIL}
 * line per check; the Gradle task reads them. Client only, active only under
 * {@code scrambler.photobooth}.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class ScramblerBooth {
    private ScramblerBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Scrambler booth");
    private static final boolean ACTIVE = Boolean.getBoolean("scrambler.photobooth");
    private static final ResourceLocation BIKE = ResourceLocation.fromNamespaceAndPath("scrambler", "scrambler");

    private enum Phase { TITLE, LOADING, PLACING, SETTLING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    private static final int HOLD = 100;
    private static final int SETTLE = 60;
    /** Where the player stands; the bike stands AHEAD blocks south of it, side on. */
    private static final double X = 0.5;
    private static final double Z = 0.5;
    private static final double AHEAD = 4.5;

    private static boolean muted = false;
    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static UUID bike;
    private static double ground;
    /** The jump: the highest the ridden bike rose over the ground, and whether its peak has been filmed. */
    private static double peak = 0.0;
    private static boolean watchingJump = false;
    private static boolean shotPeak = false;
    /** Whether the bike drifted at any tick the jump key was held in a turn. */
    private static boolean watchingDrift = false;
    private static boolean drifted = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!muted) {
            // Silent from the first tick, before the title music: someone may be listening to music while these run.
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            muted = true;
        }
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    phase = Phase.LOADING;
                    createWorld(mc);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level != null && mc.player != null && mc.screen == null && server != null
                        && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.PLACING;
                    mc.options.hideGui = true;
                    // No tutorial toasts over the photographs.
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    steps = plan(mc);
                    onServer(mc, ScramblerBooth::setUp);
                }
            }
            case PLACING -> {
                if (mc.player != null && mc.player.onGround() && mc.player.distanceToSqr(X, mc.player.getY(), Z) < 0.25 && clientBike(mc) != null) {
                    phase = Phase.SETTLING;
                    tick = 0;
                }
            }
            case SETTLING -> {
                if (tick++ % 10 == 0 && (count(mc, ScramblerBooth::red) > 300 || tick > 400)) {
                    phase = Phase.RUNNING;
                    tick = 0;
                }
            }
            case RUNNING -> {
                watch(mc);
                for (Step step : steps) {
                    if (step.at() == tick) {
                        step.action().run();
                    }
                }
                tick++;
            }
            case DONE -> { }
        }
    }

    /** effects: follows the ridden bike's jump to its peak, filming it there, and whether a held jump key drifted it */
    private static void watch(Minecraft mc) {
        if (!(mc.player != null && mc.player.getVehicle() instanceof Vehicle v)) {
            return;
        }
        if (watchingJump) {
            double h = v.getY() - ground;
            if (h > peak) {
                peak = h;
            } else if (peak > 1.0 && !shotPeak) {
                shotPeak = true;
                shoot(mc, "booth-jump-peak");
            }
        }
        if (watchingDrift && v.drifting()) {
            drifted = true;
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings("Scrambler booth", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("scrambler-booth", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    /** Noon; the player on the grass facing south; the bike ahead, side on, facing east, full. */
    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(6000L);
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        ground = level.getMinBuildHeight() + 4;
        sp.teleportTo(level, X, ground, Z, 0.0f, 12.0f);
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        fresh(sp, new Vec3(X, ground, Z + AHEAD), -90.0f);
    }

    /** effects: replaces the bike with a fresh one, full, at {@code at} facing {@code yaw} */
    private static void fresh(ServerPlayer sp, Vec3 at, float yaw) {
        ServerLevel level = sp.serverLevel();
        if (bike != null && level.getEntity(bike) instanceof Vehicle old) {
            old.discard();
        }
        Vehicle v = Vehicle.create(level, BIKE, at, yaw);
        if (v == null) {
            LOG.error("booth: FAIL the Scrambler profile is registered -- Vehicle.create returned null");
            return;
        }
        v.setFuel(v.tank().capacity());
        level.addFreshEntity(v);
        bike = v.getUUID();
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int t = HOLD;
        s.add(new Step(t, () -> {
            int red = count(mc, ScramblerBooth::red);
            shoot(mc, "booth-side");
            verdict("side on, the stock bike shows its red bodywork", () -> red > 800 ? null : "red pixels " + red);
            verdict("parked, it stands upright", () -> clientBike(mc) != null && Math.abs(clientBike(mc).suspension(1.0f).roll()) < 0.01
                    ? null : "roll " + (clientBike(mc) == null ? null : clientBike(mc).suspension(1.0f).roll()));
        }));
        s.add(new Step(t += 2, () -> withBike(mc, v -> v.setPaint(DyeColor.BLUE))));
        s.add(new Step(t += SETTLE / 2, () -> {
            int blue = count(mc, ScramblerBooth::blue);
            int red = count(mc, ScramblerBooth::red);
            shoot(mc, "booth-side-blue");
            verdict("painted blue, the bodywork is blue", () -> blue > 500 ? null : "blue pixels " + blue);
            verdict("and the red is gone", () -> red < 150 ? null : "red pixels " + red);
        }));
        // A fresh stock bike from the front-left quarter, a little above it.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            fresh(sp, new Vec3(X, ground, Z + AHEAD), -90.0f);
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            // The front-left quarter (the bike faces east, its left is north), a modeller's narrow field of view.
            sp.teleportTo(sp.serverLevel(), X + 3.6, ground + 1.2, Z + AHEAD - 3.3, 47.5f, 22.0f);
        })));
        s.add(new Step(t += 2, () -> mc.options.fov().set(40)));
        s.add(new Step(t += SETTLE, () -> {
            shoot(mc, "booth-three-quarter");
            mc.options.fov().set(70);
        }));
        // Aboard, in third person from behind; W held as a hand holds it, then into a right turn.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            ServerLevel level = sp.serverLevel();
            if (level.getEntity(bike) instanceof Vehicle v) {
                sp.startRiding(v, true);
            }
        })));
        s.add(new Step(t += 20, () -> {
            verdict("the booth's player rides the Scrambler", () -> mc.player != null && mc.player.getVehicle() instanceof Vehicle v
                    && v.getControllingPassenger() == mc.player ? null : "riding " + (mc.player == null ? null : mc.player.getVehicle()));
            look(mc, 18.0f);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.options.keyUp.setDown(true);
        }));
        s.add(new Step(t += 30, () -> mc.options.keyRight.setDown(true)));
        s.add(new Step(t += 14, () -> {
            double roll = clientBike(mc) == null ? 0.0 : clientBike(mc).suspension(1.0f).roll();
            shoot(mc, "booth-lean");
            verdict("ridden into a right turn it leans right", () -> roll > Math.toRadians(10) ? null : "roll " + Math.toDegrees(roll) + " degrees");
        }));
        // The jump key held in the turn: never a drift on a bike (the key is the jump's).
        s.add(new Step(t += 2, () -> {
            watchingDrift = true;
            xkey("down", "space");
        }));
        s.add(new Step(t += 30, () -> {
            watchingDrift = false;
            xkey("up", "space");
            verdict("the jump key held in a turn never drifts it", () -> drifted ? "it drifted" : null);
        }));
        // Straight, then stop: brake to a stand.
        s.add(new Step(t += 2, () -> mc.options.keyRight.setDown(false)));
        s.add(new Step(t += 2, () -> {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(true);
        }));
        s.add(new Step(t += 40, () -> mc.options.keyDown.setDown(false)));
        // The fork steered, from in front, the bike standing with the stick held right.
        // The fork steered, from a camera over the front wheel looking back down at it, the stick held right.
        s.add(new Step(t += 20, () -> {
            Vehicle v = clientBike(mc);
            if (v != null) {
                Vec3 ahead = new Vec3(-Math.sin(Math.toRadians(v.getYRot())), 0.0, Math.cos(Math.toRadians(v.getYRot())));
                cameraAt(mc, v.position().add(ahead.scale(2.4)).add(0.0, 2.6, 0.0), v.getYRot() + 180.0f, 50.0f);
            }
            mc.options.keyRight.setDown(true);
        }));
        s.add(new Step(t += 10, () -> {
            float steer = clientBike(mc) == null ? 0.0f : clientBike(mc).steer();
            shoot(mc, "booth-steer");
            verdict("the stick held right steers the fork right", () -> steer > 0.2f ? null : "steer " + steer);
            mc.options.keyRight.setDown(false);
            cameraBack(mc);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            look(mc, 10.0f);
        }));
        // The jump, standing, on the real jump key: the bar where the experience bar was, then the arc's top.
        s.add(new Step(t += 30, () -> {
            mc.options.hideGui = false;
            ground = clientBike(mc) == null ? ground : clientBike(mc).getY();
            xkey("down", "space");
        }));
        s.add(new Step(t += 1, () -> {
            // The same moment from beside it, for the photo of it standing that the one at the top is set against;
            // shot a tick after the camera moves, which the frame drawn that tick does not yet show.
            mc.options.hideGui = true;
            sideCamera(mc);
        }));
        s.add(new Step(t += 1, () -> shoot(mc, "booth-jump-ground")));
        s.add(new Step(t += 1, () -> {
            cameraBack(mc);
            mc.options.hideGui = false;
        }));
        s.add(new Step(t += 5, () -> {
            float bar = mc.player == null ? 0.0f : mc.player.getJumpRidingScale();
            shoot(mc, "booth-jump-bar");
            verdict("the bike is the game's jumpable mount", () -> mc.player != null && mc.player.jumpableVehicle() instanceof Vehicle ? null : "no jumpable mount");
            verdict("holding the jump key fills the jump bar", () -> bar > 0.3f ? null : "bar " + bar);
        }));
        s.add(new Step(t += 3, () -> {
            watchingJump = true;
            peak = 0.0;
            mc.options.hideGui = true;
            sideCamera(mc);
            xkey("up", "space");
        }));
        s.add(new Step(t += 40, () -> {
            watchingJump = false;
            cameraBack(mc);
            LOG.info("booth: the jump rose {} blocks", String.format("%.2f", peak));
            verdict("let go, it jumps as a horse does: up over a block and a half", () -> peak > 1.5 ? null : "peak " + peak);
            verdict("and its top was filmed from beside it", () -> shotPeak ? null : "no peak shot");
        }));
        // First person, the bars and the front wheel.
        s.add(new Step(t += 2, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            look(mc, 40.0f);
        }));
        s.add(new Step(t += 20, () -> shoot(mc, "booth-first-person")));
        s.add(new Step(t += 20, () -> {
            LOG.info("booth: PASS all checks ran");
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    /** effects: turns the riding player to face the way the bike does, looking {@code pitch} degrees down */
    private static void look(Minecraft mc, float pitch) {
        if (mc.player != null && mc.player.getVehicle() instanceof Vehicle v) {
            mc.player.setYRot(v.getYRot());
            mc.player.yRotO = v.getYRot();
        }
        if (mc.player != null) {
            mc.player.setXRot(pitch);
            mc.player.xRotO = pitch;
        }
    }

    /**
     * effects: looks through a camera of this client's own, an armour stand never added to the level,
     * its eye at {@code eye} facing {@code yaw} and {@code pitch} degrees down: a fixed view of the bike
     * while its rider still works it
     */
    private static void cameraAt(Minecraft mc, Vec3 eye, float yaw, float pitch) {
        if (mc.level == null) {
            return;
        }
        ArmorStand stand = new ArmorStand(mc.level, eye.x, eye.y, eye.z);
        stand.setPos(eye.x, eye.y - stand.getEyeHeight(), eye.z);
        stand.xo = stand.getX();
        stand.yo = stand.getY();
        stand.zo = stand.getZ();
        stand.setInvisible(true);
        stand.setYRot(yaw);
        stand.yRotO = yaw;
        stand.setYHeadRot(yaw);
        stand.yHeadRotO = yaw;
        stand.setXRot(pitch);
        stand.xRotO = pitch;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(stand);
    }

    /** effects: looks at the ridden bike from its left, six and a half blocks off, the eye 2.6 up and level: room for the whole arc */
    private static void sideCamera(Minecraft mc) {
        Vehicle v = clientBike(mc);
        if (v == null) {
            return;
        }
        double yaw = Math.toRadians(v.getYRot());
        Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));   // the body's +X, its left
        cameraAt(mc, new Vec3(v.getX(), ground + 2.6, v.getZ()).add(left.scale(6.5)), v.getYRot() + 90.0f, -2.0f);
    }

    /** effects: looks through the player's own eyes again */
    private static void cameraBack(Minecraft mc) {
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
    }

    /** effects: returns this client's copy of the booth's bike, or null before it has one */
    @Nullable
    private static Vehicle clientBike(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof Vehicle v && e.getUUID().equals(bike)) {
                return v;
            }
        }
        return null;
    }

    /**
     * effects: starts devtools/booth/xkey.py pressing ({@code down}) or letting go ({@code up}) of
     * {@code keysym} on this client's display, and returns it; null, with a FAIL line, if it would
     * not start. The helper refuses a display with a window manager, so it never types on a desktop.
     */
    @Nullable
    private static Process xkey(String action, String keysym) {
        Path script = Path.of(System.getProperty("user.dir"), "..", "..", "devtools", "booth", "xkey.py").normalize();
        Path uv = Path.of(System.getProperty("user.home"), ".local", "bin", "uv");
        try {
            return new ProcessBuilder(Files.isExecutable(uv) ? uv.toString() : "uv", "run", "--no-project", "--with", "python-xlib",
                    "python", script.toString(), action, keysym).inheritIO().start();
        } catch (IOException e) {
            LOG.error("booth: FAIL the key helper starts ({} {}) -- {}", action, keysym, e.toString());
            return null;
        }
    }

    // --- reading the frame -----------------------------------------------

    /** The bodywork's red, lit or shaded: red well over green and blue, not near-black. */
    private static boolean red(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return r > 70 && r > g * 1.6 && r > b * 1.6;
    }

    /** Blue dye on the bodywork: blue well over red and over green; not the sky, whose red and green are high. */
    private static boolean blue(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return b > 70 && b > r * 1.6 && b > g * 1.2 && r < 110;
    }

    /** effects: returns how many pixels of the frame's middle (rows 30..80 %, columns 15..85 %) satisfy {@code test} */
    private static int count(Minecraft mc, IntPredicate test) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int n = 0;
            int w = image.getWidth();
            int h = image.getHeight();
            for (int y = (int) (h * 0.30); y < (int) (h * 0.80); y++) {
                for (int x = (int) (w * 0.15); x < (int) (w * 0.85); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int rgb = (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
                    if (test.test(rgb)) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    // --- plumbing --------------------------------------------------------

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    private static void withBike(Minecraft mc, Consumer<Vehicle> action) {
        onServer(mc, sp -> {
            if (sp.serverLevel().getEntity(bike) instanceof Vehicle v) {
                action.accept(v);
            } else {
                LOG.error("booth: FAIL the bike is in the level -- gone");
            }
        });
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> LOG.info("booth: {}", message.getString()));
    }

    /** Runs {@code check}; null is a pass, anything else the failure's detail. */
    private static void verdict(String what, Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("booth: PASS {}", what);
        } else {
            LOG.error("booth: FAIL {} -- {}", what, detail);
        }
    }
}
