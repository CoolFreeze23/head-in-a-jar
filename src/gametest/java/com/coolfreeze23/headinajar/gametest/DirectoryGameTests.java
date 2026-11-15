package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.block.LeechJarBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.genetics.Directory;
import com.coolfreeze23.headinajar.genetics.Directory.Stamp;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Staff Directory (0.6.2): the hooks that stamp a species for a team (or a lone player), the backfill from an old
 * library, its own saved data beside the gene library, the milestones for the whole team, and its optional payload.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class DirectoryGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static Directory.Book book(GameTestHelper h, ServerPlayer p) {
        return Directory.get(h.getLevel().getServer()).book(GeneArchive.key(p));
    }

    private static EntityType<?> type(String id) {
        return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
    }

    /** A scoreboard team of these players, named so no other test's team shares it. */
    private static String team(GameTestHelper h, ServerPlayer... players) {
        Scoreboard board = h.getLevel().getScoreboard();
        String name = "dir" + UUID.randomUUID().toString().substring(0, 8);
        var team = board.addPlayerTeam(name);
        for (ServerPlayer p : players) board.addPlayerToTeam(p.getScoreboardName(), team);
        return name;
    }

    private static void disband(GameTestHelper h, String name) {
        Scoreboard board = h.getLevel().getScoreboard();
        if (board.getPlayerTeam(name) != null) board.removePlayerTeam(board.getPlayerTeam(name));
    }

    // --- the hooks -------------------------------------------------------------------------------------------------

    /**
     * A Syringe's draw files "sampled", the saw's cut into a jar files "jarred", boarding a host files "ridden"; a Leech
     * Jar emptied by a player and a Sequencer's read file "sampled" for that player's key. A teammate on the same
     * scoreboard team reads the same book; a player with no team keeps their own.
     */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void samplingJarringAndRidingFileTheSpecies(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "dirA", FLOOR), q = TestPlayers.at(h, "dirB", FLOOR.north()), r = TestPlayers.at(h, "dirC", FLOOR.south());
        String name = team(h, p, q);
        try {
            // sampled: a real draw
            Cow cow = h.spawn(EntityType.COW, FLOOR.east());
            cow.setNoAi(true);
            ItemStack syringe = new ItemStack(ModRegistry.SYRINGE.get());
            h.assertTrue(ModRegistry.SYRINGE.get().draw(p, syringe, cow, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "setup: the syringe did not draw");
            // jarred: the saw with a jar in the off hand
            Chicken chicken = h.spawn(EntityType.CHICKEN, FLOOR.east(2));
            chicken.setNoAi(true);
            chicken.setHealth(1);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
            p.interactOn(chicken, InteractionHand.MAIN_HAND);
            h.assertTrue(!chicken.isAlive(), "setup: the chicken was not jarred");
            // ridden: a host boarded in jar form
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.west(2));
            zombie.setNoAi(true);
            zombie.setHealth(3);
            SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), zombie, InteractionHand.MAIN_HAND);
            h.assertTrue(Hosts.isHost(zombie), "setup: no host");
            FormActions.detach(p, 3);
            h.assertTrue(FormActions.mountHost(p, zombie), "setup: could not ride the host");

            Directory.Book team = book(h, p);
            h.assertTrue(team.has(Stamp.SAMPLED, "minecraft:cow"), "the syringe's draw was not filed as sampled");
            h.assertTrue(team.has(Stamp.JARRED, "minecraft:chicken"), "the saw's jar was not filed as jarred");
            h.assertTrue(team.has(Stamp.RIDDEN, "minecraft:zombie"), "boarding a host was not filed as ridden");
            h.assertFalse(team.has(Stamp.JARRED, "minecraft:zombie"), "a host made by the saw was filed as jarred");
            h.assertTrue(team.filedBy().get("minecraft:cow").equals(p.getGameProfile().getName()), "the filer's name was not kept");
            h.assertTrue(book(h, q) == team, "a teammate does not share the team's book");
            Directory.Book alone = book(h, r);
            h.assertTrue(alone.counted().isEmpty(), "a player with no team shares someone's book: " + alone.counted());

            // the Leech Jar, emptied by the player with no team: its samples are theirs
            h.setBlock(FLOOR.above(2), ModRegistry.LEECH_JAR.get());
            var leech = (LeechJarBlockEntity) h.getBlockEntity(FLOOR.above(2));
            leech.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.SHEEP, 50));
            leech.empty(r);
            h.assertTrue(alone.has(Stamp.SAMPLED, "minecraft:sheep"), "emptying a Leech Jar did not file its samples");
            h.assertFalse(team.has(Stamp.SAMPLED, "minecraft:sheep"), "another player's Leech Jar filed for the team");

            // the Sequencer's read files for its owner (automation's samples come by no hand)
            BlockPos at = FLOOR.above(2).east(3);
            h.setBlock(at, ModRegistry.DNA_SEQUENCER.get());
            var s = (LabMachines.Sequencer) h.getBlockEntity(at);
            s.energy().set(40_000);
            s.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.RABBIT, 80));
            int seed = s.saveWithoutMetadata(h.getLevel().registryAccess()).getInt("Seed");
            h.assertTrue(s.button(r, LabMachines.Sequencer.CALL + LabMachines.Sequencer.encode(LabMachines.Sequencer.bases(seed))), "setup: the read was refused");
            h.assertTrue(alone.has(Stamp.SAMPLED, "minecraft:rabbit"), "a Sequencer's read did not file the species for its owner");
            h.assertFalse(team.has(Stamp.SAMPLED, "minecraft:rabbit"), "a Sequencer's read filed for someone else's key");
            h.assertTrue("".equals(alone.filedBy().get("minecraft:rabbit")), "a machine's read was filed under a name: " + alone.filedBy().get("minecraft:rabbit"));
        } finally {
            disband(h, name);
        }
        SawTableGameTests.done(h, p, q, r);
    }

    // --- old saves -----------------------------------------------------------------------------------------------

    /**
     * An old library marks "sampled" only for a species whose gene has no other source in this game and a read above 0:
     * Ruminant Gut is the cow's alone, Echolocation the bat's; Gills (five fish), Spider Climb, Phantom Membrane and
     * Guardian Eye are shared, so their species stay blank; a gene at 0% marks nothing. Jarred and ridden start blank.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void theBackfillMarksOnlySpeciesWithAGeneOfTheirOwn(GameTestHelper h) {
        GeneArchive old = new GeneArchive();
        old.add("lone", Gene.RUMINANT_GUT, 12);
        old.add("lone", Gene.GILLS, 40);
        old.add("lone", Gene.SPIDER_CLIMB, 30);
        old.add("lone", Gene.PHANTOM_MEMBRANE, 5);
        old.add("lone", Gene.ECHOLOCATION, 5);
        old.add("lone", Gene.VENOM_SAC, 0);
        old.add("team:old", Gene.GUARDIAN_EYE, 60);
        old.add("team:old", Gene.RABBIT_SPRING, 100);
        Directory d = Directory.backfill(old);
        h.assertValueEqual(d.book("lone").set(Stamp.SAMPLED), Set.of("minecraft:cow", "minecraft:bat"), "the lone player's backfill");
        h.assertValueEqual(d.book("team:old").set(Stamp.SAMPLED), Set.of("minecraft:rabbit"), "the team's backfill");
        for (String key : List.of("lone", "team:old")) {
            for (Stamp s : List.of(Stamp.JARRED, Stamp.RIDDEN, Stamp.HIRED)) {
                h.assertTrue(d.book(key).set(s).isEmpty(), key + "'s " + s.id() + " did not start blank");
            }
        }
        h.assertTrue(d.isDirty(), "a backfilled Directory would not be saved");
        h.succeed();
    }

    /**
     * The Directory is saved on its own ("headinajar_directory"): the gene library's tag still holds only libraries of
     * gene ids (a 0.6.1 game reads it as before), and loads back unchanged; the Directory's own tag loads back too.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void theDirectoryIsItsOwnSavedData(GameTestHelper h) throws ReflectiveOperationException {
        MinecraftServer server = h.getLevel().getServer();
        HolderLookup.Provider registries = h.getLevel().registryAccess();
        String key = "team:saved" + UUID.randomUUID().toString().substring(0, 8);
        GeneArchive archive = GeneArchive.get(server);
        archive.add(key, Gene.RABBIT_SPRING, 25);
        h.assertTrue(Directory.file(server, key, EntityType.RABBIT, Stamp.SAMPLED, "Alex"), "a new stamp was not filed");
        h.assertFalse(Directory.file(server, key, EntityType.RABBIT, Stamp.SAMPLED, "Sam"), "a stamp already there was filed again");
        Directory.file(server, key, EntityType.GOAT, Stamp.JARRED, null);
        h.assertTrue(server.overworld().getDataStorage().get(new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                () -> null, Directory::load, null), "headinajar_directory") == Directory.get(server), "the Directory is not stored under its own name");

        CompoundTag genes = archive.save(new CompoundTag(), registries);
        Set<String> ids = new java.util.HashSet<>();
        for (Gene g : Gene.values()) ids.add(g.id());
        for (String k : genes.getAllKeys()) {
            h.assertTrue(genes.get(k) instanceof CompoundTag, "the gene library holds a " + genes.get(k).getType().getName() + " at " + k);
            for (String inner : genes.getCompound(k).getAllKeys()) h.assertTrue(ids.contains(inner), "the gene library holds a non-gene " + inner + " under " + k);
        }
        var load = GeneArchive.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        load.setAccessible(true);
        GeneArchive again = (GeneArchive) load.invoke(null, genes, registries);
        h.assertTrue(Math.abs(again.readout(key, Gene.RABBIT_SPRING) - 25) < 0.01, "the gene library did not load back unchanged");
        h.assertTrue(again.keys().equals(archive.keys()), "the gene library's keys changed on a round trip");

        CompoundTag own = Directory.get(server).save(new CompoundTag(), registries);
        Directory back = Directory.load(own, registries);
        h.assertTrue(back.book(key).has(Stamp.SAMPLED, "minecraft:rabbit") && back.book(key).has(Stamp.JARRED, "minecraft:goat"), "the Directory did not load back");
        h.assertValueEqual(back.book(key).filedBy(), Map.of("minecraft:rabbit", "Alex", "minecraft:goat", ""), "who filed what");
        h.succeed();
    }

    // --- milestones ----------------------------------------------------------------------------------------------

    /**
     * Ten species with any stamp bring DOLORES's memo, 25 jarred Headhunter, all 77 on the books sampled Complete Census
     * and its memo, a [REDACTED] filed Off the Books: to every online member of the team, never to a player outside it.
     * The [REDACTED] count for nothing. A member who joins later gets what the team reached when they log in.
     */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void milestonesGoToTheWholeTeam(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = TestPlayers.at(h, "dirM", FLOOR), q = TestPlayers.at(h, "dirN", FLOOR.north()), r = TestPlayers.at(h, "dirO", FLOOR.south()),
                late = TestPlayers.at(h, "dirP", FLOOR.east());
        String name = team(h, p, q);
        try {
            String key = GeneArchive.key(p);
            List<String> books = Directory.ON_THE_BOOKS;
            h.assertValueEqual(books.size(), 77, "species on the books");
            h.assertValueEqual(Directory.VANILLA.size(), 82, "vanilla creatures with a cell");
            for (int i = 0; i < 9; i++) Directory.file(server, key, type(books.get(i)), Stamp.SAMPLED, "p");
            h.assertTrue(Memos.told(p, Memos.Memo.DIRECTORY_TEN) == 0, "nine species brought the memo");
            Directory.file(server, key, type(books.get(9)), Stamp.RIDDEN, "q");
            h.assertTrue(Memos.told(p, Memos.Memo.DIRECTORY_TEN) == 1 && Memos.told(q, Memos.Memo.DIRECTORY_TEN) == 1, "ten species did not bring the memo to both");

            for (int i = 0; i < 24; i++) Directory.file(server, key, type(books.get(i)), Stamp.JARRED, "p");
            h.assertFalse(SawTableGameTests.earned(h, p, "headhunter"), "24 jarred earned Headhunter");
            Directory.file(server, key, type(books.get(24)), Stamp.JARRED, "p");
            h.assertTrue(SawTableGameTests.earned(h, p, "headhunter") && SawTableGameTests.earned(h, q, "headhunter"), "25 jarred did not earn Headhunter for both");

            for (int i = 9; i < books.size() - 1; i++) Directory.file(server, key, type(books.get(i)), Stamp.SAMPLED, "p");
            h.assertFalse(SawTableGameTests.earned(h, p, "complete_census"), "76 sampled earned Complete Census");
            Directory.file(server, key, type(books.get(0)), Stamp.SAMPLED, "p");
            h.assertFalse(SawTableGameTests.earned(h, p, "complete_census"), "a species sampled twice counted twice");
            // jarring the last one is not sampling it
            Directory.file(server, key, type(books.get(books.size() - 1)), Stamp.JARRED, "p");
            h.assertFalse(SawTableGameTests.earned(h, p, "complete_census"), "a jarred species counted as sampled");
            Directory.file(server, key, type(books.get(books.size() - 1)), Stamp.SAMPLED, "q");
            for (ServerPlayer m : List.of(p, q)) {
                h.assertTrue(SawTableGameTests.earned(h, m, "complete_census"), "all 77 sampled did not earn Complete Census for " + m.getScoreboardName());
                h.assertTrue(Memos.told(m, Memos.Memo.CENSUS) == 1, "the census memo did not reach " + m.getScoreboardName());
            }
            h.assertFalse(SawTableGameTests.earned(h, p, "off_the_books"), "Off the Books came without a [REDACTED]");
            Directory.file(server, key, EntityType.GIANT, Stamp.SAMPLED, "p");
            h.assertTrue(SawTableGameTests.earned(h, p, "off_the_books") && SawTableGameTests.earned(h, q, "off_the_books"), "filing a giant did not earn Off the Books for both");

            // nothing reached the player outside the team
            h.assertTrue(Memos.told(r, Memos.Memo.DIRECTORY_TEN) == 0 && !SawTableGameTests.earned(h, r, "headhunter")
                    && !SawTableGameTests.earned(h, r, "complete_census") && !SawTableGameTests.earned(h, r, "off_the_books"), "a player outside the team was rewarded");
            // the [REDACTED] count for nothing: nine species and all three of them are nine
            String alone = GeneArchive.key(r);
            for (int i = 0; i < 9; i++) Directory.file(server, alone, type(books.get(i)), Stamp.SAMPLED, "r");
            for (String id : Directory.REDACTED) Directory.file(server, alone, type(id), Stamp.JARRED, "r");
            h.assertTrue(Memos.told(r, Memos.Memo.DIRECTORY_TEN) == 0, "the [REDACTED] counted towards ten");
            h.assertTrue(SawTableGameTests.earned(h, r, "off_the_books"), "a lone player's [REDACTED] did not earn Off the Books");

            // a member who joins the team later is brought up to date at login
            h.getLevel().getScoreboard().addPlayerToTeam(late.getScoreboardName(), h.getLevel().getScoreboard().getPlayerTeam(name));
            h.assertFalse(SawTableGameTests.earned(h, late, "headhunter"), "setup: the late member already has Headhunter");
            Directory.login(late);
            h.assertTrue(SawTableGameTests.earned(h, late, "headhunter") && SawTableGameTests.earned(h, late, "complete_census")
                    && SawTableGameTests.earned(h, late, "off_the_books"), "a member who joined later was not brought up to date at login");
            h.assertTrue(Memos.told(late, Memos.Memo.DIRECTORY_TEN) == 1 && Memos.told(late, Memos.Memo.CENSUS) == 1, "the late member's memos");
        } finally {
            disband(h, name);
        }
        SawTableGameTests.done(h, p, q, r, late);
    }

    // --- joining ---------------------------------------------------------------------------------------------------

    /**
     * The Directory's payload is optional: a game without it (0.6.1's channels at this version) connects either way; a
     * player whose game has not the channel is never sent it; what is sent reads back as it was written.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    @SuppressWarnings("unchecked")
    public static void theDirectoryPayloadIsOptional(GameTestHelper h) throws ReflectiveOperationException {
        var field = net.neoforged.neoforge.network.registration.NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
        field.setAccessible(true);
        var byProtocol = (Map<net.minecraft.network.ConnectionProtocol, Map<ResourceLocation, net.neoforged.neoforge.network.registration.PayloadRegistration<?>>>) field.get(null);
        List<net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent> ours = new java.util.ArrayList<>(), old = new java.util.ArrayList<>();
        boolean found = false;
        for (var reg : byProtocol.get(net.minecraft.network.ConnectionProtocol.PLAY).values()) {
            if (!reg.id().getNamespace().equals(HeadInAJar.MOD_ID)) continue;
            var c = new net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent(reg);
            ours.add(c);
            if (reg.id().equals(Directory.Sync.TYPE.id())) {
                found = true;
                h.assertTrue(c.optional(), "the Directory's payload is not optional");
            } else {
                old.add(c);
            }
        }
        h.assertTrue(found, "the Directory's payload is not registered");
        for (boolean oldServer : new boolean[]{false, true}) {
            var r = oldServer ? net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator.negotiate(old, ours)
                    : net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator.negotiate(ours, old);
            h.assertTrue(r.success(), "a game without the Directory's payload was refused as " + (oldServer ? "server" : "client") + ": " + r.failureReasons());
        }
        // a mock player's connection has no channel open: filing, syncing and logging in send it nothing (and do not throw)
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertFalse(p.connection.hasChannel(Directory.Sync.TYPE), "the mock connection has the channel");
        Pig pig = h.spawn(EntityType.PIG, FLOOR.east());
        Directory.file(p, pig.getType(), Stamp.SAMPLED);
        Directory.sync(p);
        Directory.login(p);
        h.assertTrue(book(h, p).has(Stamp.SAMPLED, "minecraft:pig"), "a player with no channel still has their book");
        // the payload's shape reads back as written
        var msg = new Directory.Sync(List.of(List.of("minecraft:cow", "minecraft:pig"), List.of("minecraft:chicken"), List.of(), List.of()),
                Map.of("minecraft:cow", "Alex", "minecraft:chicken", ""), "minecraft:pufferfish", Stamp.SAMPLED.ordinal(), "Alex");
        var buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        Directory.Sync.CODEC.encode(buf, msg);
        h.assertValueEqual(Directory.Sync.CODEC.decode(buf), msg, "the payload after a round trip");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }
}
