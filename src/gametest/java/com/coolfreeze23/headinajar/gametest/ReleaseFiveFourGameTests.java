package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.ArmAim;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The 0.5.4 fixes the server can prove: a bloodless tool on another bloodless kind names the tool that creature takes;
 * the Hemo-Syringe on a mecha's arm stops at a thick hide as the hand syringes do, and at a bloodless creature; a Board Meeting's motion carries only
 * when the ayes outnumber the nays, and its line says so.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReleaseFiveFourGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    /** A survival player who keeps every line shown to it (action bar or chat), in order, as the server sent them. */
    private static final class Listener {
        final ServerPlayer player;
        final List<Component> lines = new ArrayList<>();

        Listener(GameTestHelper h, BlockPos at) {
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "listener"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
                @Override
                public void displayClientMessage(Component line, boolean actionBar) {
                    lines.add(line);
                    super.displayClientMessage(line, actionBar);
                }
            };
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.spawnInvulnerableTime = 0;
            Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
            player.moveTo(v.x, v.y, v.z, 0, 0);
        }

        /** The key of the last line shown, and the keys of its translatable arguments. */
        String last() {
            return lines.isEmpty() ? "nothing" : keys(lines.getLast());
        }
    }

    private static String keys(Component line) {
        if (!(line.getContents() instanceof TranslatableContents t)) return line.getString();
        StringBuilder out = new StringBuilder(t.getKey());
        for (Object arg : t.getArgs()) {
            out.append(' ').append(arg instanceof Component c && c.getContents() instanceof TranslatableContents a ? a.getKey() : String.valueOf(arg));
        }
        return out.toString();
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static ServerPlayer pilot(GameTestHelper h, BlockPos at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }

    /** A machine of {@code frame} with these modules (each in the first free slot of its kind), full battery, facing south. */
    private static MechaEntity mecha(GameTestHelper h, MechaFrame frame, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(frame), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (MechaModule mod : mods) {
            int i = m.frame().firstIndex(mod.slot);
            while (used.contains(i)) i++;
            used.add(i);
            ItemStack stack = new ItemStack(ModRegistry.mechaModule(mod));
            MechaModuleItem.setSlider(stack, 50);
            slots.set(i, stack);
        }
        m.setModules(slots);
        m.setOwner(owner);
        m.setBattery(m.batteryCapacity());
        m.setYRot(0);
        m.setYBodyRot(0);
        m.yBodyRotO = 0;
        return m;
    }

    /** The player, as a jar, climbs in by a real click on the machine. */
    private static void board(GameTestHelper h, ServerPlayer p, MechaEntity m) {
        if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "the click did not board: " + FormActions.state(p).form());
        m.setOnGround(true);
    }

    /** Blood Samples the pilot was given, in the inventory or dropped beside the machine. */
    private static int samples(GameTestHelper h, ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(ModRegistry.BLOOD_SAMPLE.get())) n += s.getCount();
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8))) {
            if (e.getItem().is(ModRegistry.BLOOD_SAMPLE.get())) n += e.getItem().getCount();
        }
        return n;
    }

    /** The tool in the main hand used on the creature by a click (the interact event's path); returns the line shown. */
    private static String use(Listener l, Item tool, LivingEntity target) {
        l.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        l.lines.clear();
        l.player.interactOn(target, InteractionHand.MAIN_HAND);
        return l.last();
    }

    /**
     * A bloodless tool on a creature of another bloodless kind says which tool that creature takes (a Marrow Drill on an
     * iron golem: the Oil Tap), instead of the line for a syringe that lists all three, the tool in hand among them. A
     * syringe on a bloodless creature and a bloodless tool on a creature with blood keep their lines, and nothing is
     * drawn by any of them.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void aBloodlessToolNamesTheToolTheCreatureTakes(GameTestHelper h) {
        Listener l = new Listener(h, FLOOR);
        Mob golem = h.spawn(EntityType.IRON_GOLEM, FLOOR.east(2));
        Mob skeleton = h.spawn(EntityType.SKELETON, FLOOR.west(2));
        Mob snowman = h.spawn(EntityType.SNOW_GOLEM, FLOOR.south(2));
        Mob pig = h.spawn(EntityType.PIG, FLOOR.north(2));
        for (Mob m : List.of(golem, skeleton, snowman, pig)) m.setNoAi(true);

        h.assertValueEqual(use(l, ModRegistry.MARROW_DRILL.get(), golem), "message.headinajar.other_bloodless_tool item.headinajar.oil_tap",
                "a Marrow Drill on an iron golem");
        h.assertValueEqual(use(l, ModRegistry.ECTO_TRAP.get(), golem), "message.headinajar.other_bloodless_tool item.headinajar.oil_tap",
                "an Ecto Trap on an iron golem");
        h.assertValueEqual(use(l, ModRegistry.OIL_TAP.get(), skeleton), "message.headinajar.other_bloodless_tool item.headinajar.marrow_drill",
                "an Oil Tap on a skeleton");
        h.assertValueEqual(use(l, ModRegistry.MARROW_DRILL.get(), snowman), "message.headinajar.other_bloodless_tool item.headinajar.ecto_trap",
                "a Marrow Drill on a snow golem");
        h.assertValueEqual(use(l, ModRegistry.SYRINGE.get(), golem), "message.headinajar.bloodless", "a Syringe on an iron golem");
        h.assertValueEqual(use(l, ModRegistry.MARROW_DRILL.get(), pig), "message.headinajar.wrong_tool", "a Marrow Drill on a pig");
        boolean drawn = l.player.getInventory().items.stream().anyMatch(s -> s.is(ModRegistry.BLOOD_SAMPLE.get()));
        h.assertFalse(drawn, "a wrong tool drew a sample");
        // the right tool still draws
        use(l, ModRegistry.OIL_TAP.get(), golem);
        h.assertTrue(l.player.getInventory().items.stream().anyMatch(s -> s.is(ModRegistry.BLOOD_SAMPLE.get())), "the Oil Tap drew nothing from the golem");
        done(h, l.player);
    }

    /**
     * The Hemo-Syringe on a Hopper's arm, pressed by the arm buttons' payload while the pilot looks at an Elder Guardian:
     * refused with the hand syringes' own line, and the guardian is neither jabbed nor drawn from. Pressed again at a
     * cow in the same place, it jabs and draws a sample as before.
     */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void theHemoSyringeArmStopsAtAThickHide(GameTestHelper h) {
        ServerPlayer p = pilot(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.HEMO_SYRINGE);
        board(h, p, m);
        BlockPos spot = FLOOR.east(3).south(3);
        Mob guardian = h.spawn(EntityType.ELDER_GUARDIAN, spot);
        guardian.setNoAi(true);
        Mob[] cow = new Mob[1];
        h.runAfterDelay(2, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, guardian.getBoundingBox().getCenter());
            Network.arms(p, 1);
        });
        h.runAfterDelay(3, () -> Network.arms(p, 0));
        h.runAfterDelay(4 + ArmAim.STRIKE_CONTACT + 2, () -> {
            h.assertTrue(Refusals.delivered(p).contains("mecha.arm.hemo_hide"), "no thick-hide refusal: " + Refusals.delivered(p));
            h.assertValueEqual(guardian.getHealth(), guardian.getMaxHealth(), "the guardian's health after the press");
            h.assertValueEqual(samples(h, p), 0, "samples drawn from the guardian");
            guardian.discard();
            cow[0] = h.spawn(EntityType.COW, spot);
            cow[0].setNoAi(true);
        });
        h.runAfterDelay(4 + ArmAim.STRIKE_CONTACT + 20, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, cow[0].getBoundingBox().getCenter());
            Network.arms(p, 1);
        });
        h.runAfterDelay(4 + ArmAim.STRIKE_CONTACT + 21, () -> Network.arms(p, 0));
        h.runAfterDelay(4 + 2 * ArmAim.STRIKE_CONTACT + 26, () -> {
            h.assertTrue(cow[0].getHealth() < cow[0].getMaxHealth(), "the cow was not jabbed");
            h.assertValueEqual(samples(h, p), 1, "samples drawn from the cow");
            cow[0].discard();
            done(h, p);
        });
    }

    /**
     * The Hemo-Syringe on a Hopper's arm, pressed at an iron golem (oil, bloodless): refused with the wrong-tool line
     * that names the Oil Tap, and the golem is not hurt and nothing is drawn.
     */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theHemoSyringeArmRefusesABloodlessCreature(GameTestHelper h) {
        ServerPlayer p = pilot(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.HEMO_SYRINGE);
        board(h, p, m);
        Mob golem = h.spawn(EntityType.IRON_GOLEM, FLOOR.east(3).south(3));
        golem.setNoAi(true);
        h.runAfterDelay(2, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, golem.getBoundingBox().getCenter());
            Network.arms(p, 1);
        });
        h.runAfterDelay(3, () -> Network.arms(p, 0));
        h.runAfterDelay(4 + ArmAim.STRIKE_CONTACT + 2, () -> {
            h.assertTrue(Refusals.delivered(p).contains("mecha.arm.hemo_bloodless"), "no bloodless refusal: " + Refusals.delivered(p));
            h.assertValueEqual(golem.getHealth(), golem.getMaxHealth(), "the golem's health after the press");
            h.assertValueEqual(samples(h, p), 0, "samples drawn from the golem");
            golem.discard();
            done(h, p);
        });
    }

    /**
     * A Board Meeting of five heads (then six) votes on dental cover: 1 for and 4 against is not carried, 3 for and 2
     * against is, and a tie, 3 to 3, is not. The line each time reaches a player beside the Array with the true count.
     */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aBoardMotionCarriesOnlyWhenTheAyesHaveIt(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CEREBRAL_ARRAY.get());
        var array = (CerebralArrayBlockEntity) h.getBlockEntity(FLOOR);
        Mob zombie = h.spawn(EntityType.ZOMBIE, FLOOR.above(3));
        ItemStack head = JarredHeadItem.of(zombie);
        zombie.discard();
        for (int i = 0; i < 5; i++) array.items().setStackInSlot(i, head.copy());
        Listener l = new Listener(h, FLOOR.south(2));
        int dental = 5;
        h.runAfterDelay(1, () -> array.callVote(h.getLevel(), 1, dental));
        h.runAfterDelay(14, () -> {
            h.assertValueEqual(l.last(), "array.headinajar.vote_lost array.headinajar.motion.dental 1 4", "1 for, 4 against");
            array.callVote(h.getLevel(), 3, dental);
        });
        h.runAfterDelay(28, () -> {
            h.assertValueEqual(l.last(), "array.headinajar.vote array.headinajar.motion.dental 3 2", "3 for, 2 against");
            array.items().setStackInSlot(5, head.copy());
            array.callVote(h.getLevel(), 3, dental);
        });
        h.runAfterDelay(42, () -> {
            h.assertValueEqual(l.last(), "array.headinajar.vote_lost array.headinajar.motion.dental 3 3", "3 for, 3 against");
            h.assertFalse(CerebralArrayBlockEntity.carried(4, 5) || CerebralArrayBlockEntity.carried(1, 8) || !CerebralArrayBlockEntity.carried(5, 4),
                    "the rule: more ayes than nays");
            done(h, l.player);
        });
    }
}
