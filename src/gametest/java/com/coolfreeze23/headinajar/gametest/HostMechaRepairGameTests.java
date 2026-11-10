package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.block.BodyLockerBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.host.HostDefinition;
import com.coolfreeze23.headinajar.host.HostEvents;
import com.coolfreeze23.headinajar.host.HostFiles;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.host.WebShots;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hosts, shells and mecha, repaired. Each test takes the path a real click, key or break takes: the interaction the client
 * sends ({@code interactOn}, {@code attack}, {@code useItemOn}), a payload's handler, a menu button, the game's own block
 * break, a save and load.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class HostMechaRepairGameTests {
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

    private static JarState state(ServerPlayer p) {
        return FormActions.state(p);
    }

    private static long count(ServerPlayer p, String reason) {
        return Refusals.delivered(p).stream().filter(reason::equals).count();
    }

    private static void key(ServerPlayer p, Network.Action action) {
        Network.action(p, new Network.JarAction(action));
    }

    /** A sawn host, made the way the saw makes one. */
    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "saw failed on " + type);
        return mob;
    }

    private static ItemStack module(MechaModule m) {
        return new ItemStack(ModRegistry.mechaModule(m));
    }

    /** A mecha of this frame owned by {@code owner}, with these modules in their slots (legs, then the rest, in order). */
    private static MechaEntity mecha(GameTestHelper h, MechaFrame frame, BlockPos at, UUID owner, int battery, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(frame), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < frame.totalSlots(); i++) slots.add(ItemStack.EMPTY);
        Set<Integer> used = new HashSet<>();
        for (MechaModule mod : mods) {
            int first = frame.firstIndex(mod.slot);
            int i = first;
            while (used.contains(i)) i++;
            used.add(i);
            slots.set(i, module(mod));
        }
        m.setModules(slots);
        m.setOwner(owner);
        m.setBattery(battery);
        return m;
    }

    private static MechaEntity piloted(GameTestHelper h, ServerPlayer pilot, int battery, MechaModule... mods) {
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), pilot.getUUID(), battery, mods);
        FormActions.detach(pilot, 3);
        h.assertTrue(FormActions.mountHost(pilot, m), "could not board");
        return m;
    }

    /** Turns the player to look at the middle of this entity. */
    private static void lookAt(ServerPlayer p, Entity target) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(p.getEyePosition());
        p.setYRot((float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90);
        p.setXRot((float) -(Math.atan2(to.y, to.horizontalDistance()) * 180 / Math.PI));
    }

    // --- instruments before small talk -----------------------------------------------------------------

    @GameTest(template = "platform")
    public static void serumsAndSyringesReachHostsByHand(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = host(h, EntityType.PIG, FLOOR.east(2), p);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SYRINGE.get()));
        h.assertTrue(p.interactOn(pig, InteractionHand.MAIN_HAND).consumesAction(), "the syringe click was swallowed");
        h.assertTrue(p.getInventory().hasAnyMatching(s -> s.is(ModRegistry.BLOOD_SAMPLE.get())), "no blood drawn from a host");

        ItemStack serum = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
        new Serum(List.of(new Serum.Entry(Gene.RABBIT_SPRING, 1f)), 100).writeTo(serum);
        p.setItemInHand(InteractionHand.MAIN_HAND, serum);
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertTrue(!Genetics.genes(pig).strengths().isEmpty(), "the serum never reached the host");

        // a Chimera's Gene Ports take a serum by hand too
        MechaEntity chimera = mecha(h, MechaFrame.CHIMERA, FLOOR.west(3), p.getUUID(), 1000, MechaModule.HYDRAULIC_LEGS);
        ItemStack second = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
        new Serum(List.of(new Serum.Entry(Gene.RABBIT_SPRING, 1f)), 100).writeTo(second);
        p.setItemInHand(InteractionHand.MAIN_HAND, second);
        p.interactOn(chimera, InteractionHand.MAIN_HAND);
        h.assertTrue(!Genetics.genes(chimera).strengths().isEmpty(), "the Chimera's Gene Ports were not filled");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void emptyHandsStillClimbOnAndAsk(GameTestHelper h) {
        ServerPlayer body = player(h, FLOOR);
        ServerPlayer jar = player(h, FLOOR.west(2));
        Pig pig = host(h, EntityType.PIG, FLOOR.east(2), body);
        h.assertTrue(body.interactOn(pig, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "status click result changed");
        h.assertTrue(Refusals.delivered(body).isEmpty() && body.getVehicle() == null, "an empty hand did something new");
        FormActions.detach(jar, 3);
        jar.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertTrue(state(jar).form() == Form.HOSTED && jar.getVehicle() == pig, "an empty-handed jar did not climb on");
        // a jar holding something it can't use climbs on all the same
        Pig other = host(h, EntityType.PIG, FLOOR.south(2), body);
        ServerPlayer jar2 = player(h, FLOOR.south(3));
        FormActions.detach(jar2, 3);
        jar2.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT));
        jar2.interactOn(other, InteractionHand.MAIN_HAND);
        h.assertTrue(state(jar2).form() == Form.HOSTED, "a carrot in a jar's hotbar stopped it climbing on");
        done(h, body, jar, jar2);
    }

    @GameTest(template = "platform")
    public static void vanillaItemsOnAHostWorkOrSayWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = host(h, EntityType.PIG, FLOOR.east(2), p);
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Gerald"));
        p.setItemInHand(InteractionHand.MAIN_HAND, tag);
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertTrue(pig.hasCustomName(), "a name tag did nothing to a host");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "host.saddle", "saddle line");
        h.assertFalse(pig.isSaddled(), "a host was saddled");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT));
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "host.no_mouth", "food line");
        h.assertFalse(pig.isInLove(), "a host was fed");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertTrue(pig.isLeashed() && pig.getLeashHolder() == p, "a lead did nothing to a host");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertFalse(pig.isLeashed(), "the lead's holder could not take it off");
        // a mecha won't go on a lead
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.west(3), p.getUUID(), 1000, MechaModule.HYDRAULIC_LEGS);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(!m.isLeashed() && "host.lead_machine".equals(Refusals.lastDelivered(p)), "a mecha was leashed");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void embalmingAMobWithItsHeadOnSaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(2));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.EMBALMING_FLUID.get()));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "host.not_a_host", "embalming a whole cow");
        ShellEntity lunk = h.spawn(ModRegistry.LUNK.get(), FLOOR.west(2));
        p.interactOn(lunk, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "host.embalm_shell", "embalming a shell");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void sawKeepsTheHeadWithAnyJarOnlyOnceDead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(2));
        zombie.setHealth(1);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.COMPACT_JAR.get()));
        p.interactOn(zombie, InteractionHand.MAIN_HAND);
        h.assertTrue(zombie.isDeadOrDying(), "the cut didn't kill");
        h.assertTrue(p.getInventory().hasAnyMatching(s -> s.is(ModRegistry.JARRED_HEAD.get())) && p.getOffhandItem().isEmpty(), "a Compact Jar kept no head");

        // a totem saves it: no head, the jar stays in hand
        Zombie lucky = h.spawn(EntityType.ZOMBIE, FLOOR.west(2));
        lucky.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        lucky.setHealth(1);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.REINFORCED_JAR.get()));
        p.interactOn(lucky, InteractionHand.MAIN_HAND);
        h.assertTrue(lucky.isAlive(), "the totem did not save it");
        h.assertFalse(p.getInventory().hasAnyMatching(s -> s.is(ModRegistry.JARRED_HEAD.get())), "a head came off a creature that lived");
        h.assertTrue(p.getOffhandItem().is(ModRegistry.REINFORCED_JAR.get()), "the jar was spent on a creature that lived");
        h.assertValueEqual(Refusals.lastDelivered(p), "saw.survived", "survivor line");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aClickOnTheDragonsWingIsTheDragon(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var dragon = h.spawn(EntityType.ENDER_DRAGON, FLOOR.above(4));
        dragon.setNoAi(true);
        Entity wing = dragon.getSubEntities()[0];
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SYRINGE.get()));
        h.assertTrue(p.interactOn(wing, InteractionHand.MAIN_HAND) != InteractionResult.PASS, "a syringe on the dragon's part did nothing");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        h.assertTrue(p.interactOn(wing, InteractionHand.MAIN_HAND) != InteractionResult.PASS, "the saw on the dragon's part did nothing");
        dragon.discard();
        done(h, p);
    }

    // --- nothing lost -----------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void breakingAFullLockerLetsItsBodyOut(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.north(2);
        h.setBlock(at, ModRegistry.BODY_LOCKER.get());
        Pig pig = host(h, EntityType.PIG, FLOOR.north(1), p);
        BlockPos abs = h.absolutePos(at);
        p.gameMode.useItemOn(p, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        var locker = (BodyLockerBlockEntity) h.getBlockEntity(at);
        h.assertTrue(locker.full() && pig.isRemoved(), "the locker took nothing");
        p.gameMode.destroyBlock(abs);
        h.assertTrue(h.getLevel().getBlockState(abs).isAir(), "the locker did not break");
        h.assertTrue(h.getEntities(EntityType.PIG).stream().anyMatch(e -> !e.isRemoved() && Hosts.isHost(e)), "the stored host went with the locker");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void lockerRefusesSomeoneElsesMecha(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.north(2);
        h.setBlock(at, ModRegistry.BODY_LOCKER.get());
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.north(1), UUID.randomUUID(), 1000, MechaModule.HYDRAULIC_LEGS);
        BlockPos abs = h.absolutePos(at);
        p.gameMode.useItemOn(p, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        h.assertTrue(!m.isRemoved() && !((BodyLockerBlockEntity) h.getBlockEntity(at)).full(), "stored someone else's mecha");
        h.assertValueEqual(Refusals.lastDelivered(p), "locker.not_yours", "locker line");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void lockerWithAnUnreadableBodySaysSo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.north(2);
        h.setBlock(at, ModRegistry.BODY_LOCKER.get());
        var locker = (BodyLockerBlockEntity) h.getBlockEntity(at);
        CompoundTag saved = new CompoundTag();
        CompoundTag ghost = new CompoundTag();
        ghost.putString("id", "headinajar:no_such_creature");
        saved.put("Stored", ghost);
        saved.putString("StoredName", "Ghost");
        locker.loadWithComponents(saved, h.getLevel().registryAccess());
        locker.use(p);
        h.assertTrue(locker.full(), "an unreadable body was thrown away");
        h.assertValueEqual(Refusals.lastDelivered(p), "locker.unknown", "unknown line");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void roverCargoClosesWhenTheRoverIsStored(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.north(2);
        h.setBlock(at, ModRegistry.BODY_LOCKER.get());
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR.north(1));
        p.setShiftKeyDown(true);
        p.interactOn(rover, InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        h.assertTrue(p.containerMenu instanceof ChestMenu && p.containerMenu.stillValid(p), "the Rover's cargo did not open");
        BlockPos abs = h.absolutePos(at);
        p.gameMode.useItemOn(p, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        h.assertTrue(rover.isRemoved(), "rover not stored");
        h.assertFalse(p.containerMenu.stillValid(p), "the cargo screen of a stored Rover stays open");
        done(h, p);
    }

    // --- servers ------------------------------------------------------------------------------------------

    /** What the steering client is sent survives the trip: flight, climbing, slow falling, no-jump, the abilities. */
    @GameTest(template = "platform")
    public static void hostFilesReachTheClientIntact(GameTestHelper h) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        HostFiles.CODEC.encode(buf, new HostFiles(new HashMap<>(Hosts.definitions())));
        HostFiles back = HostFiles.CODEC.decode(buf);
        HostDefinition ghast = back.files().get(ResourceLocation.parse("minecraft:ghast"));
        HostDefinition spider = back.files().get(ResourceLocation.parse("minecraft:spider"));
        h.assertTrue(ghast != null && ghast.has("flight"), "flight lost on the way");
        h.assertTrue(spider != null && spider.has("climb") && spider.primary().isPresent(), "climb or the web lost on the way");
        h.assertValueEqual(back.files().size(), Hosts.definitions().size(), "files sent");
        done(h);
    }

    // --- mecha that behave ----------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void ejectLetsGoOfTheArms(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = piloted(h, p, 100_000, MechaModule.HYDRAULIC_LEGS, MechaModule.DRILL);
        Network.arms(p, 1);
        h.runAfterDelay(3, () -> {
            h.assertTrue(m.armsShown() == 1, "the drill button was not held");
            key(p, Network.Action.EJECT);
            h.assertTrue(p.getVehicle() == null && m.armsHeld == 0, "the arm stays held after the eject");
            h.runAfterDelay(2, () -> {
                h.assertTrue(m.armsShown() == 0, "an empty mecha still shows a held arm");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void manipulatorHandActsForThePilot(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = piloted(h, p, 100_000, MechaModule.HYDRAULIC_LEGS, MechaModule.MANIPULATOR_HAND);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(5));
        h.runAfterDelay(2, () -> {
            lookAt(p, cow);
            Network.arms(p, 1);
            h.runAfterDelay(3, () -> {
                h.assertTrue(cow.getHealth() < cow.getMaxHealth(), "the Manipulator Hand did nothing");
                h.assertTrue(cow.getLastHurtByMob() == p, "the blow wasn't the pilot's");
                Network.arms(p, 0);
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void tractorBeamNeverSetsNoGravity(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = piloted(h, p, 100_000, MechaModule.HYDRAULIC_LEGS, MechaModule.TRACTOR_BEAM);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(6));
        h.runAfterDelay(2, () -> {
            lookAt(p, cow);
            Vec3 start = cow.position();
            Network.arms(p, 1);
            h.runAfterDelay(5, () -> {
                h.assertTrue(cow.position().distanceTo(start) > 0.2, "the beam did not take the cow");
                h.assertFalse(cow.isNoGravity(), "the beam set the saved NoGravity flag");
                Network.arms(p, 0);
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform")
    public static void conversionsDontGiveAHostItsHeadBack(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = host(h, EntityType.PIG, FLOOR.east(2), p);
        Pig whole = h.spawn(EntityType.PIG, FLOOR.west(2));
        h.assertFalse(EventHooks.canLivingConvert(pig, EntityType.ZOMBIFIED_PIGLIN, t -> {
        }), "a host may turn into a zombified piglin");
        h.assertTrue(EventHooks.canLivingConvert(whole, EntityType.ZOMBIFIED_PIGLIN, t -> {
        }), "a whole pig can no longer convert");
        // lightning on a host pig: a burn, not a zombified piglin
        pig.setHealth(pig.getMaxHealth());
        var bolt = EntityType.LIGHTNING_BOLT.create(h.getLevel());
        pig.thunderHit(h.getLevel(), bolt);
        h.assertTrue(!pig.isRemoved() && h.getEntities(EntityType.ZOMBIFIED_PIGLIN).isEmpty(), "lightning replaced the host");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void mechaAndShellsDontDrown(GameTestHelper h) {
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR, UUID.randomUUID(), 0, MechaModule.AQUA_FINS);
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR.east(2));
        h.assertFalse(m.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value()), "a mecha drowns");
        h.assertFalse(rover.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value()), "a shell drowns");
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aPilotCanBeTeleportedButNotSneakOut(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = piloted(h, p, 100_000, MechaModule.HYDRAULIC_LEGS);
        p.setShiftKeyDown(true);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == m, "sneaking got the pilot out");
        p.setShiftKeyDown(false);
        Vec3 to = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.west(4)));
        p.teleportTo(h.getLevel(), to.x, to.y, to.z, Set.of(), 0, 0);
        h.assertTrue(p.getVehicle() == null, "a teleport snapped the pilot back into the cockpit");
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
        h.assertTrue(state(p).form() == Form.JAR, "the pilot is still a pilot: " + state(p).form());
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void wheelhouseSeatsTwoWithoutAPod(GameTestHelper h) {
        ServerPlayer pilot = player(h, FLOOR);
        ServerPlayer a = player(h, FLOOR.west(1)), b = player(h, FLOOR.west(2)), c = player(h, FLOOR.south(2));
        MechaEntity m = mecha(h, MechaFrame.WHEELHOUSE, FLOOR.east(3), pilot.getUUID(), 100_000);
        FormActions.detach(pilot, 3);
        h.assertTrue(FormActions.mountHost(pilot, m), "could not board");
        a.interactOn(m, InteractionHand.MAIN_HAND);
        b.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(a.getVehicle() == m && b.getVehicle() == m, "the Wheelhouse's seats are out of reach");
        c.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(c.getVehicle() == null && "mecha.seats_full".equals(Refusals.lastDelivered(c)), "a full Wheelhouse said nothing");
        // a passenger who logs out is set down, so the mecha stays in the world
        h.getLevel().getServer().getPlayerList().remove(a);
        h.assertTrue(!m.isRemoved() && a.getVehicle() == null, "the passenger took the mecha with them");
        done(h, pilot, b, c);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void pilotingDoesntDrainTheJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        piloted(h, p, 100_000, MechaModule.HYDRAULIC_LEGS);
        int before = state(p).charge();
        h.runAfterDelay(20, () -> {
            int spent = before - state(p).charge();
            h.assertTrue(spent < 20 * 20, "the jar paid for the mecha: " + spent + " FE in 20 ticks");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void mechaHealthSurvivesAReload(GameTestHelper h) {
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR, UUID.randomUUID(), 1000, MechaModule.HYDRAULIC_LEGS);
        h.runAfterDelay(3, () -> {
            h.assertTrue(m.getHealth() == m.getMaxHealth() && m.getMaxHealth() > MechaFrame.HOPPER.health, "a new mecha is not at full health: "
                    + m.getHealth() + " / " + m.getMaxHealth());
            CompoundTag tag = new CompoundTag();
            h.assertTrue(m.save(tag), "could not save");
            Entity back = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
            h.assertTrue(back instanceof MechaEntity again && again.getHealth() == m.getMaxHealth(),
                    "a reload cost health: " + (back instanceof MechaEntity again ? again.getHealth() : -1));
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void webShotFillsOnlyAir(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var spider = host(h, EntityType.SPIDER, FLOOR.east(2), p);
        Hosts.data(spider).sync = 60;
        FormActions.detach(p, 3);
        FormActions.mountHost(p, spider);
        p.setXRot(90);
        // the block the jar looks straight down at, and the space above it, where the web goes
        Vec3 eye = p.getEyePosition();
        BlockPos feet = h.getLevel().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(0, -16, 0), net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getBlockPos().above();
        h.getLevel().setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "host.web_blocked", "web into water");
        h.assertTrue(h.getLevel().getBlockState(feet).is(Blocks.WATER), "the water was replaced");
        h.getLevel().setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
        int before = WebShots.get(h.getLevel()).size();
        key(p, Network.Action.ABILITY);
        h.assertTrue(h.getLevel().getBlockState(feet).is(Blocks.COBWEB) && WebShots.get(h.getLevel()).size() == before + 1, "no web in the air");
        h.getLevel().setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void hostBlowsAreThePilots(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ShellEntity lunk = h.spawn(ModRegistry.LUNK.get(), FLOOR);
        Pig victim = h.spawn(EntityType.PIG, FLOOR.east(2));
        Pig far = h.spawn(EntityType.PIG, FLOOR.west(6));
        FormActions.detach(p, 3);
        FormActions.mountHost(p, lunk);
        p.attack(victim);
        h.assertTrue(victim.getHealth() < victim.getMaxHealth() && victim.getLastHurtByMob() == p, "the host's blow wasn't credited to its pilot");
        p.attack(far);
        h.assertValueEqual(Refusals.lastDelivered(p), "host.out_of_reach", "out of reach line");
        done(h, p);
    }

    // --- the Cargo Hold ---------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void cargoHoldOpensForOwnerAndPilot(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR);
        MechaEntity parked = mecha(h, MechaFrame.HOPPER, FLOOR.east(2), owner.getUUID(), 1000, MechaModule.HYDRAULIC_LEGS, MechaModule.CARGO_HOLD);
        owner.setShiftKeyDown(true);
        owner.interactOn(parked, InteractionHand.MAIN_HAND);
        owner.setShiftKeyDown(false);
        h.assertTrue(owner.containerMenu instanceof ChestMenu && owner.containerMenu.stillValid(owner), "sneak-use did not open the hold");
        parked.discard();
        h.assertFalse(owner.containerMenu.stillValid(owner), "the hold of a mecha that is gone stays open");
        owner.closeContainer();

        ServerPlayer pilot = player(h, FLOOR.west(2));
        piloted(h, pilot, 1000, MechaModule.HYDRAULIC_LEGS, MechaModule.CARGO_HOLD);
        key(pilot, Network.Action.SWAP);
        h.assertTrue(pilot.containerMenu instanceof ChestMenu, "the swap key did not open the hold from the cockpit");

        ServerPlayer other = player(h, FLOOR.south(2));
        MechaEntity bare = mecha(h, MechaFrame.HOPPER, FLOOR.south(4), other.getUUID(), 1000, MechaModule.HYDRAULIC_LEGS);
        other.setShiftKeyDown(true);
        other.interactOn(bare, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(other), "mecha.no_cargo", "no hold line");
        done(h, owner, pilot, other);
    }

    // --- the Gantry -------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void gantryChargeComesOutOfTheGantry(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        var menu = gantry.createMenu(1, p.getInventory(), p);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.above(), UUID.randomUUID(), 0, MechaModule.HYDRAULIC_LEGS);
        gantry.energy().set(50_000);
        h.assertFalse(menu.clickMenuButton(p, AssemblyGantryMenu.REPAIR), "repaired someone else's mecha");
        h.assertTrue(m.battery() == 0 && "gantry.not_yours".equals(Refusals.lastDelivered(p)), "a stranger charged a mecha");
        m.setOwner(p.getUUID());
        h.assertTrue(menu.clickMenuButton(p, AssemblyGantryMenu.REPAIR), "repair refused");
        h.assertTrue(m.battery() == 50_000 && gantry.energy().getEnergyStored() == 0, "energy made from nothing: mecha " + m.battery() + ", gantry "
                + gantry.energy().getEnergyStored());
        m.discard();
        // a new mecha holds what the Gantry had left after the build
        gantry.energy().set(50_000);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + MechaFrame.HOPPER.firstIndex(MechaModule.Slot.LEGS), module(MechaModule.STILTS));
        h.assertTrue(menu.clickMenuButton(p, AssemblyGantryMenu.ASSEMBLE), "assembly refused");
        MechaEntity built = gantry.onPad();
        h.assertTrue(built != null && built.battery() == 40_000 && gantry.energy().getEnergyStored() == 0,
                "battery " + (built == null ? -1 : built.battery()) + ", gantry " + gantry.energy().getEnergyStored());
        done(h, p);
    }

    // --- every input answers ----------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void hostKeysSayWhyNot(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = host(h, EntityType.COW, FLOOR.east(2), p);
        Hosts.data(cow).sync = 100;
        FormActions.detach(p, 3);
        FormActions.mountHost(p, cow);
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "host.no_ability", "no ability line");
        FormActions.leaveHost(p);

        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR.west(2));
        FormActions.mountHost(p, rover);
        key(p, Network.Action.ABILITY);
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "host.cooling", "cooldown line");
        Hosts.data(rover).primaryCooldown = 0;
        state(p).setCharge(0);
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "host.shell_flat", "flat shell line");
        key(p, Network.Action.SWAP);
        h.assertValueEqual(Refusals.lastDelivered(p), HostEvents.GET_DOWN, "swap on a host");

        // a hosted jar reaching for a second host, or its own body
        Pig other = host(h, EntityType.PIG, FLOOR.north(2), p);
        p.interactOn(other, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), HostEvents.SECOND_HOST, "second host line");
        Entity body = h.getLevel().getEntity(state(p).body());
        if (body != null) {
            p.interactOn(body, InteractionHand.MAIN_HAND);
            h.assertValueEqual(Refusals.lastDelivered(p), HostEvents.OWN_BODY, "own body line");
        }
        // what the client asks for when it cancels the click itself
        Refusals.fromClient(p, HostEvents.NO_HANDS, -1);
        h.assertValueEqual(Refusals.lastDelivered(p), HostEvents.NO_HANDS, "client-cancelled click");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void pilotKeysAndGantryButtonsSayWhyNot(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        piloted(h, p, 100_000, MechaModule.HYDRAULIC_LEGS);
        key(p, Network.Action.SECONDARY);
        h.assertValueEqual(Refusals.lastDelivered(p), "mecha.no_utility", "no utility line");
        ServerPlayer smith = player(h, FLOOR.west(3));
        h.setBlock(FLOOR.west(5), ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR.west(5));
        var menu = gantry.createMenu(1, smith.getInventory(), smith);
        h.assertFalse(menu.clickMenuButton(smith, AssemblyGantryMenu.PLUS), "adjusted an empty slot");
        h.assertValueEqual(Refusals.lastDelivered(smith), "gantry.slot_empty", "empty slot line");
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE, module(MechaModule.STILTS));
        h.assertFalse(menu.clickMenuButton(smith, AssemblyGantryMenu.PLUS), "adjusted a module with no slider");
        h.assertValueEqual(Refusals.lastDelivered(smith), "gantry.no_slider", "no slider line");
        done(h, p, smith);
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aParkedMechaSettlesItsBatteryOnceASecond(GameTestHelper h) {
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR, UUID.randomUUID(), 10_000, MechaModule.HYDRAULIC_LEGS);
        Set<Integer> seen = new HashSet<>();
        for (int t = 1; t <= 40; t++) h.runAfterDelay(t, () -> seen.add(m.battery()));
        h.runAfterDelay(42, () -> {
            h.assertTrue(seen.size() <= 3, "a parked mecha's battery changed " + seen.size() + " times in 40 ticks");
            h.assertTrue(m.battery() >= 10_000 - 60 && m.battery() <= 10_000 - 20, "parked drain changed: " + (10_000 - m.battery()) + " FE in 42 ticks");
            h.succeed();
        });
    }
}
