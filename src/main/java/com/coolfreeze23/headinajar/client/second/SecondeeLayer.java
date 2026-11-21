package com.coolfreeze23.headinajar.client.second;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.client.HostHeads;
import com.coolfreeze23.headinajar.client.JarredHeadRenderer;
import com.coolfreeze23.headinajar.client.StaffBadge;
import com.coolfreeze23.headinajar.client.body.CollarRing;
import com.coolfreeze23.headinajar.client.staff.HardHatModel;
import com.coolfreeze23.headinajar.client.staff.WorkerLines;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * What a collared host wears at its neck, drawn after its body: the Secondment Collar (the brass ring, wide or compact
 * by the jar, with its broth canister on the back), the seated jarred head in the jar it went into, the Hard Hat on the
 * jar's lid (a secondee's head slot) and the staff badge clipped to the front of the collar. Exactly where a player's
 * jar sits on the same host ({@code Hosts.neck} turned by the body's yaw, as {@code HostControl.ridingPosition} has it),
 * and inside the body's own sag and sway ({@code HostFx}: this runs before its Post pops), so a rotting body carries its
 * jar down with it. The head looks where the host looks ({@code yHeadRot - yBodyRot}, within the jar's turn); out of
 * broth it sinks 2 px and hangs 15 degrees. An empty collar is the ring and canister alone; a collared host ridden by a
 * player's jar draws only the canister (the player's own ring is drawn round their jar).
 * <p>
 * Only creatures the server has said are collared ({@link SecondeeClient}) are touched: a map lookup for every other.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class SecondeeLayer {
    /** How far a dry head sinks in its jar (px) and hangs (degrees, down). */
    public static final float DRY_SAG = 2, DRY_PITCH = 15;
    /** The Hard Hat over the lid: where {@code JarRenderer.helmet} puts a jar's helmet. */
    private static final double HAT_Y = 0.98;
    private static final float HAT_SCALE = 0.62f;
    /**
     * The badge clipped to the front of the ring, hanging down over the neck: its frame's top at the ring's top (the ring
     * stands from 0.5 px below the jar's base to 1.5 above), 5 px square, the face a third of the way up it. The ring's
     * front face is 8 px out (wide) or 6.5 (compact).
     */
    private static final StaffBadge.Anchor BADGE_WIDE = new StaffBadge.Anchor(-2.5f, -3.5f, 2.5f, 1.5f, -1.5f, -2.5f, 1.5f, -0.5f, -8.05f);
    private static final StaffBadge.Anchor BADGE_COMPACT = new StaffBadge.Anchor(-2.5f, -3.5f, 2.5f, 1.5f, -1.5f, -2.5f, 1.5f, -0.5f, -6.55f);

    private SecondeeLayer() {
    }

    /** The jar's helmet (a Hard Hat) on a collared host is on its lid: the armour layer does not draw it where the head was. */
    @SubscribeEvent
    public static void pre(RenderLivingEvent.Pre<?, ?> event) {
        SecondeeClient.Seen s = SecondeeClient.of(event.getEntity());
        if (s == null || HardHatModel.model() == null) return;
        if (event.getEntity().getItemBySlot(EquipmentSlot.HEAD).is(WorkRegistry.HARD_HAT.get())) {
            HostHeads.hidePart(HardHatModel.model().head);
            HostHeads.hidePart(HardHatModel.model().hat);
        }
    }

    /** How high a seated jar reaches over the neck (blocks): its lid at 0.98, a Hard Hat on it 0.4 more. */
    private static final double JAR_TOP = 1.0, HAT_TOP = 0.4;
    /** The name tag's lift for the creature drawing now (undone at its Post), and that creature. */
    private static float lifted;
    private static LivingEntity liftedFor;

    /**
     * How far a seated secondee's name tag and floating lines go up so they clear its jar (and hat), blocks; 0 for
     * anything else. Vanilla puts a name tag 0.5 over the creature's own name-tag point, which on a headless body is
     * inside the jar.
     */
    public static float lift(net.minecraft.world.entity.Entity e) {
        SecondeeClient.Seen s = SecondeeClient.of(e);
        if (s == null || !s.seated || !(e instanceof LivingEntity living) || Hosts.pilot(e) != null) return 0;
        Vec3 tag = e.getAttachments().getNullable(net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, e.getYRot());
        double tagY = (tag == null ? e.getBbHeight() : tag.y) + 0.5;
        double top = Hosts.neck(e).y + JAR_TOP + (living.getItemBySlot(EquipmentSlot.HEAD).is(WorkRegistry.HARD_HAT.get()) ? HAT_TOP : 0) + 0.2;
        return (float) Math.max(0, top - tagY);
    }

    /** A seated secondee's name tag is drawn over its jar: the pose goes up for it, and back down at the Post. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void nameTag(net.neoforged.neoforge.client.event.RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof LivingEntity e)) return;
        float up = lift(e);
        if (up <= 0) return;
        event.getPoseStack().translate(0, up, 0);
        lifted = up;
        liftedFor = e;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void unlift(RenderLivingEvent.Post<?, ?> event) {
        if (liftedFor == null) return;
        if (liftedFor == event.getEntity()) event.getPoseStack().translate(0, -lifted, 0);
        liftedFor = null;
        lifted = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void post(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity host = event.getEntity();
        SecondeeClient.Seen s = SecondeeClient.of(host);
        if (s == null || host.isInvisible()) return;
        draw(host, s, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    /** Draws the collar and what is on it, the pose at the host's feet (as a renderer's Post leaves it). */
    public static void draw(LivingEntity host, SecondeeClient.Seen s, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float bodyYaw = Mth.rotLerp(pt, host.yBodyRotO, host.yBodyRot);
        Player rider = Hosts.pilot(host);
        boolean seated = s.seated && rider == null;
        boolean compact = rider != null ? rider.getData(ModRegistry.JAR_STATE).compact() : seated && s.compact;
        Vec3 at = Hosts.neck(host).yRot(-bodyYaw * Mth.DEG_TO_RAD);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        float twist = CollarRing.twist(host, pt);
        if (rider == null) CollarRing.render(pose, buffers, light, host, compact, pt);
        CollarCanister.render(pose, buffers, light, compact, s.canister, twist);
        if (seated) {
            // the badge turns with the ring (the ring's twist is about its own flipped axes: the other way round here)
            WorkerLines.Look look = WorkerLines.look(host);
            if (look != null && look.colour >= 0) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotation(-twist));
                StaffBadge.draw(pose, buffers, light, look.colour, compact ? BADGE_COMPACT : BADGE_WIDE);
                pose.popPose();
            }
            jar(host, s, pt, pose, buffers, light, bodyYaw);
            hat(host, pose, buffers, light);
        }
        pose.popPose();
    }

    /** The jar and the head in it, looking where the host looks; dry, sunk and hanging. */
    private static void jar(LivingEntity host, SecondeeClient.Seen s, float pt, PoseStack pose, MultiBufferSource buffers, int light, float bodyYaw) {
        float headYaw = Mth.rotLerp(pt, host.yHeadRotO, host.yHeadRot);
        float yaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float pitch = Mth.lerp(pt, host.xRotO, host.getXRot());
        JarredHeadRenderer.Extra extra = JarredHeadRenderer.Extra.NONE;
        if (s.dry) {
            pitch = DRY_PITCH;
            extra = new JarredHeadRenderer.Extra(0, 0, -DRY_SAG, 0, JarredHeadRenderer.Dress.NONE);
        }
        float age = host.tickCount + pt;
        JarredHeadRenderer.drawJar(s.head, s.villager, s.size, s.jar, pose, buffers, light, age, yaw, pitch, 1, extra);
    }

    /** A Hard Hat in the head slot, on the jar's lid where a jar's Hat Mount wears a helmet. */
    private static void hat(LivingEntity host, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack helmet = host.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.is(WorkRegistry.HARD_HAT.get())) return;
        pose.pushPose();
        pose.translate(0, HAT_Y, 0);
        pose.scale(HAT_SCALE, HAT_SCALE, HAT_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(helmet, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                host.level(), host.getId());
        pose.popPose();
    }
}
