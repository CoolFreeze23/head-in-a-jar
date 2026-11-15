package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.staff.SawTable;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffShake;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Use on a mob at half its health or less: its head comes off and it becomes a host body a jar can pilot. With an
 * empty jar in your off hand, the saw keeps the head instead (a jarred head) and the body falls. The saw's table
 * ({@link SawTable}) is read first: whatever it refuses costs nothing.
 */
public class SurgicalSawItem extends Item {
    /**
     * The saw takes a creature at this share of its health or less. Half, so every plain weapon from a fist to a diamond
     * sword or axe leaves the common hosts (cow, pig, sheep, villager, skeleton, zombie) inside it for at least one hit
     * before the hit that kills: a cow (10) goes 10, 5, dead to a stone sword, a sheep (8) 8, 4, dead to a wooden one.
     * Only a first hit that kills outright leaves no window (a stone, iron or diamond axe on a sheep).
     */
    public static final float MAX_HEALTH_FRACTION = 0.5f;
    /** A host body is raised to this share of its health if it was lower when sawn. */
    public static final float HOST_HEALTH_FLOOR = 0.25f;

    public SurgicalSawItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob)) {
            // an armour stand or the like: it came headless and empty (another player is answered before this)
            if (player instanceof ServerPlayer server && !(target instanceof Player)) {
                // a colleague's body left behind is not a specimen either
                if (target.getType() == com.coolfreeze23.headinajar.registry.ModRegistry.HEADLESS_BODY.get()) {
                    Refusals.deny(server, com.coolfreeze23.headinajar.feedback.Refuser.at(target), "item.on_colleague", stack.getHoverName());
                } else {
                    Refusals.deny(server, com.coolfreeze23.headinajar.feedback.Refuser.at(target), "saw.furniture");
                }
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        return saw((ServerPlayer) player, stack, mob, hand);
    }

    public static InteractionResult saw(ServerPlayer server, ItemStack stack, Mob mob, InteractionHand hand) {
        Player player = server;
        ItemStack offhand = player.getOffhandItem();
        SawTable.Verdict verdict = SawTable.verdict(mob, player, offhand, true);
        if (verdict.refused()) {
            // nothing spins up and nothing is spent: the line, the refusal sound, and the creature shakes its head
            Refusals.deny(server, StaffShake.at(mob), verdict.reason(), verdict.args());
            return InteractionResult.FAIL;
        }
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        if (verdict.pumpkin()) {
            // the snow golem's pumpkin comes off first (dropped, as shears drop it); the next cut is a normal cut
            ((SnowGolem) mob).shear(SoundSource.PLAYERS);
            stack.hurtAndBreak(1, player, slot);
            player.displayClientMessage(Component.translatable("message.headinajar.saw.pumpkin"), true);
            return InteractionResult.CONSUME;
        }
        ServerLevel level = server.serverLevel();
        // the company's own blade (the chair's old cut sound) under the cork's whine
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), com.coolfreeze23.headinajar.registry.ModSounds.BOOTH_CUT.get(), SoundSource.PLAYERS, 0.8f, 1);
        // the creature holds still for the cork's whine; the head pops on the third tick, in a sanitary puff. Into an
        // off-hand jar the head arcs only when the cut really kills (below): a creature that lives keeps its head.
        boolean intoJar = offhand.getItem() instanceof LifeSupportJarItem;
        if (!intoJar) com.coolfreeze23.headinajar.body.BodyServer.cut(server, mob, false);
        net.minecraft.world.phys.Vec3 neck = com.coolfreeze23.headinajar.body.BodyServer.neck(mob);
        Runnable puff = () -> com.coolfreeze23.headinajar.body.BodyServer.later(level, 3, () -> com.coolfreeze23.headinajar.feedback.Sanitary.puff(level, neck.x, neck.y, neck.z));
        if (!intoJar) puff.run();
        stack.hurtAndBreak(1, player, slot);

        if (intoJar) {
            // keep the head: it goes into the jar, the body falls. The head is taken as it was before the cut, and
            // handed over only if the cut really killed (a totem, a claim or Resistance can stop it). A creature that is
            // all head goes into the jar whole: nothing is left behind (no body, no drops, no split slime).
            boolean whole = SawTable.headOnly(mob);
            ItemStack head = JarredHeadItem.of(mob, offhand);
            if (whole) SawTable.swallow = mob.getUUID();
            try {
                mob.hurt(mob.damageSources().playerAttack(player), Float.MAX_VALUE);
            } finally {
                SawTable.swallow = null;
            }
            if (!mob.isDeadOrDying()) {
                Refusals.deny(server, com.coolfreeze23.headinajar.feedback.Refuser.at(mob), "saw.survived", mob.getDisplayName());
                return InteractionResult.CONSUME;
            }
            com.coolfreeze23.headinajar.body.BodyServer.cut(server, mob, true);
            puff.run();
            // the file goes with the head (one copy, on the head's own data)
            StaffFile file = Staff.register(mob, server, "jarred", true);
            Staff.write(head, file);
            if (whole) {
                // a slime splits when it is removed dead: it is the size it was on the head's record, and one now
                if (mob instanceof net.minecraft.world.entity.monster.Slime slime) slime.setSize(1, false);
                mob.discard();
            }
            if (mob.getType() == EntityType.SLIME) com.coolfreeze23.headinajar.Advance.grant(server, "entirely_head");
            offhand.consume(1, player);
            com.coolfreeze23.headinajar.form.FormActions.give(server, head);
            return InteractionResult.CONSUME;
        }
        HostData data = Hosts.data(mob);
        data.host = true;
        data.freshness = 100;
        data.sync = 0;
        mob.setPersistenceRequired();
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setHealth(Math.max(mob.getHealth(), mob.getMaxHealth() * HOST_HEALTH_FLOOR));
        Network.syncHost(mob);
        Staff.register(mob, server, "host", true);
        player.displayClientMessage(Component.translatable("message.headinajar.host_made", mob.getDisplayName()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.surgical_saw.how").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.headinajar.surgical_saw.how2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
