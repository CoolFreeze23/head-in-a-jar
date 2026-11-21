package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.client.Parachute;
import com.coolfreeze23.headinajar.client.ParachuteBefore;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * {@code chuteproof LABEL}: the A/B proof that the Golden Parachute over a player's jar draws exactly as it did before
 * the parachute was generalised to any entity. The same chutes (every state, a spread of ages, rates and partial ticks,
 * a steer to each side, the fold on land and in water) are drawn over this client's own player through the fixture
 * {@link ParachuteBefore} (the old class, renamed) and through today's {@link Parachute}, each into a recorder that
 * hashes every vertex's position, colour, texture, overlay, light and normal bits and the render types asked for. The
 * report says how many draws, vertices and clips were compared and whether every draw matched. Run it standing on the
 * ground (the flare), flying (descend, steer) and in third person looking down (the faint cloth).
 */
final class ChuteProof {
    private ChuteProof() {
    }

    /** One draw's record: its digest and size. */
    private static final class Tape implements MultiBufferSource {
        final MessageDigest md;
        int vertices;
        final ByteBuffer b = ByteBuffer.allocate(64);
        final VertexConsumer consumer = new VertexConsumer() {
            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                put('v', Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z));
                vertices++;
                return this;
            }

            @Override
            public VertexConsumer setColor(int r, int g, int bl, int a) {
                put('c', r, g, bl, a);
                return this;
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                put('t', Float.floatToRawIntBits(u), Float.floatToRawIntBits(v));
                return this;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                put('o', u, v);
                return this;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                put('l', u, v);
                return this;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                put('n', Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z));
                return this;
            }
        };

        Tape() {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        void put(char kind, int... values) {
            b.clear();
            b.putChar(kind);
            for (int v : values) b.putInt(v);
            md.update(b.array(), 0, b.position());
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            md.update(type.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return consumer;
        }

        String hex() {
            StringBuilder s = new StringBuilder();
            for (byte x : md.digest()) s.append(String.format("%02x", x));
            return s.toString();
        }
    }

    private static int draws, vertices, differ;
    private static String firstDiffer;
    private static final java.util.Set<String> CLIPS = new java.util.TreeSet<>();
    private static MessageDigest all;

    static String run(Minecraft mc, String label) {
        draws = 0;
        vertices = 0;
        differ = 0;
        firstDiffer = null;
        CLIPS.clear();
        try {
            all = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var p = mc.player;
        int id = p.getId();
        int light = 0xF000F0;
        // each class keeps its own posing object over GeckoLib's one set of baked bones, and each takes the bones' rest
        // pose from them when it is made: both are made now, before either has posed anything
        if (!rest(ParachuteBefore.class) || !rest(Parachute.class)) return "chuteproof " + label + " -> the parachute model is not loaded";
        float[] partials = {0, 0.25f, 0.5f, 0.75f};
        // every state at a spread of ages: the deploy (as drawn and faster), the snap, open, the tangle's streamer
        int[] ages = {0, 1, 3, 6, 10, 13, 15, 19, 20, 24, 29, 30, 34, 40, 47, 60, 90};
        float[] rates = {1, 1.7f};
        for (int state : new int[]{HopperNet.FIRED, HopperNet.OPEN, HopperNet.TANGLED}) {
            for (float rate : rates) {
                if (state == HopperNet.TANGLED && rate != 1) continue;
                for (int age : ages) {
                    reset(id);
                    set(id, state, age, rate);
                    for (float pt : partials) draw(mc, pt, light, "s" + state + " r" + rate + " a" + age + " pt" + pt);
                }
            }
        }
        // a run of frames on one chute: the flare's timer and the swing carry from frame to frame
        reset(id);
        set(id, HopperNet.OPEN, 30, 1);
        for (int i = 0; i < 24; i++) draw(mc, (i % 4) / 4f, light, "run " + i);
        // steering, each side: the jar sliding across its facing faster than 0.03 a tick
        for (int side : new int[]{-1, 1}) {
            reset(id);
            set(id, HopperNet.OPEN, 40, 1);
            double xo = p.xo, zo = p.zo;
            var dm = p.getDeltaMovement();
            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            // across = v.x * -cos(yaw) + v.z * -sin(yaw)
            double ax = -Mth.cos(yaw) * 0.08 * side, az = -Mth.sin(yaw) * 0.08 * side;
            p.xo = p.getX() - ax;
            p.zo = p.getZ() - az;
            p.setDeltaMovement(ax, -0.08, az);
            for (int i = 0; i < 16; i++) draw(mc, (i % 4) / 4f, light, "steer " + side + " " + i);
            p.xo = xo;
            p.zo = zo;
            p.setDeltaMovement(dm);
        }
        // the fold on land and in water, drawn where it came down
        for (int fold : new int[]{HopperNet.FOLDED, HopperNet.SUNK}) {
            reset(id);
            set(id, HopperNet.OPEN, 40, 1);
            draw(mc, 0, light, "before fold " + fold);
            set(id, fold, 41, 1);
            for (float pt : partials) draw(mc, pt, light, "fold " + fold + " pt" + pt);
        }
        reset(id);
        StringBuilder d = new StringBuilder();
        for (byte x : all.digest()) d.append(String.format("%02x", x));
        var cam = mc.gameRenderer.getMainCamera();
        return String.format(Locale.ROOT, "chuteproof %s -> %d draws, %d vertices, clips %s, %s; camera %s pitch %.0f, on ground %s; all-draws digest %s",
                label, draws, vertices, String.join(",", CLIPS), differ == 0 ? "EVERY DRAW IDENTICAL" : differ + " DRAWS DIFFER, first: " + firstDiffer,
                mc.options.getCameraType(), cam.getXRot(), p.onGround(), d.substring(0, 16));
    }

    /** Makes the class's model object (its {@code model()}, or {@code model(GOLDEN)}) now; false if it is not loaded. */
    private static boolean rest(Class<?> c) {
        try {
            for (var m : c.getDeclaredMethods()) {
                if (!m.getName().equals("model") || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                m.setAccessible(true);
                Object made = m.getParameterCount() == 0 ? m.invoke(null) : m.invoke(null, c.getField("GOLDEN").get(null));
                return made != null;
            }
        } catch (ReflectiveOperationException e) {
            return false;
        }
        return false;
    }

    private static void reset(int id) {
        Parachute.set(id, HopperNet.FOLDED, 0, 1);
        ParachuteBefore.set(id, HopperNet.FOLDED, 0, 1);
        set(id, HopperNet.FIRED, 0, 1);
        set(id, HopperNet.FOLDED, 0, 1);
    }

    private static void set(int id, int state, int age, float rate) {
        ParachuteBefore.set(id, state, age, rate);
        Parachute.set(id, state, age, rate);
    }

    private static void draw(Minecraft mc, float pt, int light, String what) {
        var p = mc.player;
        if (!ParachuteBefore.shown(p.getId()) && !Parachute.shown(p.getId())) return;
        Tape before = new Tape(), after = new Tape();
        ParachuteBefore.render(p, pt, new PoseStack(), before, light);
        String clipBefore = ParachuteBefore.describe();
        Parachute.render(p, pt, new PoseStack(), after, light);
        String clipAfter = Parachute.describe();
        String hb = before.hex(), ha = after.hex();
        draws++;
        vertices += before.vertices;
        all.update(hb.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String clip = clipBefore.contains(" ") ? clipBefore.split(" ")[1].replace(";", "") : clipBefore;
        CLIPS.add(clip);
        if (!hb.equals(ha) || before.vertices != after.vertices || !clipBefore.equals(clipAfter)) {
            if (differ++ == 0) firstDiffer = what + " (before " + clipBefore + " " + before.vertices + " v, after " + clipAfter + " " + after.vertices + " v)";
        }
    }
}
