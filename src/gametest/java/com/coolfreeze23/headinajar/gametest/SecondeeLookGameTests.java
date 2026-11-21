package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.HeadInfo;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.staff.second.SecondeeData;
import com.coolfreeze23.headinajar.staff.second.SecondeeLook;
import com.coolfreeze23.headinajar.staff.second.SecondeeSync;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.second.Secondment;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * What clients are told about a collared host (0.7.1, the optional payload {@code headinajar:secondee}): the payload
 * survives its codec, its steps are the sight glass's and the glass's, it goes out when a collar is fitted, a head
 * seated or unseated, a step changes and a player starts seeing the host, and never when nothing shown has changed;
 * the seat's effect has no pilot; and the words: Jade's lines and the staff list's kind.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class SecondeeLookGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A host made the saw's way (no jar in the off hand). */
    private static <T extends Mob> T host(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        h.assertTrue(SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw makes a host of the " + type.getDescription().getString());
        return mob;
    }

    /** A jarred head made the saw's way (a Life Support Jar in the off hand), taken out of the player's inventory. */
    private static ItemStack head(GameTestHelper h, ServerPlayer p, EntityType<? extends Mob> type, BlockPos at) {
        Mob mob = h.spawn(type, at);
        mob.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND);
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ModRegistry.JARRED_HEAD.get()) && JarredHeadItem.type(s) == type) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
                return s;
            }
        }
        throw new AssertionError("no jarred " + type.getDescription().getString() + " head");
    }

    /** Uses {@code stack} on the host through the collar's own path, as the player's main hand. */
    private static void use(ServerPlayer p, ItemStack stack, Mob host) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Secondment.instrument(p, p, p.getMainHandItem(), host, InteractionHand.MAIN_HAND);
    }

    /** A collared cow host with a zombie head seated on it by {@code p}. */
    private static Mob secondee(GameTestHelper h, ServerPlayer p) {
        Mob cow = host(h, p, EntityType.COW, FLOOR.east(2));
        use(p, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()), cow);
        h.assertTrue(Secondees.collared(cow), "the collar is fitted");
        use(p, head(h, p, EntityType.ZOMBIE, FLOOR.west(2)), cow);
        h.assertTrue(Secondees.seated(cow), "the zombie head is seated");
        return cow;
    }

    private static SecondeeSync.Sent last(Mob host) {
        List<SecondeeSync.Sent> sent = SecondeeSync.sent(host.getId());
        return sent.isEmpty() ? null : sent.get(sent.size() - 1);
    }

    private static int changes(Mob host) {
        return (int) SecondeeSync.sent(host.getId()).stream().filter(s -> s.why().equals("change")).count();
    }

    // --- the payload ----------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void theViewSurvivesItsCodec(GameTestHelper h) {
        CompoundTag villager = new CompoundTag();
        villager.putString("profession", "minecraft:librarian");
        villager.putString("type", "minecraft:plains");
        villager.putInt("level", 2);
        int step = SecondeeSync.COLLAR | SecondeeSync.SEATED | SecondeeSync.DRY | 2 << SecondeeSync.CANISTER_SHIFT
                | 3 << SecondeeSync.GLASS_SHIFT | 4 << SecondeeSync.QUARTER_SHIFT;
        SecondeeSync.View sent = new SecondeeSync.View(4321, step, "minecraft:villager", HeadInfo.COMPACT, villager, 0);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        SecondeeSync.View.CODEC.encode(buf, sent);
        SecondeeSync.View got = SecondeeSync.View.CODEC.decode(buf);
        h.assertTrue(buf.readableBytes() == 0, "the whole payload is read back");
        h.assertTrue(got.equals(sent), "the payload comes back as it went: " + got);
        h.assertTrue(got.collar() && got.seated() && got.dry(), "collar, seated and dry");
        h.assertTrue(got.canister() == 2 && got.glass() == 3 && got.quarter() == 4, "canister one third, glass stage 3, four quarters");
        SecondeeSync.View none = new SecondeeSync.View(7, 0, "", "", new CompoundTag(), 0);
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        SecondeeSync.View.CODEC.encode(buf, none);
        SecondeeSync.View gone = SecondeeSync.View.CODEC.decode(buf);
        h.assertTrue(gone.equals(none) && !gone.collar(), "no collar: the client forgets the host");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void theStepsAreTheSightGlassAndTheGlass(GameTestHelper h) {
        int[][] canister = {{500, 0}, {334, 0}, {333, 1}, {167, 1}, {166, 2}, {1, 2}, {0, 3}, {-5, 3}};
        for (int[] c : canister) {
            h.assertTrue(SecondeeSync.canisterStep(c[0]) == c[1], c[0] + " mB shows step " + c[1] + ", not " + SecondeeSync.canisterStep(c[0]));
        }
        int[][] glass = {{10, 10, 0}, {8, 10, 0}, {7, 10, 1}, {5, 10, 2}, {3, 10, 2}, {2, 10, 3}, {0, 10, 3}, {30, 30, 0}, {22, 30, 1}};
        for (int[] g : glass) {
            h.assertTrue(SecondeeSync.glassStage(g[0], g[1]) == g[2], "glass " + g[0] + "/" + g[1] + " is stage " + g[2]);
        }
        h.succeed();
    }

    // --- what is sent when ----------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aFittedCollarThenASeatedHeadAreSent(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "Look", FLOOR);
        Mob cow = host(h, p, EntityType.COW, FLOOR.east(2));
        h.assertTrue(SecondeeSync.sent(cow.getId()).isEmpty(), "a plain host is never sent");
        use(p, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()), cow);
        SecondeeSync.Sent fitted = last(cow);
        h.assertTrue(fitted != null && fitted.why().equals("change"), "the fitted collar is sent");
        h.assertTrue(fitted.view().collar() && !fitted.view().seated() && fitted.view().head().isEmpty(), "an empty collar");
        h.assertTrue(fitted.view().canister() == 3, "its canister is empty");
        use(p, head(h, p, EntityType.ZOMBIE, FLOOR.west(2)), cow);
        SecondeeSync.Sent seated = last(cow);
        h.assertTrue(seated != fitted && seated.view().seated(), "the seated head is sent");
        h.assertTrue(seated.view().head().equals("minecraft:zombie"), "its species: " + seated.view().head());
        h.assertTrue(seated.view().jar().equals(HeadInfo.LIFE_SUPPORT), "its jar: " + seated.view().jar());
        h.assertTrue(seated.view().glass() == 0 && seated.view().quarter() == 0, "whole glass, no team sync yet");
        h.assertTrue(seated.view().dry(), "seated on an empty canister it is dry (it sags until topped up)");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aVillagerHeadIsSentWithItsLook(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "Look", FLOOR);
        Mob cow = host(h, p, EntityType.COW, FLOOR.east(2));
        use(p, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()), cow);
        net.minecraft.world.entity.npc.Villager villager = h.spawn(EntityType.VILLAGER, FLOOR.west(2));
        villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
        villager.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), villager, InteractionHand.MAIN_HAND);
        ItemStack head = p.getInventory().items.stream().filter(s -> s.is(ModRegistry.JARRED_HEAD.get())).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(JarredHeadItem.type(head) == EntityType.VILLAGER, "a jarred villager head");
        use(p, head.copy(), cow);
        SecondeeSync.View v = last(cow).view();
        h.assertTrue(v.head().equals("minecraft:villager"), "a villager head");
        h.assertTrue(v.villager().getString("profession").endsWith("librarian"), "its profession travels for its look: " + v.villager());
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void onlyAChangedStepIsSent(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "Look", FLOOR);
        Mob cow = secondee(h, p);
        SecondeeData d = Secondees.data(cow);
        int n = changes(cow);
        SecondeeSync.changed(cow);
        h.assertTrue(changes(cow) == n, "nothing changed: nothing sent");
        d.canister = SecondeeData.CANISTER;
        SecondeeSync.changed(cow);
        h.assertTrue(changes(cow) == n + 1 && last(cow).view().canister() == 0 && !last(cow).view().dry(), "topped up: full and no longer dry");
        d.canister = 480;
        SecondeeSync.changed(cow);
        d.canister = 334;
        SecondeeSync.changed(cow);
        h.assertTrue(changes(cow) == n + 1, "drinking inside a step sends nothing");
        d.canister = 333;
        SecondeeSync.changed(cow);
        h.assertTrue(changes(cow) == n + 2 && last(cow).view().canister() == 1, "down to two thirds: sent");
        d.teamSync = 24.9f;
        SecondeeSync.changed(cow);
        h.assertTrue(changes(cow) == n + 2, "team sync inside a quarter sends nothing");
        d.teamSync = 25;
        SecondeeSync.changed(cow);
        h.assertTrue(changes(cow) == n + 3 && last(cow).view().quarter() == 1, "a quarter of team sync: sent (the client's ring runs)");
        d.glass = 7;
        SecondeeSync.changed(cow);
        h.assertTrue(changes(cow) == n + 4 && last(cow).view().glass() == 1, "the glass down a stage: sent");
        d.canister = 0;
        SecondeeSync.changed(cow);
        h.assertTrue(changes(cow) == n + 5 && last(cow).view().dry() && last(cow).view().canister() == 3, "run dry: sent, the head sags");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void throughTheCollarEachAnswerIsSent(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "Look", FLOOR);
        Mob cow = secondee(h, p);
        int n = changes(cow);
        use(p, new ItemStack(ModFluidsBucket.broth()), cow);
        h.assertTrue(changes(cow) == n + 1 && last(cow).view().canister() == 0, "a bucket of broth: the canister shows full");
        Secondees.data(cow).glass = 2;
        SecondeeSync.changed(cow);
        h.assertTrue(last(cow).view().glass() == 3, "glass 2 of 10: stage 3");
        n = changes(cow);
        use(p, new ItemStack(ModRegistry.GLASS_PATCH.get()), cow);
        h.assertTrue(changes(cow) == n + 1 && last(cow).view().glass() == 1, "a Glass Patch: the glass shows mended (7 of 10)");
        // Sneak and Use with an empty hand: the head comes off and the empty collar is sent
        p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Secondment.instrument(p, p, ItemStack.EMPTY, cow, InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        h.assertFalse(Secondees.seated(cow), "the head is unseated");
        SecondeeSync.View empty = last(cow).view();
        h.assertTrue(empty.collar() && !empty.seated() && empty.head().isEmpty(), "unseated: the empty collar is sent");
        // shears on the empty collar: it comes off and the client is told to forget it
        use(p, new ItemStack(Items.SHEARS), cow);
        h.assertFalse(Secondees.collared(cow), "the collar is off");
        h.assertTrue(last(cow).view().step() == 0 && !last(cow).view().collar(), "the collar off is sent");
        done(h, p);
    }

    /**
     * A player who starts seeing a collared host is sent it. A mock player's game never acknowledges chunks, so the
     * chunk map never pairs it with the host: the game's own StartTracking event is posted as the chunk map posts it.
     */
    @GameTest(template = "platform")
    public static void aPlayerWhoStartsSeeingASecondeeIsSentIt(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "Look", FLOOR);
        Mob cow = secondee(h, p);
        ServerPlayer late = TestPlayers.at(h, "Late", FLOOR.north(2));
        java.util.function.Supplier<Long> tracked = () -> SecondeeSync.sent(cow.getId()).stream().filter(s -> s.why().equals("tracking")).count();
        long before = tracked.get();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking(late, cow));
        h.assertTrue(tracked.get() == before + 1, "a player who comes into view is sent the secondee");
        SecondeeSync.Sent t = last(cow);
        h.assertTrue(t.why().equals("tracking") && t.view().seated() && t.view().head().equals("minecraft:zombie"), "with what is on its neck");
        Mob plain = host(h, p, EntityType.PIG, FLOOR.south(2));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking(late, plain));
        h.assertTrue(SecondeeSync.sent(plain.getId()).isEmpty(), "a host with no collar is never sent");
        done(h, p, late);
    }

    @GameTest(template = "platform")
    public static void theSeatHasNoPilot(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "Look", FLOOR);
        Mob cow = secondee(h, p);
        h.assertTrue(Fx.count(Fx.HOST_SEAT, cow.getId()) == 1, "the seat's effect is sent once (the collar twists and the body braces on every client)");
        h.assertTrue(Fx.count(Fx.HOST_SEAT, p.getId()) == 0, "it names no player as the pilot");
        Fx seat = Fx.sent().stream().map(Fx.Sent::fx).filter(f -> f.kind() == Fx.HOST_SEAT && f.entityB() == cow.getId())
                .reduce((a, b) -> b).orElse(null);
        h.assertTrue(seat == null || seat.entityA() == -1, "its pilot is -1: " + (seat == null ? "-" : seat.entityA()));
        done(h, p);
    }

    /**
     * An idle secondee's head comes back to face where its body faces (the jar's head looks along {@code yHeadRot -
     * yBodyRot}): the staff brain ticks the body's look control every tick, which turns a head with nothing to look at
     * back toward the body, and the body turns after a head left turned. A head left 90 degrees round is square again
     * within 40 ticks.
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void anIdleHeadComesBackToFaceForward(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "Look", FLOOR);
        Mob cow = secondee(h, p);
        Secondees.data(cow).canister = SecondeeData.CANISTER;
        p.teleportTo(p.getX(), p.getY() + 40, p.getZ());
        cow.yBodyRot = 0;
        cow.setYRot(0);
        cow.setYHeadRot(90);
        h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(cow.getYHeadRot() - cow.yBodyRot)) > 80, "the head starts turned round");
        h.runAfterDelay(40, () -> {
            float off = Math.abs(net.minecraft.util.Mth.wrapDegrees(cow.getYHeadRot() - cow.yBodyRot));
            h.assertTrue(off < 5, "the head faces where the body faces again (off by " + off + " degrees)");
            done(h, p);
        });
    }

    // --- the words ------------------------------------------------------------------------------------------------

    private static TranslatableContents tr(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t : null;
    }

    private static String arg(Object a) {
        if (a instanceof Component c && c.getContents() instanceof TranslatableContents t) return t.getKey();
        return String.valueOf(a);
    }

    @GameTest(template = "platform")
    public static void jadeAndTheStaffListSayHeadAndBody(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "Look", FLOOR);
        Mob cow = host(h, p, EntityType.COW, FLOOR.east(2));
        use(p, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()), cow);
        SecondeeData d = Secondees.data(cow);
        d.canister = 320;
        CompoundTag data = new CompoundTag();
        SecondeeLook.jadeData(data, cow);
        List<Component> lines = SecondeeLook.jadeLines(data, cow.getType());
        h.assertTrue(lines.size() == 1 && tr(lines.get(0)).getKey().equals("jade.headinajar.secondee.vacant"), "an empty collar: position vacant");
        h.assertTrue(arg(tr(lines.get(0)).getArgs()[0]).equals("320"), "with its broth");
        h.assertTrue(SecondeeLook.listKind(cow) == null, "an empty collar is not on the staff list as a secondee");
        use(p, head(h, p, EntityType.ZOMBIE, FLOOR.west(2)), cow);
        d.teamSync = 34.2f;
        d.canister = 320;
        d.glass = 8;
        data = new CompoundTag();
        SecondeeLook.jadeData(data, cow);
        lines = SecondeeLook.jadeLines(data, cow.getType());
        h.assertTrue(lines.size() == 2, "two lines: " + lines.size());
        TranslatableContents first = tr(lines.get(0)), second = tr(lines.get(1));
        h.assertTrue(first.getKey().equals("jade.headinajar.secondee"), "Zombie head on a Cow body");
        h.assertTrue(arg(first.getArgs()[0]).equals("entity.minecraft.zombie") && arg(first.getArgs()[1]).equals("entity.minecraft.cow")
                && arg(first.getArgs()[2]).equals("34"), "the head, the body and team sync 34%");
        h.assertTrue(second.getKey().equals("jade.headinajar.secondee.upkeep") && arg(second.getArgs()[0]).equals("320")
                && arg(second.getArgs()[1]).equals("8") && arg(second.getArgs()[2]).equals("10"), "Broth 320 mB, Glass 8/10");
        TranslatableContents kind = tr(SecondeeLook.listKind(cow));
        h.assertTrue(kind.getKey().equals("command.headinajar.staff.secondee") && arg(kind.getArgs()[0]).equals("entity.minecraft.zombie")
                && arg(kind.getArgs()[1]).equals("entity.minecraft.cow") && arg(kind.getArgs()[2]).equals("34"), "the staff list: Zombie head, Cow body, team sync 34%");
        done(h, p);
    }

    /** The Nutrient Broth Bucket. */
    private static final class ModFluidsBucket {
        static net.minecraft.world.item.Item broth() {
            return com.coolfreeze23.headinajar.registry.ModFluids.BROTH.bucket.get();
        }
    }
}
