package com.coolfreeze23.headinajar.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * One sprite of the kit: a mote, spark, bubble or droplet, puff, ring or static, tinted by meaning (the sheets are
 * light masks). Emissive sprites (mote, spark, ring, static) draw full bright; bubbles, drops and puffs are lit by the
 * world. Make them with {@link #make}, which applies vanilla's particle setting and the 32-block limit.
 */
public class FxParticle extends TextureSheetParticle {
    public enum Look {
        MOTE(4, 4, true), SPARK(3, 3, true), BUBBLE(6, 7, false), DROPLET(1, 7, false), PUFF(8, 8, false), RING(4, 4, true), STATIC(4, 4, true);

        /** frames this sprite animates through; textures in its sheet (the bubble sheet's last one is the droplet) */
        final int frames, textures;
        final boolean emissive;
        public SpriteSet set;

        Look(int frames, int textures, boolean emissive) {
            this.frames = frames;
            this.textures = textures;
            this.emissive = emissive;
        }
    }

    private final Look kind;
    private final SpriteSet set;
    private float baseAlpha = 1, grow = 1, startSize;
    private Vec3 normal;
    private Vec3 home;
    private int follow = -1;
    private Vec3 followOffset = Vec3.ZERO;
    private boolean fade = true;
    private int fixedFrame = -1;

    protected FxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, Look kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.set = kind.set;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0;
        this.friction = 0.92f;
        this.hasPhysics = false;
        this.lifetime = 12;
        this.quadSize = 0.12f;
        this.startSize = quadSize;
        frame();
    }

    /** A sprite of {@code kind} at {@code at}, or null if the particle setting or the distance says no. Add it with {@link #add}. */
    public static FxParticle make(Look kind, Vec3 at, Vec3 velocity) {
        var level = Minecraft.getInstance().level;
        if (level == null || kind.set == null || Comfort.particles(1) == 0 && kind != Look.RING || !Comfort.nearEnough(at)) return null;
        return new FxParticle(level, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z, kind);
    }

    public FxParticle color(int rgb) {
        setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
        return this;
    }

    public FxParticle alpha(float a) {
        baseAlpha = a;
        setAlpha(a);
        return this;
    }

    public FxParticle life(int ticks) {
        lifetime = ticks;
        return this;
    }

    public FxParticle size(float blocks) {
        quadSize = startSize = blocks;
        return this;
    }

    /** Size multiplier reached at the end of its life (a ring opening out, a puff growing). */
    public FxParticle grow(float factor) {
        grow = factor;
        return this;
    }

    public FxParticle gravity(float g) {
        gravity = g;
        return this;
    }

    public FxParticle drag(float f) {
        friction = f;
        return this;
    }

    /** A flat sprite lying across {@code normal} instead of facing the camera (rings). */
    public FxParticle flat(Vec3 normal) {
        this.normal = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        return this;
    }

    /** Steers toward a point and dies on arrival (motes streaming to the head). */
    public FxParticle home(Vec3 to) {
        home = to;
        return this;
    }

    /** Rides along with an entity, offset from its position. */
    public FxParticle follow(Entity e, Vec3 offset) {
        follow = e.getId();
        followOffset = offset;
        return this;
    }

    /** Shows one frame of the sheet for its whole life (a ring that closes rather than opens). */
    public FxParticle frame(int k) {
        fixedFrame = k;
        frame();
        return this;
    }

    public FxParticle steady() {
        fade = false;
        return this;
    }

    public void add() {
        Minecraft.getInstance().particleEngine.add(this);
    }

    private void frame() {
        if (set == null) return;
        int last = kind.textures - 1;
        if (fixedFrame >= 0) {
            setSprite(set.get(Math.min(fixedFrame, last), last));
            return;
        }
        if (kind == Look.DROPLET) setSprite(set.get(last, last));
        else if (kind == Look.STATIC) setSprite(set.get((age / 2) % kind.frames, last));
        else setSprite(set.get(Math.min(age * kind.frames / Math.max(1, lifetime), kind.frames - 1), last));
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) return;
        float t = age / (float) Math.max(1, lifetime);
        quadSize = startSize * Mth.lerp(t, 1, grow);
        if (fade) setAlpha(baseAlpha * (t < 0.6f ? 1 : 1 - (t - 0.6f) / 0.4f));
        if (home != null) {
            Vec3 to = home.subtract(x, y, z);
            double d = to.length();
            if (d < 0.15) {
                remove();
                return;
            }
            Vec3 v = to.scale(Math.min(0.45, 0.25 + t * 0.4) / Math.max(d, 0.001) * Math.min(d, 1.2));
            xd = Mth.lerp(0.5, xd, v.x);
            yd = Mth.lerp(0.5, yd, v.y);
            zd = Mth.lerp(0.5, zd, v.z);
        }
        if (follow >= 0) {
            Entity e = level.getEntity(follow);
            if (e == null) {
                remove();
                return;
            }
            setPos(e.getX() + followOffset.x, e.getY() + followOffset.y, e.getZ() + followOffset.z);
        }
        frame();
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (normal == null && kind != Look.SPARK) {
            super.render(buffer, camera, partialTicks);
            return;
        }
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
        Vector3f u, v;
        float s = getQuadSize(partialTicks);
        if (normal != null) {
            Vector3f n = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z);
            Vector3f any = Math.abs(n.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            u = new Vector3f(n).cross(any).normalize().mul(s);
            v = new Vector3f(n).cross(u).normalize().mul(s);
        } else {
            // a spark: its long axis follows its motion, its width faces the camera
            Vector3f dir = new Vector3f((float) xd, (float) yd, (float) zd);
            if (dir.lengthSquared() < 1e-8f) dir.set(0, 1, 0);
            dir.normalize();
            Vector3f toCam = new Vector3f(-px, -py, -pz).normalize();
            u = new Vector3f(dir).cross(toCam).normalize().mul(s * 0.5f);
            v = new Vector3f(dir).mul(s * 1.6f);
        }
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        quad(buffer, px, py, pz, u, v, u0, u1, v0, v1, light);
        if (normal != null) {
            // both faces, so a ring lying across the view is seen from either side
            quad(buffer, px, py, pz, new Vector3f(u).negate(), v, u0, u1, v0, v1, light);
        }
    }

    private void quad(VertexConsumer b, float x, float y, float z, Vector3f u, Vector3f v, float u0, float u1, float v0, float v1, int light) {
        vertex(b, x - u.x - v.x, y - u.y - v.y, z - u.z - v.z, u1, v1, light);
        vertex(b, x - u.x + v.x, y - u.y + v.y, z - u.z + v.z, u1, v0, light);
        vertex(b, x + u.x + v.x, y + u.y + v.y, z + u.z + v.z, u0, v0, light);
        vertex(b, x + u.x - v.x, y + u.y - v.y, z + u.z - v.z, u0, v1, light);
    }

    private void vertex(VertexConsumer b, float x, float y, float z, float u, float v, int light) {
        b.addVertex(x, y, z).setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return kind.emissive ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** What {@code /particle headinajar:<name>} makes: a plain white sprite. */
    public record Provider(Look kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public FxParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new FxParticle(level, x, y, z, vx, vy, vz, kind);
        }
    }
}
