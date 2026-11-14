package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.clarity.Guideposts;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.host.HostDefinition;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.Handbook;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.menu.JarMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * What tells a player what they are and what to do next: the career ladder's steps, DOLORES's memos, the handbook's
 * written edition, and the names the rider's HUD shows. Each goes through the path the game itself takes: the player's
 * tick event, a real slot click, a real block placement, the advancement being awarded.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClarityGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** The server's once-a-second look, as the player's own tick event delivers it. */
    private static void second(ServerPlayer p) {
        p.tickCount = (p.tickCount / 20 + 1) * 20;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
    }

    private static boolean earned(GameTestHelper h, ServerPlayer p, String id) {
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id(id));
        if (holder == null) throw new net.minecraft.gametest.framework.GameTestAssertException("no advancement " + id);
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static int memos(ServerPlayer p, Memos.Memo memo) {
        String want = p.getUUID() + ":" + memo.name();
        return (int) Memos.history().stream().filter(want::equals).count();
    }

    // --- the career ladder ------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void ladderStepsFollowWhatThePlayerDoes(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        // the first step is an item: Surgical Steel in the pocket
        p.getInventory().add(new ItemStack(ModRegistry.SURGICAL_STEEL_INGOT.get()));
        p.inventoryMenu.broadcastChanges(); // as the server does every tick: the inventory's change reaches the advancement
        h.assertTrue(earned(h, p, "steel"), "Surgical Steel in the pocket did not earn Steel Yourself");
        // a placed block: the Separation Chair set down by a real use of its item, against a fed Galvanic Pile
        h.setBlock(FLOOR.east(4), ModRegistry.GALVANIC_PILE.get());
        ((com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity) h.getBlockEntity(FLOOR.east(4))).energy().set(1000);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SEPARATION_CHAIR_ITEM.get()));
        BlockPos at = h.absolutePos(FLOOR.east(3));
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at.below()).add(0, 0.5, 0), Direction.UP, at.below(), false));
        h.assertTrue(earned(h, p, "chair"), "placing a Separation Chair did not earn Take a Seat");
        // the jar's steps, from its own state: a module fitted through the jar menu's real slot click
        FormActions.detach(p, 3);
        Guideposts.forget(p);
        second(p);
        h.assertFalse(earned(h, p, "first_module"), "a bare jar was told it had a module");
        JarMenu menu = new JarMenu(1, p.getInventory());
        p.containerMenu = menu;
        menu.setCarried(new ItemStack(ModRegistry.MODULES.get(com.coolfreeze23.headinajar.module.ModuleType.MANIPULATOR_ARM).get()));
        menu.clicked(0, 0, ClickType.PICKUP, p);
        h.assertTrue(FormActions.state(p).has(com.coolfreeze23.headinajar.module.ModuleType.MANIPULATOR_ARM), "setup: the arm did not go in");
        second(p);
        h.assertTrue(earned(h, p, "first_module") && earned(h, p, "armed"), "a fitted Manipulator Arm earned neither step");
        // charge rising between two looks (the feed slot, a Dock or a Cradle)
        JarState s = FormActions.state(p);
        s.setCharge(10_000);
        second(p);
        s.setCharge(20_000);
        second(p);
        h.assertTrue(earned(h, p, "charged"), "charge rising by 10 kFE did not earn Plugged In");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void rootIsOnTheFirstTickAndHasTheNextSteps(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.tick();
        h.assertTrue(earned(h, p, "root"), "Orientation Day was not granted on the first tick");
        var server = h.getLevel().getServer().getAdvancements();
        for (String id : List.of("steel", "saline")) {
            var adv = server.get(HeadInAJar.id(id));
            h.assertTrue(adv != null && adv.value().parent().orElseThrow().equals(HeadInAJar.id("root")), id + " does not hang from the root");
        }
        h.assertTrue(server.get(HeadInAJar.id("signed")).value().parent().orElseThrow().equals(HeadInAJar.id("chair")), "signing does not follow the chair");
        done(h, p);
    }

    // --- DOLORES's memos --------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void memosComeOnceAtTheMomentsTheyAreAbout(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        second(p);
        h.assertTrue(memos(p, Memos.Memo.NO_HANDS) == 0, "a whole body was told it has no hands");
        FormActions.detach(p, 3);
        JarState s = FormActions.state(p);
        s.setCharge(s.chargeCapacity());
        second(p);
        second(p);
        h.assertTrue(memos(p, Memos.Memo.NO_HANDS) == 1, "the no-hands memo came " + memos(p, Memos.Memo.NO_HANDS) + " times");
        h.assertTrue(memos(p, Memos.Memo.CHARGE_LOW) == 0, "a full jar was told its charge is low");
        s.setCharge(s.chargeCapacity() / 5);
        second(p);
        h.assertTrue(memos(p, Memos.Memo.CHARGE_LOW) == 1, "a jar at a fifth was not told");
        // a linked body that left its head out (the chair's own result): told once that it drinks, then once that it dries
        ServerPlayer q = player(h, FLOOR.south(3));
        h.assertTrue(FormActions.separate(q, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.south(5))), null), "setup: no separation");
        JarState t = FormActions.state(q);
        second(q);
        h.assertTrue(memos(q, Memos.Memo.HEAD_LEFT) == 1 && memos(q, Memos.Memo.HEAD_DRYING) == 0, "head left out: left "
                + memos(q, Memos.Memo.HEAD_LEFT) + ", drying " + memos(q, Memos.Memo.HEAD_DRYING) + " (form " + t.form() + ", head at "
                + t.headAt() + ", carried " + t.carryingHead() + ", saline " + t.saline() + "/" + t.maxSaline() + ")");
        t.setSaline(t.maxSaline() / 5);
        second(q);
        second(q);
        h.assertTrue(memos(q, Memos.Memo.HEAD_DRYING) == 1, "a drying head was told " + memos(q, Memos.Memo.HEAD_DRYING) + " times");
        // remembered where death can't reach it
        h.assertTrue(Memos.sent(q, Memos.Memo.HEAD_DRYING)
                && q.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(Memos.SENT) != 0, "memos are not remembered past death");
        // the memo names keys as the reader bound them
        Component line = Memos.line(Memos.Memo.NO_HANDS, Component.keybind("key.headinajar.jar_menu"), Component.literal("x"));
        h.assertTrue(line.getString().length() > 0 && hasKeybind(line), "the memo names no key binding");
        done(h, p, q);
    }

    private static boolean hasKeybind(Component c) {
        if (c.getContents() instanceof net.minecraft.network.chat.contents.KeybindContents) return true;
        if (c.getContents() instanceof TranslatableContents t) {
            for (Object a : t.getArgs()) if (a instanceof Component ac && hasKeybind(ac)) return true;
        }
        for (Component s : c.getSiblings()) if (hasKeybind(s)) return true;
        return false;
    }

    @GameTest(template = "platform")
    public static void aFirstHostAndAFirstMechaAreExplained(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(2));
        zombie.setHealth(3);
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), zombie, InteractionHand.MAIN_HAND);
        h.assertTrue(Hosts.isHost(zombie), "setup: no host");
        h.assertTrue(FormActions.mountHost(p, zombie), "setup: could not ride the host");
        second(p);
        h.assertTrue(memos(p, Memos.Memo.FIRST_HOST) == 1, "riding a host for the first time was not explained");
        // a mecha's first assembly: the advancement the Gantry awards brings the boarding memo
        AdvancementHolder assembled = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id("assembled"));
        second(p);
        for (String c : assembled.value().criteria().keySet()) p.getAdvancements().award(assembled, c);
        second(p);
        h.assertTrue(memos(p, Memos.Memo.MECHA_BUILT) == 1, "a first mecha was not followed by how to board it");
        done(h, p);
    }

    // --- what the rider's HUD names, and the handbook -----------------------------------------------------------

    @GameTest(template = "platform")
    public static void everyHostAbilityHasAName(GameTestHelper h) {
        Language lang = Language.getInstance();
        List<String> missing = new ArrayList<>();
        for (HostDefinition def : Hosts.definitions().values()) {
            for (var a : List.of(def.primary(), def.secondary())) {
                a.ifPresent(ability -> {
                    if (!lang.has("ability.headinajar." + ability.type())) missing.add(ability.type());
                });
            }
        }
        for (Memos.Memo m : Memos.Memo.values()) if (!lang.has(m.key())) missing.add(m.key());
        h.assertTrue(missing.isEmpty(), "no name for " + missing);
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void theWrittenEditionHoldsTheWholeHandbook(GameTestHelper h) {
        var recipe = h.getLevel().getServer().getRecipeManager().byKey(Handbook.WRITTEN_RECIPE);
        if (recipe.isEmpty()) {
            // Patchouli is installed on this server, so the written edition's recipe is switched off: the gift is the
            // Patchouli book, built from the same text by the same generator
            h.assertTrue(Handbook.patchouli(), "neither edition's recipe loaded");
            h.succeed();
            return;
        }
        ItemStack book = recipe.get().value().getResultItem(h.getLevel().registryAccess());
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        h.assertTrue(content != null && content.pages().size() >= 80 && content.pages().size() <= 100,
                "the written edition has " + (content == null ? 0 : content.pages().size()) + " pages");
        // the contents page sends the reader to each chapter
        int links = 0;
        for (Component part : content.pages().get(1).raw().getSiblings()) {
            ClickEvent click = part.getStyle().getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.CHANGE_PAGE) links++;
        }
        h.assertTrue(links >= 9, "the contents page links " + links + " chapters");
        // every page's text is a translation (each reader's client shows it in their own language) that exists
        Language lang = Language.getInstance();
        for (var page : content.pages()) {
            for (Component part : page.raw().getSiblings()) {
                if (part.getContents() instanceof TranslatableContents t && !lang.has(t.getKey())) {
                    throw new net.minecraft.gametest.framework.GameTestAssertException("no text for " + t.getKey());
                }
            }
        }
        h.succeed();
    }

    // --- the first-day handbook never pushes aside what is in hand ---------------------------------------------------

    @GameTest(template = "platform")
    public static void theFirstDayHandbookLandsAwayFromTheHand(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().selected = 8;
        p.getInventory().setItem(7, new ItemStack(net.minecraft.world.item.Items.BREAD));
        Handbook.giveToTestPlayers = true;
        try {
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
        } finally {
            Handbook.giveToTestPlayers = false;
        }
        ItemStack want = Handbook.copy(h.getLevel().getServer());
        var inv = p.getInventory();
        h.assertTrue(inv.getItem(8).isEmpty(), "the handbook went into the slot in hand");
        h.assertTrue(ItemStack.isSameItemSameComponents(inv.getItem(6), want), "the handbook is not in the first free hotbar slot from the right: "
                + inv.getItem(6));
        h.assertTrue(inv.getItem(7).is(net.minecraft.world.item.Items.BREAD), "the bread was moved");
        // a full hotbar: the inventory's last free slot
        ServerPlayer q = player(h, FLOOR.east(2));
        for (int i = 0; i < 9; i++) q.getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.DIRT));
        h.assertTrue(Handbook.place(q, want.copy()) == 35, "a full hotbar's gift did not go to the inventory");
        done(h, p, q);
    }

    // --- a dud mutation, and the status command in the reader's own words ----------------------------------------

    @GameTest(template = "platform")
    public static void aDudSerumIsADudNotARejection(GameTestHelper h) {
        var mecha = com.coolfreeze23.headinajar.registry.ModRegistry.mechaType(com.coolfreeze23.headinajar.mecha.MechaFrame.CHIMERA).create(h.getLevel());
        h.assertTrue(mecha != null, "setup: no mecha");
        mecha.moveTo(h.absoluteVec(Vec3.atBottomCenterOf(FLOOR)));
        h.getLevel().addFreshEntity(mecha);
        // a random that always mutates and always rolls the table's dud
        var dud = new net.minecraft.world.level.levelgen.LegacyRandomSource(0) {
            @Override
            public float nextFloat() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return bound == 100 ? 85 : 0;
            }
        };
        var gene = java.util.Arrays.stream(com.coolfreeze23.headinajar.genetics.Gene.values()).filter(g -> g.active).findFirst().orElseThrow();
        var result = com.coolfreeze23.headinajar.genetics.Genetics.inject(mecha,
                new com.coolfreeze23.headinajar.genetics.Serum(List.of(new com.coolfreeze23.headinajar.genetics.Serum.Entry(gene, 1f)), 20),
                com.coolfreeze23.headinajar.genetics.Genetics.Target.MECHA, dud);
        h.assertTrue(result == com.coolfreeze23.headinajar.genetics.Genetics.Result.DUD, "a dud came back as " + result);
        h.assertTrue(Language.getInstance().has("message.headinajar.inject.dud"), "the dud has no line");
        mecha.discard();
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void headOfficeStatusIsInTheReadersLanguage(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        List<Component> said = new ArrayList<>();
        var source = p.createCommandSourceStack().withSource(new net.minecraft.commands.CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        });
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, "headoffice status");
        h.assertTrue(!said.isEmpty() && said.get(0).getContents() instanceof TranslatableContents, "no status line: " + said);
        var args = ((TranslatableContents) said.get(0).getContents()).getArgs();
        h.assertTrue(args[1] instanceof Component form && form.getContents() instanceof TranslatableContents t
                && t.getKey().equals("command.headinajar.status.form.body"), "the form is not translated: " + args[1]);
        done(h, p);
    }
}
