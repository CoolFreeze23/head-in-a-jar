package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.ChairSounds;
import com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.fx.HouseSounds;
import com.coolfreeze23.headinajar.fx.IdleBudget;
import com.coolfreeze23.headinajar.fx.JarDrip;
import com.coolfreeze23.headinajar.fx.LinkStatic;
import com.coolfreeze23.headinajar.fx.LoopMix;
import com.coolfreeze23.headinajar.machine.MachineSounds;
import com.coolfreeze23.headinajar.mecha.HopperSounds;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.ShellSounds;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The mod's sound as one family: every sound it registers has its files and a subtitle in both languages, every sound
 * key in every animation clip plays a registered event (looked up the way the game looks it up), the loop mix keeps a
 * room of machines to six loops with identical machines grouped, the link's static follows quality and swells on
 * exactly the stutter's ticks, and the inputs that make the new sounds make them through their own handlers.
 * These check names, files and timing, not how anything sounds.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class SoundGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static JsonObject resource(String path) {
        try (var in = SoundGameTests.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(path, e);
        }
    }

    private static boolean exists(String path) {
        return SoundGameTests.class.getResource(path) != null;
    }

    @GameTest(template = "platform")
    public static void everyRegisteredSoundHasItsFilesAndASubtitleInBothLanguages(GameTestHelper h) {
        JsonObject sounds = resource("/assets/headinajar/sounds.json");
        JsonObject en = resource("/assets/headinajar/lang/en_us.json"), pt = resource("/assets/headinajar/lang/pt_br.json");
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (!id.getNamespace().equals(HeadInAJar.MOD_ID)) continue;
            checked++;
            JsonObject entry = sounds.getAsJsonObject(id.getPath());
            if (entry == null) {
                wrong.add(id + ": no sounds.json entry (it would play nothing)");
                continue;
            }
            if (!entry.has("subtitle")) wrong.add(id + ": no subtitle");
            else {
                String key = entry.get("subtitle").getAsString();
                if (!en.has(key) || !pt.has(key)) wrong.add(id + ": subtitle " + key + " missing in a language");
            }
            if (entry.getAsJsonArray("sounds").isEmpty()) wrong.add(id + ": no sounds");
            for (JsonElement s : entry.getAsJsonArray("sounds")) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                boolean event = s.isJsonObject() && s.getAsJsonObject().has("type") && s.getAsJsonObject().get("type").getAsString().equals("event");
                ResourceLocation n = ResourceLocation.parse(name);
                if (event) {
                    if (!BuiltInRegistries.SOUND_EVENT.containsKey(n)) wrong.add(id + ": plays " + n + ", which is not a sound event");
                } else if (n.getNamespace().equals(HeadInAJar.MOD_ID) && !exists("/assets/headinajar/sounds/" + n.getPath() + ".ogg")) {
                    wrong.add(id + ": file " + n + " missing");
                }
            }
        }
        // and nothing in sounds.json is an event the game does not know (a name only a resource pack could reach)
        for (String key : sounds.keySet()) {
            if (!BuiltInRegistries.SOUND_EVENT.containsKey(HeadInAJar.id(key))) wrong.add(key + ": in sounds.json, not registered");
        }
        h.assertTrue(checked > 400, "only " + checked + " sounds registered");
        h.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong.subList(0, Math.min(8, wrong.size()))));
        h.succeed();
    }

    private static final Pattern INSTRUCTION = Pattern.compile("([a-z]+):([a-z_]+);");

    @GameTest(template = "platform")
    public static void everyClipsSoundKeyPlaysARegisteredSound(GameTestHelper h) throws Exception {
        Path root = ModList.get().getModFileById(HeadInAJar.MOD_ID).getFile().findResource("assets", "headinajar", "animations");
        List<String> silent = new ArrayList<>();
        int keys = 0;
        List<Path> clips;
        try (Stream<Path> walk = Files.walk(root)) {
            clips = walk.filter(p -> p.toString().endsWith(".animation.json")).toList();
        }
        h.assertTrue(clips.size() > 20, "found " + clips.size() + " clips under " + root);
        for (Path clip : clips) {
            String rel = root.relativize(clip).toString().replace('\\', '/');
            String file = clip.getFileName().toString().replace(".animation.json", "");
            JsonObject doc;
            try (var r = Files.newBufferedReader(clip, StandardCharsets.UTF_8)) {
                doc = JsonParser.parseReader(r).getAsJsonObject();
            }
            for (Map.Entry<String, JsonElement> anim : doc.getAsJsonObject("animations").entrySet()) {
                JsonObject a = anim.getValue().getAsJsonObject();
                if (a.has("sound_effects")) {
                    for (Map.Entry<String, JsonElement> k : a.getAsJsonObject("sound_effects").entrySet()) {
                        String id = k.getValue().getAsJsonObject().get("effect").getAsString().split("\\|")[0].trim();
                        keys++;
                        if (!plays(rel, file, id)) silent.add(rel + " " + anim.getKey() + " " + id);
                    }
                }
                // a block clip's instruction naming a sound (family:name;) plays family.name
                if (rel.startsWith("block/") && a.has("timeline")) {
                    for (Map.Entry<String, JsonElement> k : a.getAsJsonObject("timeline").entrySet()) {
                        Matcher m = INSTRUCTION.matcher(k.getValue().toString());
                        while (m.find()) {
                            keys++;
                            if (MachineSounds.byId(m.group(1) + "." + m.group(2)) == null) silent.add(rel + " " + anim.getKey() + " " + m.group());
                        }
                    }
                }
            }
        }
        h.assertTrue(keys > 300, "only " + keys + " sound keys read");
        h.assertTrue(silent.isEmpty(), silent.size() + " clip sounds play nothing: " + String.join("; ", silent.subList(0, Math.min(8, silent.size()))));
        h.succeed();
    }

    /** As the game's own lookups resolve a clip's sound key: the chair's, the kit machines', each frame's, the shells'. */
    private static boolean plays(String rel, String file, String id) {
        if (rel.startsWith("block/")) return file.equals("separation_chair") ? ChairSounds.byId(id) != null : MachineSounds.byId(id) != null;
        if (rel.startsWith("entity/mecha/")) {
            for (MechaFrame f : MechaFrame.values()) {
                if (f.name().toLowerCase(java.util.Locale.ROOT).equals(file)) return HopperSounds.get(f, id) != null;
            }
        }
        String name = id.startsWith("headinajar:") ? id.substring(11) : id;
        if (name.startsWith("mecha.")) name = name.substring(6);
        return switch (file) {
            case "lunk" -> HopperSounds.get("lunk." + name) != null || HopperSounds.get("bulwark." + name) != null || HopperSounds.get(name) != null;
            case "rover", "spider_walker" -> HopperSounds.get(ShellSounds.resolve(name)) != null;
            default -> HopperSounds.get(name) != null;
        };
    }

    @GameTest(template = "platform")
    public static void aRoomOfMachinesIsHeardAsAtMostSixLoops(GameTestHelper h) {
        List<LoopMix.Want> room = new ArrayList<>();
        // forty machines of eight kinds in one 10 x 10 room, all running
        for (int i = 0; i < 40; i++) room.add(new LoopMix.Want("kind" + i % 8, i % 10, 64, i / 10 * 2, 1, 1, false));
        List<LoopMix.Group> plan = LoopMix.plan(room, 5, 64, 4);
        h.assertTrue(plan.size() == 8, "eight kinds in one room make " + plan.size() + " groups, not 8");
        h.assertTrue(plan.stream().filter(LoopMix.Group::audible).count() == LoopMix.MAX_AUDIBLE, "not six heard");
        for (LoopMix.Group g : plan) {
            h.assertTrue(g.count() == 5, g.sound + " groups " + g.count() + ", not its five machines");
            h.assertTrue(g.volume() <= 1, g.sound + " louder than full");
        }
        // ten identical machines are one loop, a little louder than one, never more than full
        List<LoopMix.Want> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) ten.add(new LoopMix.Want("centrifuge", i, 64, 0, 0.5f, 1, false));
        List<LoopMix.Group> one = LoopMix.plan(ten, 0, 64, 0);
        h.assertTrue(one.size() == 1 && one.get(0).count() == 10, "ten Centrifuges are not one loop");
        h.assertTrue(Math.abs(one.get(0).volume() - 0.8f) < 1e-4, "ten at 0.5 should be 0.5 x 1.6, is " + one.get(0).volume());
        h.assertTrue(Math.abs(one.get(0).x() - 4.5) < 1e-6, "the group is not at the machines' middle");
        // a machine 40 blocks away is its own loop; one at another speed band is its own loop
        List<LoopMix.Want> apart = List.of(new LoopMix.Want("mixer", 0, 64, 0, 1, 1, false), new LoopMix.Want("mixer", 40, 64, 0, 1, 1, false),
                new LoopMix.Want("mixer", 1, 64, 0, 1, 1.9f, false));
        h.assertTrue(LoopMix.plan(apart, 0, 64, 0).size() == 3, "far or faster machines were merged");
        // the nearest and loudest are the ones heard
        List<LoopMix.Want> near = new ArrayList<>();
        for (int i = 0; i < 9; i++) near.add(new LoopMix.Want("k" + i, i * 20, 64, 0, 1, 1, false));
        List<LoopMix.Group> heard = LoopMix.plan(near, 0, 64, 0).stream().filter(LoopMix.Group::audible).toList();
        h.assertTrue(heard.stream().allMatch(g -> g.x() <= 100), "a far loop was heard before a near one");
        // the company's stingers duck the machines near them; a machine's own sound ducks nothing
        h.assertTrue(LoopMix.duckTicks(HouseSounds.JINGLE.getId().toString()) > 0 && LoopMix.duckTicks("headinajar:mecha_boot") > 0
                && LoopMix.duckTicks("headinajar:centrifuge.whine") == 0, "the stinger duck");
        // two loops of one sound never share a pitch offset
        h.assertTrue(LoopMix.offset(1) != LoopMix.offset(2) && LoopMix.offset(1) >= 0.97f && LoopMix.offset(2) <= 1.03f, "offsets");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void theLinksStaticFollowsQualityAndSwellsOnTheStutter(GameTestHelper h) {
        h.assertTrue(LinkStatic.band(100) == LinkStatic.Band.CLEAN && LinkStatic.band(70) == LinkStatic.Band.CLEAN, "a clean link hisses");
        h.assertTrue(LinkStatic.band(69) == LinkStatic.Band.FAINT && LinkStatic.band(40) == LinkStatic.Band.FAINT, "40-69 is not faint");
        h.assertTrue(LinkStatic.band(39) == LinkStatic.Band.STEADY && LinkStatic.band(20) == LinkStatic.Band.STEADY, "20-39 is not steady");
        h.assertTrue(LinkStatic.band(19) == LinkStatic.Band.HEAVY && LinkStatic.band(0) == LinkStatic.Band.HEAVY, "under 20 is not heavy");
        for (int q = 0; q <= 100; q++) {
            for (long t = 0; t < 120; t++) {
                float v = LinkStatic.volume(q, t);
                boolean stutter = LinkStatic.stutter(q, t);
                if (q >= 70) h.assertTrue(v == 0, "static on a clean link at " + q);
                else h.assertTrue(stutter ? v == 1 : v == LinkStatic.BAND_LEVEL, "q " + q + " t " + t + ": volume " + v + " against stutter " + stutter);
            }
        }
        // the stutter is the rule the movement handler always had: 4 ticks a second under 20, 2 in 40 under 40
        h.assertTrue(LinkStatic.stutter(10, 3) && !LinkStatic.stutter(10, 4) && LinkStatic.stutter(30, 41) && !LinkStatic.stutter(30, 3)
                && !LinkStatic.stutter(50, 0), "the stutter's ticks moved");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void idleLifeIsHeardThreeTimesASecondAtMostAndACrackedJarDrips(GameTestHelper h) {
        IdleBudget.reset();
        int allowed = 0;
        for (int i = 0; i < 40; i++) if (IdleBudget.take(1000)) allowed++;
        h.assertTrue(allowed == IdleBudget.PER_SECOND, allowed + " idle sounds in one tick");
        h.assertTrue(IdleBudget.take(1020), "the next second is not allowed its own");
        IdleBudget.reset();
        h.assertTrue(JarDrip.heard(3, true, true) && !JarDrip.heard(2, true, true) && !JarDrip.heard(3, false, true)
                && !JarDrip.heard(3, true, false), "the drip rule: a quarter of glass, saline left, standing");
        h.succeed();
    }

    // --- through the handlers a key or a click takes ----------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        return p;
    }

    private static List<ResourceLocation> listen(List<Object> holder) {
        List<ResourceLocation> heard = new ArrayList<>();
        java.util.function.Consumer<PlayLevelSoundEvent.AtPosition> l = e -> {
            if (e.getSound() != null) heard.add(e.getSound().value().getLocation());
        };
        java.util.function.Consumer<PlayLevelSoundEvent.AtEntity> le = e -> {
            if (e.getSound() != null) heard.add(e.getSound().value().getLocation());
        };
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, l);
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtEntity.class, le);
        holder.add(l);
        holder.add(le);
        return heard;
    }

    private static void finish(GameTestHelper h, List<Object> holder, ServerPlayer... players) {
        for (Object l : holder) NeoForge.EVENT_BUS.unregister(l);
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theDriveKeyFoldsTheWheelsAndTipsABareJarWithTheirOwnSounds(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer q = player(h, FLOOR.east(3));
        FormActions.detach(p, 3);
        FormActions.detach(q, 3);
        FormActions.state(p).setModule(0, new ItemStack(ModRegistry.module(ModuleType.WHEELS)));
        FormActions.state(p).setCharge(100_000);
        List<Object> holder = new ArrayList<>();
        List<ResourceLocation> heard = listen(holder);
        Network.action(p, new Network.JarAction(Network.Action.DRIVE));
        Network.action(p, new Network.JarAction(Network.Action.DRIVE));
        Network.action(q, new Network.JarAction(Network.Action.DRIVE));
        Network.action(q, new Network.JarAction(Network.Action.DRIVE));
        List<ResourceLocation> want = List.of(HouseSounds.WHEELS_DOWN.getId(), HouseSounds.WHEELS_UP.getId(), HouseSounds.TIP_OVER.getId(), HouseSounds.SIT_UP.getId());
        h.assertTrue(heard.containsAll(want), "heard " + heard);
        h.assertTrue(heard.stream().noneMatch(r -> r.getNamespace().equals("minecraft")), "a vanilla stand-in still plays: " + heard);
        finish(h, holder, p, q);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theSocketConnectsClicksItsChannelsAndDisconnectsLikeASwitchboard(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        FormActions.detach(p, 3);
        List<Object> holder = new ArrayList<>();
        List<ResourceLocation> heard = listen(holder);
        h.assertTrue(socket.plugIn(p) && FormActions.state(p).form() == Form.SOCKET, "could not plug in");
        // the socket's own key handler (the SocketInput payload): a face set, the same level again, cleared, a pulse
        NeuralSocketBlockEntity.input(p, 0, Direction.UP.get3DDataValue(), 8);
        NeuralSocketBlockEntity.input(p, 0, Direction.UP.get3DDataValue(), 12);
        NeuralSocketBlockEntity.input(p, 0, Direction.UP.get3DDataValue(), 0);
        NeuralSocketBlockEntity.input(p, 1, Direction.NORTH.get3DDataValue(), 0);
        NeuralSocketBlockEntity.input(p, 2, 0, 0);
        long on = heard.stream().filter(HouseSounds.CHANNEL_ON.getId()::equals).count();
        long off = heard.stream().filter(HouseSounds.CHANNEL_OFF.getId()::equals).count();
        h.assertTrue(heard.contains(HouseSounds.RELAY_UP.getId()) && heard.contains(HouseSounds.RELAY_DOWN.getId()), "no cascade: " + heard);
        h.assertTrue(on == 2 && off == 1, "channel clicks on " + on + " off " + off + " (want 2 and 1: a level change on a lit face is silent)");
        h.assertTrue(heard.stream().noneMatch(r -> r.getPath().startsWith("block.beacon")), "the beacon still plays: " + heard);
        finish(h, holder, p);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theShockLidZapsWithItsOwnSound(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        FormActions.state(p).setModule(0, new ItemStack(ModRegistry.module(ModuleType.SHOCK_LID)));
        FormActions.state(p).setCharge(100_000);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, FLOOR.east());
        zombie.setNoAi(true);
        List<Object> holder = new ArrayList<>();
        List<ResourceLocation> heard = listen(holder);
        // the zombie's own melee hit, as the game makes it
        zombie.doHurtTarget(p);
        h.assertTrue(heard.contains(HouseSounds.SHOCK_LID.getId()), "heard " + heard);
        h.assertTrue(!heard.contains(BuiltInRegistries.SOUND_EVENT.getKey(net.minecraft.sounds.SoundEvents.BEE_STING)), "the bee sting still plays");
        finish(h, holder, p);
    }
}
