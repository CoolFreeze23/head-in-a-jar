package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.compat.Compat;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.genetics.GeneEvents;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** Phase 7, integrations and polish: language parity, advancements, sounds, optional mods absent, the modded genes. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class PolishGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    private static final List<String> ADVANCEMENTS = List.of("root", "signed", "separated", "link_lost", "botched", "headless_horseman", "periscope", "hosted",
            "synced", "mula", "out_for_blood", "spliced", "mutated", "synergy", "chimera", "legendary", "assembled", "parachute", "frequent_flyer", "heavy_head",
            "chairman", "staffed", "board_meeting", "brain_trust", "socket_pulse");

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

    private static void give(LivingEntity e, Gene gene) {
        var set = e.getData(ModRegistry.GENES);
        set.serums.add(new Serum(List.of(new Serum.Entry(gene, 1f)), 100));
        set.changed();
    }

    private static JsonObject lang(String code) {
        var in = PolishGameTests.class.getResourceAsStream("/assets/headinajar/lang/" + code + ".json");
        if (in == null) throw new IllegalStateException("no " + code);
        return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @GameTest(template = "platform")
    public static void everyLangKeyInBothLanguages(GameTestHelper h) {
        JsonObject en = lang("en_us"), pt = lang("pt_br");
        Set<String> a = en.keySet(), b = pt.keySet();
        h.assertTrue(a.equals(b), "key sets differ by " + (a.size() - b.size()));
        for (String key : a) {
            h.assertFalse(en.get(key).getAsString().isBlank() || pt.get(key).getAsString().isBlank(), "blank text for " + key);
        }
        for (Gene g : Gene.values()) h.assertTrue(a.contains("gene.headinajar." + g.id()), "no name for gene " + g.id());
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void advancementsLoadAndFire(GameTestHelper h) {
        var server = h.getLevel().getServer();
        for (String key : ADVANCEMENTS) h.assertTrue(server.getAdvancements().get(HeadInAJar.id(key)) != null, "advancement " + key + " missing");
        ServerPlayer p = player(h, FLOOR);
        var holder = server.getAdvancements().get(HeadInAJar.id("socket_pulse"));
        h.assertFalse(p.getAdvancements().getOrStartProgress(holder).isDone(), "already done");
        Advance.grant(p, "socket_pulse");
        h.assertTrue(p.getAdvancements().getOrStartProgress(holder).isDone(), "the event did not grant it");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void soundsRegistered(GameTestHelper h) {
        for (String s : List.of("booth_cut", "booth_oopsie", "link_lost", "link_restored", "mecha_boot", "gene_splice", "gene_mutation", "array_murmur", "staff_complain")) {
            h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(HeadInAJar.id(s)), "sound " + s + " missing");
        }
        h.succeed();
    }

    /** With the other mods absent, their host files are skipped or inert, and the vanilla ones still load. */
    @GameTest(template = "platform")
    public static void dataLoadsWithModsAbsent(GameTestHelper h) {
        h.assertTrue(Hosts.definitions().containsKey(ResourceLocation.withDefaultNamespace("zombie")), "vanilla hosts did not load");
        for (ResourceLocation id : Hosts.definitions().keySet()) {
            boolean registered = BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            h.assertTrue(registered || !id.getNamespace().equals("minecraft"), "a vanilla host names an unknown creature: " + id);
        }
        h.assertFalse(Compat.curios(), "Curios is not in the test server");
        ServerPlayer p = player(h, FLOOR);
        h.assertFalse(FormActions.hasSatchel(p), "a satchel appeared from nowhere");
        p.getInventory().add(new net.minecraft.world.item.ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        h.assertTrue(FormActions.hasSatchel(p), "a carried satchel stopped counting");
        h.assertTrue(Gene.of("aether:moa").contains(Gene.MOA_WINGS) && Gene.of("orespawn:godzilla").contains(Gene.ATOMIC_BREATH), "modded genes lost their sources");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void regrowthSavesOncePerTenMinutes(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        give(p, Gene.REGROWTH);
        p.hurt(h.getLevel().damageSources().generic(), 100);
        h.assertTrue(p.isAlive() && Math.abs(p.getHealth() - p.getMaxHealth() * 0.3f) < 0.01f, "Regrowth did not save: health " + p.getHealth());
        h.assertTrue(p.getPersistentData().getLong(GeneEvents.REGROWTH_READY) > h.getLevel().getGameTime(), "no cooldown");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void muleFireAndHides(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        give(p, Gene.MULE_FIRE);
        give(p, Gene.PACHYDERM_HIDE);
        GeneEffects.tick(p);
        float before = p.getHealth();
        p.hurt(h.getLevel().damageSources().inFire(), 4);
        h.assertTrue(p.getHealth() == before, "Mule Fire did not stop fire damage");
        h.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 2, "Pachyderm Hide gave no armour");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void blazingSoulLandsInFlames(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        give(p, Gene.BLAZING_SOUL);
        var zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(2));
        float before = zombie.getHealth();
        GeneEvents.burst(p);
        h.assertTrue(zombie.getHealth() < before && zombie.isOnFire(), "the landing burst missed");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void atomicBreathFiresAndCools(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        give(p, Gene.ATOMIC_BREATH);
        h.assertTrue(GeneEffects.actives(p).contains(Gene.ATOMIC_BREATH), "not an active gene");
        int index = GeneEffects.actives(p).indexOf(Gene.ATOMIC_BREATH);
        h.assertTrue(GeneEffects.activate(p, p, index), "did not fire");
        h.assertFalse(GeneEffects.activate(p, p, index), "ignored its cooldown");
        done(h, p);
    }
}
