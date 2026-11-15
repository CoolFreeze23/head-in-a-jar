package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.HeadInfo;
import com.coolfreeze23.headinajar.block.JarredHeadBlock;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.staff.Band;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.joml.Quaternionf;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A jarred mob head: the jar, with the mob's own head (from its own model and texture) floating inside. A creature that
 * is all head (a slime, a ghast, a bee ...) or whose model has no head to take is drawn whole, shrunk to sit in the jar.
 */
public class JarredHeadRenderer implements BlockEntityRenderer<JarredHeadBlockEntity> {
    private static boolean listening;

    public JarredHeadRenderer(BlockEntityRendererProvider.Context context) {
        if (!listening) {
            listening = true;
            // leaving a world (or the server): the stand-ins hold the old client world, so they go
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> forget());
        }
    }

    /** Beyond this the head stops turning (it is drawn looking ahead). */
    private static final double TURN_DISTANCE = 32;
    /** Beyond this a creature drawn whole is not drawn (its head part, if it has one, is). */
    private static final double WHOLE_DISTANCE = 48;

    /**
     * The real jar at 1:1 on its plinth (the block's model), its base at (0, 7, 0) px, facing the way the head was set
     * down; the head inside turns to the nearest player, nods when a trade goes through and shakes when it refuses. Its
     * morale shows: an Engaged head holds itself up and bobs a little more; a Disengaged one (or one with a Grievance)
     * hangs its head and looks away from whoever is near.
     */
    @Override
    public void render(JarredHeadBlockEntity head, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        EntityType<?> type = head.entityType();
        if (type == null || head.getLevel() == null) return;
        float age = head.getLevel().getGameTime() + partialTick;
        float yaw = Mth.lerp(partialTick, head.yawPrev, head.yaw), pitch = Mth.lerp(partialTick, head.pitchPrev, head.pitch);
        double now = head.getLevel().getGameTime() + partialTick;
        double nod = head.since(JarredHeadBlockEntity.EV_NOD, now), no = head.since(JarredHeadBlockEntity.EV_REFUSE, now);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (head.getBlockPos().getCenter().distanceToSqr(camera) > TURN_DISTANCE * TURN_DISTANCE) yaw = pitch = 0;
        // morale: the band the server sent
        Band band = head.band();
        float bob = 1;
        if (band == Band.ENGAGED) {
            pitch -= 4;
            bob = 1.4f;
        } else if (band.low()) {
            pitch += 12;
            yaw += away(head, yaw, partialTick);
        }
        if (nod >= 0 && nod < 10) pitch += (float) (20 * Math.sin(Math.PI * nod / 10));
        if (no >= 0 && no < 12) yaw += (float) (25 * Math.sin(2 * Math.PI * no / 6) * (1 - no / 12));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-head.getBlockState().getValue(JarredHeadBlock.FACING).toYRot()));
        if (head.badge() >= 0) StaffBadge.draw(pose, buffers, light, head.badge(), StaffBadge.PLINTH);
        pose.translate(0, 7 / 16.0, 0);
        CompoundTag record = head.entityTag();
        drawJar(type, record.contains("VillagerData") ? record.getCompound("VillagerData") : null, size(record), head.jar(),
                pose, buffers, light, age, yaw, pitch, bob);
        pose.popPose();
    }

    /** A slime's or magma cube's size from its record ({@code Size} is saved one less than the size), 0 when not there. */
    public static int size(CompoundTag entity) {
        return entity != null && entity.contains("Size") ? entity.getInt("Size") + 1 : 0;
    }

    /** Per head, how far it is turned away from the nearest player now (degrees) and which way it last chose. */
    private static final Map<JarredHeadBlockEntity, float[]> AWAY = new WeakHashMap<>();

    /**
     * A low head turns 20 degrees away from the player it would look at: to the side away from them, chosen again only
     * once they are well to one side (so a player in front does not flip it from side to side), and eased over.
     */
    private static float away(JarredHeadBlockEntity head, float yaw, float partialTick) {
        float[] s = AWAY.computeIfAbsent(head, h -> new float[]{0, (h.getBlockPos().hashCode() & 1) == 0 ? 1 : -1, -1});
        if (yaw > 8) s[1] = -1;
        else if (yaw < -8) s[1] = 1;
        float want = 20 * s[1];
        float t = head.getLevel().getGameTime() + partialTick;
        float dt = s[2] < 0 ? 1000 : Math.max(0, t - s[2]);
        s[2] = t;
        // eased: a fifth of the way each tick, as the head's own turn is
        s[0] += (want - s[0]) * (1 - (float) Math.pow(0.8, dt));
        return s[0];
    }

    /** The jar's lid and valve rise 7 px over the cell. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(JarredHeadBlockEntity head) {
        return new net.minecraft.world.phys.AABB(head.getBlockPos()).expandTowards(0, 0.5, 0);
    }

    /** A whole jarred head (jar and the mob's head inside), at the pose's origin; {@code entityTag}: the villager data, or null. */
    public static void drawJar(EntityType<?> type, CompoundTag entityTag, PoseStack pose, MultiBufferSource buffers, int light, float age) {
        draw(type, entityTag, 0, null, pose, buffers, light, age, 0, 0, 1, false);
    }

    /** As above, the head inside turned {@code yaw} degrees (clockwise from above, as a player turns) and tipped {@code pitch} (down). */
    public static void drawJar(EntityType<?> type, CompoundTag entityTag, PoseStack pose, MultiBufferSource buffers, int light, float age,
                               float yaw, float pitch) {
        draw(type, entityTag, 0, null, pose, buffers, light, age, yaw, pitch, 1, false);
    }

    /**
     * A whole jarred head in the jar it went into ({@code jar}: the jar's item id, null for a Life Support Jar), at the
     * pose's origin. {@code villagerData}: a villager's data, or null; {@code size}: a slime's or magma cube's size (1, 2,
     * 4 ...), 0 when not known. Always drawn in full (the file card's photo and other screens use this one).
     */
    public static void drawJar(EntityType<?> type, CompoundTag villagerData, int size, String jar, PoseStack pose, MultiBufferSource buffers, int light, float age) {
        draw(type, villagerData, size, jar, pose, buffers, light, age, 0, 0, 1, false);
    }

    /**
     * As above, in the world: the head turned {@code yaw} and tipped {@code pitch} degrees, its bob in the fluid {@code bob}
     * times the usual. A creature drawn whole is left out beyond {@link #WHOLE_DISTANCE} of the camera (the pose's own
     * offset, as the level's renderers set it up), its head part drawn instead if it has one.
     */
    public static void drawJar(EntityType<?> type, CompoundTag villagerData, int size, String jar, PoseStack pose, MultiBufferSource buffers, int light, float age,
                               float yaw, float pitch, float bob) {
        draw(type, villagerData, size, jar, pose, buffers, light, age, yaw, pitch, bob, true);
    }

    private static final Quaternionf TURN = new Quaternionf();

    private static void draw(EntityType<?> type, CompoundTag villagerData, int size, String jar, PoseStack pose, MultiBufferSource buffers, int light, float age,
                             float yaw, float pitch, float bobScale, boolean world) {
        boolean compact = HeadInfo.COMPACT.equals(jar);
        JarRenderer.Shape shape = compact ? JarRenderer.COMPACT : JarRenderer.WIDE;
        Look look = type == null ? null : look(type, size, villagerData);
        boolean far = false;
        if (world && look != null && look.whole) {
            var m = pose.last().pose();
            far = m.m30() * m.m30() + m.m31() * m.m31() + m.m32() * m.m32() > WHOLE_DISTANCE * WHOLE_DISTANCE;
        }
        pose.pushPose();
        float turn = Mth.wrapDegrees(yaw);
        if (compact) {
            // the Compact Jar holds the head close: past its little turn, the whole jar turns, as a player's does
            float inside = Mth.clamp(turn, -shape.turn(), shape.turn());
            pose.mulPose(Axis.YP.rotationDegrees(-(turn - inside)));
            turn = inside;
        }
        float bob = JarRenderer.openWith(pose, buffers, light, age, shape) * bobScale;
        if (look != null) {
            if (look.whole && !far) drawWhole(look, pose, buffers, light, age, bob, turn, pitch, compact);
            else if (look.head != null && !(look.whole && look.headIsRoot)) drawHead(look, villagerData, pose, buffers, light, bob, turn, pitch, compact);
        }
        JarRenderer.closeWith(pose, buffers, light, shape, SALINE);
        pose.popPose();
    }

    // --- the room inside the jars (jar model space: px, y down, the base's underside at 24) --------------------------

    /** Where a head's neck sits, and where a whole creature stands, in each jar (wide, compact). */
    private static final float[] NECK = {22.0f, 22.3f}, FLOOR = {22.0f, 22.3f};
    /**
     * The saline's top in a jar that holds a creature's head (px, y down): fuller than a player's own jar, so the head
     * floats inside it rather than poking out of its surface.
     */
    static final float SALINE = 14.5f;
    /** The highest a head or creature may reach: under the saline's surface, with room for the bob. */
    private static final float TOP = SALINE + 0.5f;
    /** How far from the jar's axis anything may reach (wide: turning right round; compact: facing the front). */
    private static final float[] ROOM = {5.4f, 4.0f};
    /** The most a head is enlarged: a small head (a cat's, an allay's) fills its jar as far as this, so it reads. */
    private static final float MAX_SCALE = 1.4f;

    private static float scale(float reach, float height, float floor, boolean compact) {
        int j = compact ? 1 : 0;
        float s = MAX_SCALE;
        if (reach > 0) s = Math.min(s, ROOM[j] / reach);
        if (height > 0) s = Math.min(s, (floor - TOP) / height);
        return s;
    }

    /** How far to raise something {@code height} px tall drawn at {@code s} so it floats in the middle of the saline (px). */
    private static float lift(float height, float s, float floor) {
        return Math.max(0, (floor - TOP - height * s) / 2);
    }

    /** The head part (and what goes with it), its box's bottom centre at the jar's neck, turned and tipped about it. */
    private static void drawHead(Look look, CompoundTag villagerData, PoseStack pose, MultiBufferSource buffers, int light, float bob,
                                 float yaw, float pitch, boolean compact) {
        float neck = NECK[compact ? 1 : 0];
        float s = compact ? look.headScaleCompact : look.headScale;
        float lift = compact ? look.headLiftCompact : look.headLift;
        pose.pushPose();
        pose.translate(0, (neck - lift + bob) / 16f, 0);
        pose.mulPose(TURN.rotationZYX(0, (yaw + look.side) * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD));
        pose.scale(s, s, s);
        pose.translate(-look.hcx / 16f, -look.hbottom / 16f, -look.hcz / 16f);
        // the head as the creature holds it at rest (a hoglin's snout down, a goat's head bowed)
        if (look.rest != null) pose.mulPose(look.rest);
        int overlay = OverlayTexture.NO_OVERLAY;
        // a villager's hat brim is wider than the glass: it stays outside the jar
        boolean rim = look.rim != null && look.rim.visible;
        if (look.rim != null) look.rim.visible = false;
        VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(look.texture));
        part(look.head, pose, base, light, overlay, 0, 0, 0, 0, 0, 0, -1);
        for (int i = 0; i < look.extras.length; i++) {
            float[] o = look.extraPose[i];
            part(look.extras[i], pose, base, light, overlay, o[0], o[1], o[2], o[3], o[4], o[5], -1);
        }
        for (int i = 0; i < look.overlays.length; i++) {
            float[] o = look.overlayPose[i];
            part(look.overlays[i], pose, buffers.getBuffer(RenderType.entityCutoutNoCull(look.overlayTexture[i])), light, overlay,
                    o[0], o[1], o[2], o[3], o[4], o[5], -1);
        }
        // a villager's (or zombie villager's) biome clothes and trade hat, as its own layer dresses its head
        if (look.dressed && villagerData != null) {
            for (ResourceLocation layer : dress(look.dressPrefix, villagerData)) {
                if (layer != null) part(look.head, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(layer)), light, overlay, 0, 0, 0, 0, 0, 0, -1);
            }
        }
        if (look.pumpkin) pumpkin(pose, buffers, light);
        if (look.rim != null) look.rim.visible = rim;
        pose.popPose();
    }

    /** The snow golem's carved pumpkin, where its own layer puts it on the head (in the head part's space). */
    private static void pumpkin(PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, -0.34375f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(0.625f, -0.625f, -0.625f);
        pose.translate(-0.5f, -0.5f, -0.5f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(net.minecraft.world.level.block.Blocks.CARVED_PUMPKIN.defaultBlockState(), pose, buffers,
                light, OverlayTexture.NO_OVERLAY, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    /** The whole creature through its own renderer (no shadow, no name), standing on the jar's floor, turned about its middle. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void drawWhole(Look look, PoseStack pose, MultiBufferSource buffers, int light, float age, float bob, float yaw, float pitch, boolean compact) {
        float floor = FLOOR[compact ? 1 : 0];
        float s = compact ? look.wholeScaleCompact : look.wholeScale;
        float lift = compact ? look.wholeLiftCompact : look.wholeLift;
        Entity e = look.entity;
        int tick = Mth.floor(age);
        e.tickCount = tick;
        pose.pushPose();
        pose.translate(0, (floor - lift + bob) / 16f, 0);
        // back to the world's way up (the renderer turns it over again itself)
        pose.scale(-1, -1, 1);
        pose.mulPose(TURN.rotationZYX(0, -(yaw + look.side) * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD));
        pose.scale(s, s, s);
        pose.translate(-look.wcx / 16f, -look.wbottom / 16f, -look.wcz / 16f);
        ((EntityRenderer) look.renderer).render(e, FACING_YAW, age - tick, pose, buffers, light);
        pose.popPose();
    }

    /** A creature turned this way looks out of the jar's front (-Z), as a head part does. */
    private static final float FACING_YAW = 180;
    /** Long swimmers show their side, as fish in a tank do (a fish facing you is a sliver). */
    private static final Set<EntityType<?>> SIDE_ON = Set.of(EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH, EntityType.TADPOLE,
            EntityType.SILVERFISH, EntityType.ENDERMITE);
    /** Heads whose face is on their sides, down a long snout: turned three-quarters, so the eyes show. */
    private static final Set<EntityType<?>> THREE_QUARTER = Set.of(EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.FROG);

    /** Draws {@code part} at the given place and turn (px, radians), its own pose put back afterwards. */
    private static void part(ModelPart part, PoseStack pose, VertexConsumer buffer, int light, int overlay,
                             float x, float y, float z, float xRot, float yRot, float zRot, int colour) {
        float px = part.x, py = part.y, pz = part.z, rx = part.xRot, ry = part.yRot, rz = part.zRot;
        float sx = part.xScale, sy = part.yScale, sz = part.zScale;
        boolean visible = part.visible;
        part.x = x;
        part.y = y;
        part.z = z;
        part.xRot = xRot;
        part.yRot = yRot;
        part.zRot = zRot;
        part.xScale = part.yScale = part.zScale = 1;
        part.visible = true;
        part.render(pose, buffer, light, overlay, colour);
        part.x = px;
        part.y = py;
        part.z = pz;
        part.xRot = rx;
        part.yRot = ry;
        part.zRot = rz;
        part.xScale = sx;
        part.yScale = sy;
        part.zScale = sz;
        part.visible = visible;
    }

    // --- what each creature looks like, worked out once ----------------------------------------------------------

    /** Creatures that are all head: drawn whole in the jar. */
    private static final Set<EntityType<?>> ALL_HEAD = Set.of(EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.SHULKER, EntityType.VEX, EntityType.BEE,
            EntityType.SILVERFISH, EntityType.ENDERMITE, EntityType.TADPOLE, EntityType.GHAST,
            // a fish's head alone is a stub of a block: the whole fish reads
            EntityType.COD, EntityType.SALMON);

    /**
     * Parts beside the head that belong to the face (siblings in the model, not the head's own children): a chicken's beak
     * and wattle, a rabbit's ears and nose. Field names, as the model classes have them.
     */
    private static final Map<EntityType<?>, String[]> EXTRAS = Map.of(
            EntityType.CHICKEN, new String[]{"beak", "redThing"},
            EntityType.RABBIT, new String[]{"rightEar", "leftEar", "nose"});

    /**
     * Creatures with no head part whose drawing whole would not fit the jar's saline: the parts that stand for the head
     * (the first is the head, the rest go with it). A ghast's body is its face, a squid's mantle its head; a strider is
     * all body on legs; the Wither is three heads.
     */
    private static final Map<EntityType<?>, String[]> PARTS = Map.of(
            EntityType.GHAST, new String[]{"body"},
            EntityType.SQUID, new String[]{"body"},
            EntityType.GLOW_SQUID, new String[]{"body"},
            EntityType.STRIDER, new String[]{"body"},
            EntityType.WITHER, new String[]{"center_head", "right_head", "left_head"});

    /** Layers that dress the head with a model of their own and a texture of their own (wool, a drowned's weed, a stray's rags). */
    private static final Set<String> HEAD_LAYERS = Set.of("SheepFurLayer", "DrownedOuterLayer", "SkeletonClothingLayer");

    /** One creature's stand-in and how its head (or whole body) sits in each jar. */
    static final class Look {
        final EntityType<?> type;
        final Entity entity;
        final EntityRenderer<?> renderer;
        final boolean whole;
        ModelPart head;
        /** The head part is the model's root (a creature with no head of its own): for faces only, never drawn as a head. */
        boolean headIsRoot;
        ResourceLocation texture;
        ModelPart[] extras = new ModelPart[0];
        float[][] extraPose = new float[0][];
        ModelPart[] overlays = new ModelPart[0];
        float[][] overlayPose = new float[0][];
        ResourceLocation[] overlayTexture = new ResourceLocation[0];
        boolean dressed, pumpkin;
        /** Degrees a whole creature is turned from facing the front (a fish side-on). */
        float side;
        String dressPrefix;
        /** A villager's hat brim (a child of its head), left off in the jar. */
        ModelPart rim;
        /** The head's box (px, part space, y down): its bottom centre, and the scale it is drawn at in each jar. */
        float hcx, hbottom, hcz, headScale = MAX_SCALE, headScaleCompact = MAX_SCALE, headLift, headLiftCompact;
        /** The head part's own turn as the model rests (null when none). */
        org.joml.Quaternionf rest;
        /** The whole creature's box (px, the renderer's space, y up): its bottom centre, and its scale in each jar. */
        float wcx, wbottom, wcz, wholeScale = MAX_SCALE, wholeScaleCompact = MAX_SCALE, wholeLift, wholeLiftCompact;
        /** The head and texture for the renderers that take a face from it. */
        Head asHead;
        /** What was measured, for the notes and the species board. */
        float headReach, headHeight, wholeReach, wholeHeight;

        Look(EntityType<?> type, Entity entity, EntityRenderer<?> renderer, boolean whole) {
            this.type = type;
            this.entity = entity;
            this.renderer = renderer;
            this.whole = whole;
        }
    }

    /** One look per creature (and per slime size), made in the current client world; let go with it. */
    private static final Map<EntityType<?>, Look[]> LOOKS = new IdentityHashMap<>();
    private static final int SIZES = 9;
    private static net.minecraft.client.multiplayer.ClientLevel looksLevel;

    /** Forgets every stand-in (leaving a world). */
    public static void forget() {
        LOOKS.clear();
        looksLevel = null;
    }

    /** The look of {@code type} (a slime at {@code size}; a villager dressed by its data), or null when it cannot be drawn. */
    static Look look(EntityType<?> type, int size, CompoundTag villagerData) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (looksLevel != mc.level) {
            // another world since they were made (a dimension change makes a new client world)
            LOOKS.clear();
            looksLevel = mc.level;
        }
        int slot = type == EntityType.SLIME || type == EntityType.MAGMA_CUBE ? Mth.clamp(size, 0, SIZES - 1) : 0;
        Look[] bySize = LOOKS.computeIfAbsent(type, t -> new Look[SIZES]);
        Look look = bySize[slot];
        // the renderers are made again on a resource reload: the old parts are no longer the ones drawn
        if (look != null && look.renderer != null && mc.getEntityRenderDispatcher().getRenderer(look.entity) != look.renderer) look = null;
        if (look == null) {
            look = make(type, slot);
            bySize[slot] = look;
        }
        if (look.renderer == null) return null;
        if (look.entity instanceof net.minecraft.world.entity.npc.VillagerDataHolder holder && villagerData != null && !villagerData.isEmpty()) {
            var data = villagerData(villagerData);
            if (data != null && data != holder.getVillagerData()) holder.setVillagerData(data);
        }
        return look;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Look make(EntityType<?> type, int size) {
        var mc = Minecraft.getInstance();
        Entity entity = type.create(mc.level);
        if (entity == null) return new Look(type, null, null, false);
        if (entity instanceof net.minecraft.world.entity.monster.Slime slime && size > 0) slime.setSize(size, false);
        standIn(entity);
        EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        EntityModel<?> model = renderer instanceof LivingEntityRenderer<?, ?> living ? living.getModel() : null;
        ModelPart head = model == null ? null : HostHeads.find(model);
        ModelPart horseMouth = null;
        if (model instanceof net.minecraft.client.model.HorseModel<?>) {
            // a horse's head comes on its neck and mane: the skull and muzzle alone
            ModelPart parts = field(model, "headParts");
            if (parts != null && parts.hasChild("head")) {
                head = parts.getChild("head");
                if (parts.hasChild("upper_mouth")) horseMouth = parts.getChild("upper_mouth");
            }
        }
        String[] parts = PARTS.get(type);
        if (parts != null && model instanceof HierarchicalModel<?> hierarchical && hierarchical.root().hasChild(parts[0])) head = hierarchical.root().getChild(parts[0]);
        else parts = null;
        boolean whole = parts == null && (ALL_HEAD.contains(type) || head == null);
        Look look = new Look(type, entity, renderer, whole);
        if (SIDE_ON.contains(type)) look.side = 90;
        else if (THREE_QUARTER.contains(type)) look.side = 35;
        if (head == null && model instanceof HierarchicalModel<?> hierarchical) {
            // no head: its whole body stands for one where only a face is wanted (the Cerebral Array's specimen jars)
            head = hierarchical.root();
            look.headIsRoot = true;
        }
        look.head = head;
        if (renderer instanceof LivingEntityRenderer living && entity instanceof LivingEntity le) look.texture = living.getTextureLocation(le);
        if (head != null && model != null && !look.headIsRoot) {
            List<ModelPart> extras = new ArrayList<>();
            List<float[]> poses = new ArrayList<>();
            var headPose = head.getInitialPose();
            if (model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid) {
                extras.add(humanoid.hat);
                poses.add(relative(humanoid.hat, headPose));
            }
            if (horseMouth != null) {
                extras.add(horseMouth);
                poses.add(relative(horseMouth, headPose));
            }
            if (parts != null) {
                var root = ((HierarchicalModel<?>) model).root();
                for (int i = 1; i < parts.length; i++) {
                    if (!root.hasChild(parts[i])) continue;
                    extras.add(root.getChild(parts[i]));
                    poses.add(relative(root.getChild(parts[i]), headPose));
                }
            }
            for (String name : EXTRAS.getOrDefault(type, new String[0])) {
                ModelPart p = field(model, name);
                if (p != null) {
                    extras.add(p);
                    poses.add(relative(p, headPose));
                }
            }
            look.extras = extras.toArray(new ModelPart[0]);
            look.extraPose = poses.toArray(new float[0][]);
            if (renderer instanceof LivingEntityRenderer<?, ?> living) layers(look, living, headPose);
            look.dressed = type == EntityType.VILLAGER || type == EntityType.ZOMBIE_VILLAGER;
            look.dressPrefix = type == EntityType.VILLAGER ? "villager" : "zombie_villager";
            look.pumpkin = type == EntityType.SNOW_GOLEM;
            if (type == EntityType.VILLAGER || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.WANDERING_TRADER) look.rim = field(model, "hatRim");
        }
        if (look.head != null && look.texture != null) look.asHead = new Head(look.head, look.texture);
        measure(look);
        return look;
    }

    /** Fish out of water lie on their side and a shulker keeps its lid shut: in a jar of saline they swim, and it peeks. */
    private static void standIn(Entity entity) {
        try {
            if (IN_WATER == null) {
                IN_WATER = Entity.class.getDeclaredField("wasTouchingWater");
                IN_WATER.setAccessible(true);
            }
            IN_WATER.setBoolean(entity, true);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        if (entity instanceof net.minecraft.world.entity.monster.Shulker) {
            try {
                for (String name : new String[]{"currentPeekAmount", "currentPeekAmountO"}) {
                    Field f = net.minecraft.world.entity.monster.Shulker.class.getDeclaredField(name);
                    f.setAccessible(true);
                    f.setFloat(entity, 0.4f);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        // a pufferfish in a jar is puffed up: all head, as the saw found it
        if (entity instanceof net.minecraft.world.entity.animal.Pufferfish puffer) puffer.setPuffState(2);
        entity.setYRot(FACING_YAW);
        entity.setXRot(0);
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = FACING_YAW;
            living.yHeadRot = living.yHeadRotO = FACING_YAW;
        }
        entity.yRotO = FACING_YAW;
        entity.xRotO = 0;
    }

    private static Field IN_WATER;

    /** {@code part}'s place and turn relative to the head's (both as the model rests). */
    private static float[] relative(ModelPart part, net.minecraft.client.model.geom.PartPose head) {
        var p = part.getInitialPose();
        return new float[]{p.x - head.x, p.y - head.y, p.z - head.z, p.xRot, p.yRot, p.zRot};
    }

    /** A model part held in a field of the model (or its superclasses), or null. */
    private static ModelPart field(Object model, String name) {
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                if (f.getType() != ModelPart.class) return null;
                f.setAccessible(true);
                return (ModelPart) f.get(model);
            } catch (NoSuchFieldException e) {
                // look further up
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    /** The head layers ({@link #HEAD_LAYERS}): each one's own model's head, drawn over the head with that layer's texture. */
    private static void layers(Look look, LivingEntityRenderer<?, ?> renderer, net.minecraft.client.model.geom.PartPose headPose) {
        List<RenderLayer<?, ?>> found;
        try {
            Field f = LivingEntityRenderer.class.getDeclaredField("layers");
            f.setAccessible(true);
            found = new ArrayList<>((List<RenderLayer<?, ?>>) f.get(renderer));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return;
        }
        List<ModelPart> parts = new ArrayList<>();
        List<float[]> poses = new ArrayList<>();
        List<ResourceLocation> textures = new ArrayList<>();
        for (RenderLayer<?, ?> layer : found) {
            if (!HEAD_LAYERS.contains(layer.getClass().getSimpleName())) continue;
            EntityModel<?> model = null;
            ResourceLocation texture = null;
            for (Class<?> c = layer.getClass(); c != null && c != RenderLayer.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object value = f.get(java.lang.reflect.Modifier.isStatic(f.getModifiers()) ? null : layer);
                        if (model == null && value instanceof EntityModel<?> m) model = m;
                        if (texture == null && value instanceof ResourceLocation r) texture = r;
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                }
            }
            ModelPart head = model == null ? null : HostHeads.find(model);
            if (head == null || texture == null) continue;
            parts.add(head);
            poses.add(relative(head, headPose));
            textures.add(texture);
        }
        look.overlays = parts.toArray(new ModelPart[0]);
        look.overlayPose = poses.toArray(new float[0][]);
        look.overlayTexture = textures.toArray(new ResourceLocation[0]);
    }

    /** The boxes once: the head with all that goes with it, and the whole creature as its renderer draws it. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void measure(Look look) {
        Bounds b = new Bounds();
        boolean rim = look.rim != null && look.rim.visible;
        if (look.rim != null) look.rim.visible = false;
        if (look.head != null && !look.headIsRoot) {
            PoseStack pose = new PoseStack();
            var at = look.head.getInitialPose();
            if (at.xRot != 0 || at.yRot != 0 || at.zRot != 0) {
                look.rest = new org.joml.Quaternionf().rotationZYX(at.zRot, at.yRot, at.xRot);
                pose.mulPose(look.rest);
            }
            part(look.head, pose, b, 0, 0, 0, 0, 0, 0, 0, 0, -1);
            for (int i = 0; i < look.extras.length; i++) {
                float[] o = look.extraPose[i];
                part(look.extras[i], pose, b, 0, 0, o[0], o[1], o[2], o[3], o[4], o[5], -1);
            }
            for (int i = 0; i < look.overlays.length; i++) {
                float[] o = look.overlayPose[i];
                part(look.overlays[i], pose, b, 0, 0, o[0], o[1], o[2], o[3], o[4], o[5], -1);
            }
            if (look.pumpkin) {
                // the snow golem's carved pumpkin (drawn over the head, not part of it): a 10 px block about the head
                b.minX = Math.min(b.minX, -5 / 16f);
                b.maxX = Math.max(b.maxX, 5 / 16f);
                b.minZ = Math.min(b.minZ, -5 / 16f);
                b.maxZ = Math.max(b.maxZ, 5 / 16f);
                b.minY = Math.min(b.minY, -10.5f / 16f);
                b.maxY = Math.max(b.maxY, -0.5f / 16f);
            }
            if (b.any()) {
                // a turning head needs its corners' reach; one facing the front of the Compact Jar only its sides
                look.hcx = b.cx();
                look.hcz = b.cz();
                look.hbottom = b.maxY * 16;
                look.headReach = b.reach();
                look.headHeight = (b.maxY - b.minY) * 16;
                look.headScale = scale(look.headReach, look.headHeight, NECK[0], false);
                look.headScaleCompact = scale(b.half() * 1.05f, look.headHeight, NECK[1], true);
                look.headLift = lift(look.headHeight, look.headScale, NECK[0]);
                look.headLiftCompact = lift(look.headHeight, look.headScaleCompact, NECK[1]);
            }
        }
        if (look.rim != null) look.rim.visible = rim;
        if (look.whole && look.renderer != null) {
            b.clear();
            try {
                ((EntityRenderer) look.renderer).render(look.entity, FACING_YAW, 0, new PoseStack(), b, 0xF000F0);
            } catch (RuntimeException e) {
                // a renderer that cannot draw a stand-in: drawn as nothing
            }
            if (look.type == EntityType.PUFFERFISH && b.any()) {
                // its renderer bobs it up and down 0.08 of a block: room for that, so it never dips through the saline
                b.minY -= 0.08f;
                b.maxY += 0.08f;
            }
            if (b.any()) {
                look.wcx = b.cx();
                look.wcz = b.cz();
                look.wbottom = b.minY * 16;
                look.wholeReach = b.reach();
                look.wholeHeight = (b.maxY - b.minY) * 16;
                look.wholeScale = scale(look.wholeReach, look.wholeHeight, FLOOR[0], false);
                look.wholeScaleCompact = scale(b.half() * 1.05f, look.wholeHeight, FLOOR[1], true);
                look.wholeLift = lift(look.wholeHeight, look.wholeScale, FLOOR[0]);
                look.wholeLiftCompact = lift(look.wholeHeight, look.wholeScaleCompact, FLOOR[1]);
            }
        }
    }

    /** What a creature's look measured, for the species board: "head" or "whole", reach and height (px), and the scale. */
    public static String describe(EntityType<?> type, int size) {
        Look look = look(type, size, null);
        if (look == null) return "none";
        if (look.whole) return String.format(java.util.Locale.ROOT, "whole reach %.1f height %.1f scale %.2f/%.2f%s", look.wholeReach, look.wholeHeight,
                look.wholeScale, look.wholeScaleCompact, look.headIsRoot ? "" : " (head part beyond 48)");
        return String.format(java.util.Locale.ROOT, "head reach %.1f height %.1f scale %.2f/%.2f extras %d layers %d", look.headReach, look.headHeight,
                look.headScale, look.headScaleCompact, look.extras.length, look.overlays.length);
    }

    /** The smallest box round every vertex drawn into it (it takes every buffer and draws nothing). */
    static final class Bounds implements VertexConsumer, MultiBufferSource {
        float minX, minY, minZ, maxX, maxY, maxZ;

        Bounds() {
            clear();
        }

        void clear() {
            minX = minY = minZ = Float.POSITIVE_INFINITY;
            maxX = maxY = maxZ = Float.NEGATIVE_INFINITY;
        }

        boolean any() {
            return minX <= maxX;
        }

        float cx() {
            return (minX + maxX) / 2 * 16;
        }

        float cz() {
            return (minZ + maxZ) / 2 * 16;
        }

        /** The farthest a corner reaches from the box's vertical axis (px). */
        float reach() {
            float hx = (maxX - minX) / 2 * 16, hz = (maxZ - minZ) / 2 * 16;
            return (float) Math.sqrt(hx * hx + hz * hz);
        }

        /** The larger half-width (px). */
        float half() {
            return Math.max(maxX - minX, maxZ - minZ) / 2 * 16;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return this;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    // --- villagers --------------------------------------------------------------------------------------------------

    /** A villager's look by its data tag, parsed once (it is drawn every frame). */
    private static final Map<CompoundTag, VillagerData> VILLAGER_DATA = new HashMap<>();

    private static VillagerData villagerData(CompoundTag tag) {
        var known = VILLAGER_DATA.get(tag);
        if (known != null) return known;
        var parsed = VillagerData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElse(null);
        if (parsed != null) {
            if (VILLAGER_DATA.size() > 256) {
                VILLAGER_DATA.clear();
                DRESS.clear();
            }
            VILLAGER_DATA.put(tag.copy(), parsed);
        }
        return parsed;
    }

    /** A villager's biome clothes and trade hat textures (as its profession layer has them), by its parsed data. */
    private static final Map<VillagerData, ResourceLocation[]> DRESS = new IdentityHashMap<>();

    private static ResourceLocation[] dress(String prefix, CompoundTag tag) {
        VillagerData data = villagerData(tag);
        if (data == null) return new ResourceLocation[0];
        return DRESS.computeIfAbsent(data, d -> {
            ResourceLocation biome = BuiltInRegistries.VILLAGER_TYPE.getKey(d.getType());
            ResourceLocation job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(d.getProfession());
            return new ResourceLocation[]{
                    biome.withPath(p -> "textures/entity/" + prefix + "/type/" + p + ".png"),
                    d.getProfession() == VillagerProfession.NONE ? null : job.withPath(p -> "textures/entity/" + prefix + "/profession/" + p + ".png")};
        });
    }

    // --- for the other renderers: a creature's head part and texture --------------------------------------------------

    /** A mob's head part and its texture (the part as the mob's model has it; restore its pose after drawing). */
    record Head(ModelPart part, ResourceLocation texture) {
    }

    /** The head of {@code type} (a villager dressed by its data; a creature with no head, its whole model), or null. */
    static Head head(EntityType<?> type, CompoundTag villagerData) {
        Look look = look(type, 0, villagerData);
        return look == null ? null : look.asHead;
    }
}
