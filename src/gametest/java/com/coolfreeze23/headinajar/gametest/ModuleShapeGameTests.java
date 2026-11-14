package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.ModuleArt;
import com.coolfreeze23.headinajar.mecha.ModuleMotion;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every module drawn from its own model on the animated frames: the size each frame takes, every file and bone the
 * module layer looks up by name, the shoulder sockets, and each module's clip chosen from the arm buttons, the utility
 * key, a blow and a parked machine, pressed through their real handlers (the ArmInput payload, the jar's key payload,
 * {@code Player.attack}).
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ModuleShapeGameTests {
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

    /** A machine with these modules in their slots (in order within a slot kind), owned by {@code owner}, battery full. */
    private static MechaEntity machine(GameTestHelper h, EntityType<MechaEntity> type, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(type, at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        Set<Integer> used = new HashSet<>();
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
        return m;
    }

    private static void board(GameTestHelper h, ServerPlayer p, MechaEntity m) {
        if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "the click did not board: " + FormActions.state(p).form());
        m.setOnGround(true);
    }

    private static double now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    private static ModuleMotion.Pose pose(MechaEntity m, MechaModule module, GameTestHelper h) {
        return ModuleMotion.pose(m, m.indexOf(module), module, now(h) + 0.5, false);
    }

    private static String clip(ModuleMotion.Pose p) {
        return p.clip() == null ? "built" : p.clip();
    }

    private static JsonObject json(String path) {
        InputStream in = ModuleShapeGameTests.class.getResourceAsStream(path);
        if (in == null) throw new AssertionError("missing " + path);
        return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static boolean exists(String path) {
        return ModuleShapeGameTests.class.getResource(path) != null;
    }

    /** Bone name -> its cube count, of a module model. */
    private static Map<String, Integer> bones(String file) {
        Map<String, Integer> out = new HashMap<>();
        JsonObject geo = json("/assets/headinajar/geo/entity/mecha/module/" + file + ".geo.json");
        for (var b : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            JsonObject bone = b.getAsJsonObject();
            out.put(bone.get("name").getAsString(), bone.has("cubes") ? bone.getAsJsonArray("cubes").size() : 0);
        }
        return out;
    }

    private static Set<String> frameBones(String frame) {
        Set<String> out = new HashSet<>();
        JsonObject geo = json("/assets/headinajar/geo/entity/mecha/" + frame + ".geo.json");
        for (var b : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) out.add(b.getAsJsonObject().get("name").getAsString());
        return out;
    }

    /**
     * All 58 modules: each has a model, sheet and the clips the layer asks for; each frame takes the size the table
     * says (the Bulwark's arc cannon heavy, its chainsaw light, the Hopper everything light); every lit or tinted cube
     * the table names is in the model; and on the Hopper and the Bulwark every socket a module would be drawn at is in
     * the frame's model and the module has the mount bone that socket needs. No module falls back to a simple shape.
     */
    @GameTest(template = "platform")
    public static void everyModuleHasItsModelAtItsSocketInItsSize(GameTestHelper h) {
        for (MechaModule mod : MechaModule.values()) {
            h.assertTrue(ModuleArt.has(mod), mod + " has no model in the table (it would fall back to its simple shape)");
            h.assertTrue(ModuleArt.slot(mod) == mod.slot, mod + " is in the table under another slot");
            for (String clip : ModuleMotion.ASKED.getOrDefault(mod, new String[0])) {
                h.assertTrue(ModuleArt.clips(mod).containsKey(clip), mod + " is asked for a clip it lacks: " + clip);
            }
            for (String frame : List.of("hopper", "bulwark", "colossus", "executive", "chimera", "kite", "skitter", "wheelhouse")) {
                ModuleArt.Art art = ModuleArt.art(frame, mod);
                String file = art.file();
                h.assertTrue(exists("/assets/headinajar/geo/entity/mecha/module/" + file + ".geo.json"), "no model " + file);
                h.assertTrue(exists("/assets/headinajar/textures/entity/mecha/module/" + file + ".png"), "no sheet " + file);
                if (!ModuleArt.clips(mod).isEmpty()) {
                    h.assertTrue(exists("/assets/headinajar/animations/entity/mecha/module/" + file + ".animation.json"), "no clips " + file);
                }
                Map<String, Integer> bones = bones(file);
                for (var list : List.of(art.glow(), art.firing(), art.charge(), art.tint())) {
                    for (ModuleArt.Cube c : list) {
                        h.assertTrue(bones.getOrDefault(c.bone(), 0) > c.index(), file + " names a cube it lacks: " + c);
                    }
                }
                if (!art.glow().isEmpty()) h.assertTrue(exists("/assets/headinajar/textures/entity/mecha/module/" + file + "_glowmask.png"), "no glow mask " + file);
                if (!art.firing().isEmpty()) h.assertTrue(exists("/assets/headinajar/textures/entity/mecha/module/" + file + "_firing.png"), "no firing mask " + file);
            }
        }
        h.assertTrue(ModuleArt.heavy("bulwark", MechaModule.ARC_CANNON), "the Bulwark's arc cannon is not the heavy one");
        h.assertFalse(ModuleArt.heavy("bulwark", MechaModule.CHAINSAW), "the Bulwark's chainsaw is not the light one");
        h.assertTrue(ModuleArt.heavy("colossus", MechaModule.CHAINSAW), "the Colossus's chainsaw is not the heavy one");
        h.assertFalse(ModuleArt.heavy("colossus", MechaModule.SOLAR_SKIN), "solar skin has one size");
        for (MechaModule mod : MechaModule.values()) h.assertFalse(ModuleArt.heavy("hopper", mod), "the Hopper takes a heavy " + mod);
        h.assertFalse(ModuleArt.heavy("rover", MechaModule.CHAINSAW), "a frame the table does not name must take the light size");
        // the table the generator read is the art's own, entry for entry
        JsonObject table = json("/assets/headinajar/mecha/module_art.json").getAsJsonObject("heavy");
        for (var e : table.entrySet()) {
            JsonArray list = e.getValue().getAsJsonArray();
            int heavy = 0;
            for (MechaModule mod : MechaModule.values()) if (ModuleArt.heavy(e.getKey(), mod)) heavy++;
            h.assertValueEqual(heavy, list.size(), e.getKey() + "'s heavy modules");
        }
        for (MechaFrame frame : List.of(MechaFrame.HOPPER, MechaFrame.BULWARK)) {
            Set<String> sockets = frameBones(frame.id());
            for (int i = 0; i < frame.totalSlots(); i++) {
                MechaModule.Slot slot = frame.slotAt(i);
                for (String s : HopperSockets.sockets(frame, i)) {
                    h.assertTrue(sockets.contains(s), frame + " slot " + i + ": no socket " + s);
                    for (MechaModule mod : MechaModule.values()) {
                        if (mod.slot != slot) continue;
                        h.assertTrue(bones(ModuleArt.art(frame.id(), mod).file()).containsKey(HopperSockets.mountOf(s)),
                                mod + " has no " + HopperSockets.mountOf(s) + " for " + frame + "'s " + s);
                    }
                }
            }
        }
        h.succeed();
    }

    /** Arm slots three and four are the shoulder hardpoints, not the forearms again. */
    @GameTest(template = "platform")
    public static void shoulderArmsHaveTheirOwnSockets(GameTestHelper h) {
        for (MechaFrame frame : MechaFrame.values()) {
            int first = frame.firstIndex(MechaModule.Slot.ARM);
            for (int n = 0; n < frame.slots(MechaModule.Slot.ARM); n++) {
                String expected = n == 0 ? "socket_arm_l" : n == 1 ? "socket_arm_r" : "socket_arm_" + n;
                h.assertValueEqual(HopperSockets.sockets(frame, first + n), List.of(expected), frame + " arm " + n);
                h.assertValueEqual(HopperSockets.slotOf(frame, expected), first + n, frame + " " + expected + " maps back");
            }
        }
        h.assertValueEqual(HopperSockets.sockets(MechaFrame.COLOSSUS, MechaFrame.COLOSSUS.firstIndex(MechaModule.Slot.ARM) + 3),
                List.of("socket_arm_3"), "the Colossus's fourth arm");
        h.succeed();
    }

    /**
     * The arm buttons through the ArmInput payload's handler: the flamethrower burns (its sustain, lit) while held and
     * shows its idle (flame out) when let go; the fist's press plays its strike; parked, everything is at rest.
     */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void armButtonsPlayTheToolsClips(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = machine(h, ModRegistry.HOPPER.get(), FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS,
                MechaModule.FLAMETHROWER, MechaModule.HYDRAULIC_FIST, MechaModule.ENERGY_SHIELD);
        // parked and nobody aboard: every module at rest, the flame out
        h.assertValueEqual(clip(pose(m, MechaModule.FLAMETHROWER, h)), "idle", "an unpiloted flamethrower");
        h.assertValueEqual(clip(ModuleMotion.pose(m, m.indexOf(MechaModule.ENERGY_SHIELD), MechaModule.ENERGY_SHIELD, now(h), true)), "built",
                "a parked machine's shield projector");
        board(h, p, m);
        h.assertValueEqual(clip(pose(m, MechaModule.FLAMETHROWER, h)), "idle", "the flamethrower before a press");
        h.assertValueEqual(clip(pose(m, MechaModule.ENERGY_SHIELD, h)), "sustain", "a powered shield projector");
        h.assertTrue(pose(m, MechaModule.ENERGY_SHIELD, h).lit(), "the shield's lens is not lit");
        // both buttons down (bit 0 the left arm, bit 1 the right)
        Network.arms(p, 3);
        h.runAfterDelay(2, () -> {
            ModuleMotion.Pose flame = pose(m, MechaModule.FLAMETHROWER, h);
            h.assertValueEqual(clip(flame), "sustain", "the held flamethrower");
            h.assertTrue(flame.lit(), "the flame is not lit while firing");
            h.assertValueEqual(clip(pose(m, MechaModule.HYDRAULIC_FIST, h)), "strike", "the pressed fist");
            Network.arms(p, 0);
        });
        h.runAfterDelay(30, () -> {
            ModuleMotion.Pose flame = pose(m, MechaModule.FLAMETHROWER, h);
            h.assertValueEqual(clip(flame), "idle", "the flamethrower let go");
            h.assertFalse(flame.lit(), "the flame is still lit when let go");
            h.assertValueEqual(clip(pose(m, MechaModule.HYDRAULIC_FIST, h)), "built", "the fist after its strike");
            done(h, p);
        });
    }

    /**
     * The utility key through the jar's key payload: the missile pod's hatch opens and the salvo kicks; the secondary
     * key picks the smoke mortar and the key fires it (its recoil). A blow by a player's attack shakes the armour.
     */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void utilityKeyAndBlowsPlayTheirModulesClips(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer hitter = player(h, FLOOR.east(1));
        MechaEntity m = machine(h, ModRegistry.BULWARK.get(), FLOOR.east(4), p.getUUID(), MechaModule.HYDRAULIC_LEGS,
                MechaModule.MISSILE_POD, MechaModule.SMOKE_MORTAR, MechaModule.ARMOR_PLATING, MechaModule.REACTIVE_ARMOUR);
        board(h, p, m);
        h.assertValueEqual(clip(pose(m, MechaModule.MISSILE_POD, h)), "idle", "the missile pod before it fires (hatch shut)");
        Network.action(p, new Network.JarAction(Network.Action.ABILITY));
        h.assertTrue(m.actAt() >= 0 && (m.actAt() & 63) == MechaModule.MISSILE_POD.ordinal(), "the salvo was not recorded: " + m.actAt());
        h.runAfterDelay(1, () -> h.assertValueEqual(clip(pose(m, MechaModule.MISSILE_POD, h)), "deploy", "the hatch opening"));
        h.runAfterDelay(8, () -> h.assertValueEqual(clip(pose(m, MechaModule.MISSILE_POD, h)), "recoil", "the salvo's kick"));
        h.runAfterDelay(10, () -> {
            Network.action(p, new Network.JarAction(Network.Action.SECONDARY));
            Network.action(p, new Network.JarAction(Network.Action.ABILITY));
            h.assertTrue((m.actAt() & 63) == MechaModule.SMOKE_MORTAR.ordinal(), "the mortar round was not recorded");
        });
        h.runAfterDelay(12, () -> h.assertValueEqual(clip(pose(m, MechaModule.SMOKE_MORTAR, h)), "recoil", "the mortar's recoil"));
        h.runAfterDelay(14, () -> {
            hitter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            Vec3 at = m.position().add(0, 0, 2);
            hitter.moveTo(at.x, at.y, at.z, 180, 0);
            hitter.attack(m);
            h.assertTrue(m.hitAt() >= 0, "the blow did not land");
        });
        h.runAfterDelay(16, () -> {
            h.assertValueEqual(clip(pose(m, MechaModule.ARMOR_PLATING, h)), "hit", "the plates thrown apart");
            h.assertValueEqual(clip(pose(m, MechaModule.REACTIVE_ARMOUR, h)), "hit", "a tile blown off");
        });
        h.runAfterDelay(60, () -> {
            h.assertValueEqual(clip(pose(m, MechaModule.ARMOR_PLATING, h)), "built", "the plates back at rest");
            done(h, p, hitter);
        });
    }

    /** The module's last action is synced, never saved: a machine saves exactly the keys it did before. */
    @GameTest(template = "platform")
    public static void aModuleActionIsNeverSaved(GameTestHelper h) {
        MechaEntity m = machine(h, ModRegistry.HOPPER.get(), FLOOR, UUID.randomUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.CARGO_HOLD);
        Set<String> before = new HashSet<>(m.saveWithoutId(new CompoundTag()).getAllKeys());
        m.act(MechaModule.CARGO_HOLD);
        h.assertTrue(m.actAt() >= 0, "the action was not recorded");
        Set<String> after = new HashSet<>(m.saveWithoutId(new CompoundTag()).getAllKeys());
        h.assertValueEqual(after, before, "saved keys");
        h.succeed();
    }
}
