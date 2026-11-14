package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.block.LeechJarBlockEntity;
import com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity;
import com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Warnings;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.form.KeyAnswers;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.QuantumItems;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * No input that does nothing, no rule that contradicts itself. Each test takes the path the game takes: the key's packet
 * handler ({@code Network.action}), the click ({@code interactOn}, {@code useItemOn}, a menu button), a mob's own swing,
 * the player's and the block's ticks, a command through the dispatcher, the damage and effect events a shot raises.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class RepairFourGameTests {
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

    private static void fit(ServerPlayer p, ModuleType... types) {
        for (int i = 0; i < types.length; i++) state(p).setModule(i, new ItemStack(ModRegistry.module(types[i])));
    }

    private static void key(ServerPlayer p, Network.Action action) {
        Network.action(p, new Network.JarAction(action));
    }

    /** One server tick of this player, as the game runs it (a test player is never ticked by its connection). */
    private static void tick(ServerPlayer p) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
    }

    private static void face(ServerPlayer p, float yaw, float pitch) {
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    private static InteractionResult useBlock(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        return p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.2, 0), Direction.UP, abs, false));
    }

    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setNoAi(true);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "saw failed on " + type);
        return mob;
    }

    private static HeadJarEntity separate(GameTestHelper h, ServerPlayer p, BlockPos headAt) {
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(headAt)), null), "separate failed");
        return h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
    }

    private static ItemStack jarredHead(GameTestHelper h, EntityType<? extends Mob> type, net.minecraft.world.entity.npc.VillagerProfession job) {
        Mob mob = h.spawn(type, FLOOR.above(4));
        if (mob instanceof Villager v) v.setVillagerData(v.getVillagerData().setProfession(job));
        ItemStack stack = JarredHeadItem.of(mob);
        mob.discard();
        return stack;
    }

    private static ItemStack jarredHead(GameTestHelper h, EntityType<? extends Mob> type) {
        Mob mob = h.spawn(type, FLOOR.above(4));
        ItemStack stack = JarredHeadItem.of(mob);
        mob.discard();
        return stack;
    }

    private static void giveGene(net.minecraft.world.entity.LivingEntity e, Gene gene) {
        var set = Genetics.genes(e);
        set.serums.add(new Serum(List.of(new Serum.Entry(gene, 1f)), 100));
        set.changed();
    }

    private static boolean hasKey(List<Component> lines, String key) {
        return lines.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
    }

    /** Records every sound the server plays while it listens (the event Level.playSound raises). */
    private static List<ResourceLocation> listen(List<Object> holder) {
        List<ResourceLocation> heard = new ArrayList<>();
        java.util.function.Consumer<PlayLevelSoundEvent.AtPosition> l = e -> {
            if (e.getSound() != null) heard.add(e.getSound().value().getLocation());
        };
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, l);
        holder.add(l);
        return heard;
    }

    private static void stop(List<Object> holder) {
        for (Object l : holder) NeoForge.EVENT_BUS.unregister(l);
    }

    // --- the jar ------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void theLaserStopsAtAWall(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        FormActions.detach(p, 3);
        fit(p, ModuleType.LACRIMAL_LASER);
        state(p).setCharge(100_000);
        face(p, -90, 0);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(2));
        cow.setNoAi(true);
        h.setBlock(FLOOR, Blocks.STONE);
        h.setBlock(FLOOR.above(), Blocks.STONE);
        float full = cow.getHealth();
        key(p, Network.Action.SECONDARY);
        h.assertValueEqual(cow.getHealth(), full, "the cow behind the wall");
        h.runAfterDelay(25, () -> {
            h.setBlock(FLOOR, Blocks.AIR);
            h.setBlock(FLOOR.above(), Blocks.AIR);
            key(p, Network.Action.SECONDARY);
            h.assertTrue(cow.getHealth() < full, "the cow in the open was not hit");
            // the jar just out of its body fires out through it, never into it
            for (var body : h.getEntities(ModRegistry.HEADLESS_BODY.get())) {
                h.assertTrue(body.getHealth() >= body.getMaxHealth(), "the laser hit the jar's own body");
            }
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void telekinesisAsksForTheTrustOfAMechasOwnerAndARider(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR.north(4)), lifter = player(h, FLOOR.west(3)), rider = player(h, FLOOR.south(4));
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.HOPPER), FLOOR.east(1));
        m.setNoAi(true);
        m.setOwner(owner.getUUID());
        FormActions.detach(lifter, 3);
        fit(lifter, ModuleType.TELEKINESIS);
        state(lifter).setCharge(100_000);
        face(lifter, -90, 0);
        key(lifter, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(lifter), "tk.not_trusted", "lifting a stranger's parked mecha");
        h.assertTrue(state(lifter).heldEntity() < 0, "the mecha was lifted without trust");
        HeadRegistry.get(owner).setTrust(owner.getUUID(), lifter.getUUID(), true);
        try {
            key(lifter, Network.Action.ABILITY);
            h.assertValueEqual(state(lifter).heldEntity(), m.getId(), "a trusted colleague's lift");
            key(lifter, Network.Action.ABILITY);
        } finally {
            HeadRegistry.get(owner).setTrust(owner.getUUID(), lifter.getUUID(), false);
        }
        m.discard();
        // a cow someone else is riding is theirs while they ride it
        Cow cow = host(h, EntityType.COW, FLOOR.east(1), rider);
        FormActions.detach(rider, 3);
        h.assertTrue(FormActions.mountHost(rider, cow), "the rider could not board");
        h.runAfterDelay(12, () -> {
            key(lifter, Network.Action.ABILITY);
            h.assertTrue(state(lifter).heldEntity() < 0, "a ridden cow was lifted without its rider's trust");
            h.assertValueEqual(Refusals.lastDelivered(lifter), "tk.not_trusted", "lifting someone's ridden host");
            done(h, owner, lifter, rider);
        });
    }

    @GameTest(template = "platform")
    public static void theShockLidZapsWhenTheGlassIsGone(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        fit(p, ModuleType.SHOCK_LID);
        state(p).setCharge(100_000);
        state(p).setIntegrity(0);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east());
        zombie.setNoAi(true);
        float full = zombie.getHealth();
        zombie.doHurtTarget(p);
        h.assertTrue(zombie.getHealth() < full, "the lid did not zap with the glass gone");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theRecyclerKeepsASetDownHeadWetLonger(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        separate(h, p, FLOOR.east(2));
        h.assertTrue(state(p).form() == Form.LINKED, "not linked");
        state(p).setSaline(300);
        state(p).setCharge(10_000);
        // two drain steps' worth of ticks, bare and then with the recycler fitted
        int span = 2 * com.coolfreeze23.headinajar.form.LifeSupport.drainTicks(state(p));
        int before = state(p).saline();
        for (int t = 1; t <= span; t++) {
            p.tickCount = t;
            tick(p);
        }
        int bare = before - state(p).saline();
        fit(p, ModuleType.SALINE_RECYCLER);
        state(p).setSaline(300);
        for (int t = span + 1; t <= 2 * span; t++) {
            p.tickCount = t;
            tick(p);
        }
        int recycled = 300 - state(p).saline();
        h.assertTrue(bare == 2 && recycled == 1, span + " ticks at a placed head: " + bare + " mB bare, " + recycled + " mB with the recycler");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void moduleTooltipsSayWhatTheyCostAndKeysAsBound(GameTestHelper h) {
        List<Component> cups = new ArrayList<>(), laser = new ArrayList<>(), zoom = new ArrayList<>();
        ItemStack c = new ItemStack(ModRegistry.module(ModuleType.SUCTION_CUPS)), l = new ItemStack(ModRegistry.module(ModuleType.LACRIMAL_LASER));
        c.getItem().appendHoverText(c, Item.TooltipContext.EMPTY, cups, TooltipFlag.NORMAL);
        l.getItem().appendHoverText(l, Item.TooltipContext.EMPTY, laser, TooltipFlag.NORMAL);
        ItemStack z = new ItemStack(ModRegistry.COLLAR_UPGRADES.get(QuantumItems.Upgrade.ZOOM_OPTIC).get());
        z.getItem().appendHoverText(z, Item.TooltipContext.EMPTY, zoom, TooltipFlag.NORMAL);
        h.assertTrue(hasKey(cups, "module.headinajar.needs_charge") && !hasKey(cups, "module.headinajar.powered"), "Suction Cups' cost line");
        h.assertTrue(hasKey(laser, "module.headinajar.powered"), "the Laser's cost line");
        h.assertTrue(hasKey(zoom, "item.headinajar.collar_zoom_optic.keyed"), "the Zoom Optic names no key");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void flatWheelsFoldAwayAndSaySo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        fit(p, ModuleType.WHEELS);
        state(p).setCharge(5_000);
        h.assertValueEqual(FormActions.drive(p), "message.headinajar.wheels_down", "wheels down");
        state(p).setCharge(0);
        tick(p);
        h.assertFalse(state(p).wheels(), "the wheels stayed down on a flat jar");
        h.assertTrue(Warnings.delivered(p).contains("jar.wheels_flat"), "nothing was said: " + Warnings.delivered(p));
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theSpeakerSaysARepeatedLineAgain(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        fit(p, ModuleType.SPEAKER_GRILLE);
        JarState client = new JarState();
        Runnable chat = () -> NeoForge.EVENT_BUS.post(new ServerChatEvent(p, "Good morning", Component.literal("Good morning")));
        Runnable sync = () -> {
            var buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            JarState.SYNC_CODEC.encode(buf, state(p));
            client.copyFrom(JarState.SYNC_CODEC.decode(buf));
        };
        chat.run();
        sync.run();
        h.assertValueEqual(client.speech(), "Good morning", "the first line");
        // the bubble lasts five seconds of the client's clock (wound on here); then the same words again
        try {
            var until = JarState.class.getDeclaredField("speechUntil");
            until.setAccessible(true);
            until.setLong(client, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        h.assertValueEqual(client.speech(), "", "the bubble after its five seconds");
        sync.run();
        h.assertValueEqual(client.speech(), "", "a sync with nothing new said brought the bubble back");
        chat.run();
        sync.run();
        h.assertValueEqual(client.speech(), "Good morning", "the line said twice");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void aJarRestingInACradleIsToppedUp(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(2);
        h.setBlock(at, ModRegistry.CRADLE.get());
        var cradle = (LifeSupportCradleBlockEntity) h.getBlockEntity(at);
        cradle.saline().fill(new FluidStack(ModFluids.SALINE.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        FormActions.detach(p, 3);
        state(p).setIntegrity(4);
        state(p).setSaline(100);
        // the jar climbs in by a click, as a jar does
        useBlock(h, p, at);
        h.assertTrue(p.blockPosition().equals(h.absolutePos(at)), "the jar did not climb in: at " + p.blockPosition());
        h.runAfterDelay(210, () -> {
            h.assertTrue(state(p).integrity() > 4, "resting mended no glass: " + state(p).integrity());
            h.assertTrue(state(p).saline() > 100, "resting gave no saline: " + state(p).saline());
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void theLinkComingBackAndTheSawsCutAreHeard(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        separate(h, p, FLOOR.east(1));
        List<Object> ears = new ArrayList<>();
        List<ResourceLocation> heard = listen(ears);
        try {
            state(p).setDeadLinkTicks(60);
            p.tickCount = 1000;
            tick(p);
            h.assertTrue(heard.contains(ModSounds.LINK_RESTORED.get().getLocation()), "the link came back silently: " + heard);
            ServerPlayer sawyer = player(h, FLOOR.south(3));
            Cow cow = h.spawn(EntityType.COW, FLOOR.south(4));
            cow.setNoAi(true);
            cow.setHealth(1);
            sawyer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
            sawyer.interactOn(cow, InteractionHand.MAIN_HAND);
            h.assertTrue(heard.contains(ModSounds.BOOTH_CUT.get().getLocation()), "the saw's cut made no blade sound: " + heard);
            done(h, p, sawyer);
        } finally {
            stop(ears);
        }
    }

    @GameTest(template = "platform")
    public static void aRenamedSatchelStillCarriesTheHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(1));
        HeadJarEntity head = separate(h, p, FLOOR.east(1));
        ItemStack satchel = new ItemStack(ModRegistry.HEAD_SATCHEL.get());
        satchel.set(DataComponents.CUSTOM_NAME, Component.literal("Gerald"));
        p.getInventory().setItem(5, satchel);
        p.interactOn(head, InteractionHand.MAIN_HAND);
        h.assertTrue(state(p).carryingHead(), "a satchel named Gerald did not count");
        done(h, p);
    }

    // --- hosts ---------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void sneakHighOnAFlyingHostKeepsTheJarAboardAndSaysSo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Bat bat = host(h, EntityType.BAT, FLOOR.above(5), p);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, bat), "could not board the bat");
        bat.setOnGround(false);
        p.setShiftKeyDown(true);
        p.rideTick();
        h.assertTrue(p.getVehicle() == bat, "sneak high in the air dropped the jar");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.too_high", "the line");
        bat.setOnGround(true);
        p.setShiftKeyDown(true);
        p.rideTick();
        h.assertTrue(p.getVehicle() == null, "sneak on the ground did not let the jar off");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aLacedHostKeepsTheFluidAndTheLace(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = host(h, EntityType.COW, FLOOR.east(), p);
        Hosts.data(cow).laced = true;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.EMBALMING_FLUID.get()));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertValueEqual(p.getMainHandItem().getCount(), 1, "fluid left");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.embalm_laced", "the embalming line");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.NEURAL_LACE.get()));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertValueEqual(p.getMainHandItem().getCount(), 1, "lace left");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.lace_again", "the lace line");
        done(h, p);
    }

    // --- genes ---------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void theGeneKeysAlwaysAnswer(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "gene.none", "a body with no gene");
        int said = Refusals.delivered(p).size();
        key(p, Network.Action.ABILITY_SHARED);
        h.assertValueEqual(Refusals.delivered(p).size(), said, "a sprint on the shared key was answered as a gene press");
        giveGene(p, Gene.GUARDIAN_EYE);
        key(p, Network.Action.SECONDARY);
        h.assertValueEqual(Refusals.lastDelivered(p), "gene.one_only", "the second key with one gene");
        face(p, 0, -90);
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "gene.no_target", "a Guardian Eye at the open sky");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aHostsOwnGenesFireFromItsKeys(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = host(h, EntityType.COW, FLOOR.east(), p);
        giveGene(cow, Gene.GUARDIAN_EYE);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, cow), "could not board");
        face(p, 0, -90);
        key(p, Network.Action.ABILITY);
        // the gene's own answer: it fired at nothing (without this fix the host's "no ability" was all there was)
        h.assertValueEqual(Refusals.lastDelivered(p), "gene.no_target", "the cow's gene key");
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(4));
        zombie.setNoAi(true);
        Vec3 to = zombie.getEyePosition().subtract(p.getEyePosition());
        face(p, (float) (Math.atan2(-to.x, to.z) * 180 / Math.PI), (float) (-Math.atan2(to.y, to.horizontalDistance()) * 180 / Math.PI));
        float full = zombie.getHealth();
        key(p, Network.Action.ABILITY);
        h.assertTrue(zombie.getHealth() < full, "the cow's Guardian Eye did not fire");
        done(h, p);
    }

    // --- heads at work and the lab ------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theLeechJarNeverTakesTheLastPoint(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.LEECH_JAR.get());
        var jar = (LeechJarBlockEntity) h.getBlockEntity(FLOOR);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east());
        cow.setNoAi(true);
        cow.setHealth(1);
        try {
            var timer = LeechJarBlockEntity.class.getDeclaredField("timer");
            timer.setAccessible(true);
            timer.setInt(jar, LeechJarBlockEntity.INTERVAL - 2);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        // the block's own tick takes the sample
        h.runAfterDelay(5, () -> {
            h.assertTrue(jar.count() == 1, "no sample after the interval");
            h.assertTrue(cow.isAlive() && cow.getHealth() >= 1, "the leeches took the last point: " + cow.getHealth());
            h.succeed();
        });
    }

    private static TurretPedestalBlockEntity turret(GameTestHelper h, BlockPos at, EntityType<? extends Mob> head, ServerPlayer owner) {
        h.setBlock(at, ModRegistry.TURRET_PEDESTAL.get());
        var t = (TurretPedestalBlockEntity) h.getBlockEntity(at);
        t.items().setStackInSlot(0, jarredHead(h, head));
        t.energy().set(40_000);
        if (owner != null) t.setOwner(owner.getUUID());
        return t;
    }

    private static void fire(GameTestHelper h, TurretPedestalBlockEntity t) {
        TurretPedestalBlockEntity.serverTick(h.getLevel(), t.getBlockPos(), t.getBlockState(), t);
    }

    @GameTest(template = "platform")
    public static void aTurretDoesNotShootThroughWalls(GameTestHelper h) {
        h.setBlock(FLOOR.below(), Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(FLOOR.below())).setItem(0, new ItemStack(Items.ARROW, 4));
        var t = turret(h, FLOOR, EntityType.SKELETON, null);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(5));
        zombie.setNoAi(true);
        for (int y = 0; y < 4; y++) h.setBlock(FLOOR.east(2).above(y), Blocks.STONE);
        fire(h, t);
        h.assertValueEqual(((ChestBlockEntity) h.getBlockEntity(FLOOR.below())).getItem(0).getCount(), 4, "arrows after a zombie behind a wall");
        for (int y = 0; y < 4; y++) h.setBlock(FLOOR.east(2).above(y), Blocks.AIR);
        fire(h, t);
        h.assertTrue(((ChestBlockEntity) h.getBlockEntity(FLOOR.below())).getItem(0).getCount() < 4, "no shot at a zombie in the open");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void aBowHeadWithNoArrowsWaitsAndIsWarnedOfAtFitting(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        var t = turret(h, FLOOR, EntityType.SKELETON, p);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(5));
        zombie.setNoAi(true);
        int before = t.energy().getEnergyStored();
        for (int i = 0; i < 40; i++) fire(h, t);
        int spent = before - t.energy().getEnergyStored();
        h.assertTrue(spent < 100, "two seconds dry drew " + spent + " FE (it used to be 400)");
        // a bow head fitted by a click with no arrows in reach is warned of; a creeper head, that it goes off once
        BlockPos second = FLOOR.north(3), third = FLOOR.south(3);
        h.setBlock(second, ModRegistry.TURRET_PEDESTAL.get());
        h.setBlock(third, ModRegistry.TURRET_PEDESTAL.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, jarredHead(h, EntityType.SKELETON));
        useBlock(h, p, second);
        h.assertTrue(Warnings.delivered(p).contains("turret.no_arrows"), "fitting a bow head was not warned: " + Warnings.delivered(p));
        p.setItemInHand(InteractionHand.MAIN_HAND, jarredHead(h, EntityType.CREEPER));
        useBlock(h, p, third);
        h.assertTrue(Warnings.delivered(p).contains("turret.creeper_once"), "fitting a creeper head was not warned: " + Warnings.delivered(p));
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aTurretsShotsPassOverWhoItSpares(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR.west(2));
        var t = turret(h, FLOOR, EntityType.CREEPER, owner);
        Villager villager = h.spawn(EntityType.VILLAGER, FLOOR.north(2));
        villager.setNoAi(true);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(2));
        zombie.setNoAi(true);
        float ownerHealth = owner.getHealth(), villagerHealth = villager.getHealth(), zombieHealth = zombie.getHealth();
        fire(h, t);
        h.assertTrue(zombie.getHealth() < zombieHealth || zombie.isRemoved(), "the blast spared the zombie");
        h.assertValueEqual(owner.getHealth(), ownerHealth, "the owner's health after the blast");
        h.assertValueEqual(villager.getHealth(), villagerHealth, "the villager's health after the blast");
        // an arrow, fangs and a splash from a turret leave the villager be
        BlockPos at = h.absolutePos(FLOOR);
        Arrow arrow = new Arrow(h.getLevel(), villager.getX(), villager.getY() + 2, villager.getZ(), new ItemStack(Items.ARROW), null);
        arrow.getPersistentData().putLong(TurretPedestalBlockEntity.SHOT_TAG, at.asLong());
        h.getLevel().addFreshEntity(arrow);
        EvokerFangs fangs = new EvokerFangs(h.getLevel(), villager.getX(), villager.getY(), villager.getZ(), 0, 0, null);
        fangs.getPersistentData().putLong(TurretPedestalBlockEntity.SHOT_TAG, at.asLong());
        h.getLevel().addFreshEntity(fangs);
        ThrownPotion potion = new ThrownPotion(h.getLevel(), villager.getX(), villager.getY() + 2, villager.getZ());
        potion.getPersistentData().putLong(TurretPedestalBlockEntity.SHOT_TAG, at.asLong());
        h.getLevel().addFreshEntity(potion);
        // the damage and the effect as the shots deal them
        villager.hurt(h.getLevel().damageSources().arrow(arrow, null), 4);
        villager.hurt(h.getLevel().damageSources().magic(), 6);
        villager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200), potion);
        h.assertValueEqual(villager.getHealth(), villagerHealth, "the villager's health after an arrow and fangs");
        h.assertFalse(villager.hasEffect(MobEffects.WEAKNESS), "the splash took on the villager");
        arrow.discard();
        fangs.discard();
        potion.discard();
        // a snow golem head's snowball slows what it hits, not the target it was aimed at from afar
        Zombie far = h.spawn(EntityType.ZOMBIE, FLOOR.south(4));
        far.setNoAi(true);
        var snowTurret = turret(h, FLOOR.south(1), EntityType.SNOW_GOLEM, owner);
        Snowball ball = new Snowball(h.getLevel(), far.getX(), far.getY() + 3, far.getZ());
        ball.getPersistentData().putLong(TurretPedestalBlockEntity.SHOT_TAG, snowTurret.getBlockPos().asLong());
        ball.setDeltaMovement(0, -1.5, 0);
        h.getLevel().addFreshEntity(ball);
        h.runAfterDelay(10, () -> {
            h.assertTrue(far.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the snowball's hit did not slow the zombie");
            done(h, owner);
        });
    }

    @GameTest(template = "platform")
    public static void theSocketsKeysReachLevelOne(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        FormActions.detach(p, 3);
        h.assertTrue(socket.plugIn(p) && state(p).form() == Form.SOCKET, "could not plug in");
        h.assertTrue(NeuralSocketBlockEntity.levelForKey(1) == 1 && NeuralSocketBlockEntity.levelForKey(9) == 15, "the ends of the keys");
        // the level packet the first hotbar key sends
        NeuralSocketBlockEntity.input(p, 0, Direction.UP.get3DDataValue(), NeuralSocketBlockEntity.levelForKey(1));
        h.assertValueEqual(socket.output(Direction.UP), 1, "level after the first key");
        NeuralSocketBlockEntity.unplug(p);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void swapAndDriveOnFootAreAnswered(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        // what the client sends when it stops the key
        Refusals.fromClient(p, KeyAnswers.SWAP_BODY, -1);
        h.assertValueEqual(Refusals.lastDelivered(p), KeyAnswers.SWAP_BODY, "swap with the head on");
        Refusals.fromClient(p, KeyAnswers.DRIVE_BODY, -1);
        h.assertValueEqual(Refusals.lastDelivered(p), KeyAnswers.DRIVE_BODY, "drive on foot");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void trustingTwiceSaysItWasAlreadySo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR), colleague = player(h, FLOOR.east(4));
        List<Component> said = new ArrayList<>();
        CommandSource rec = new CommandSource() {
            @Override
            public void sendSystemMessage(Component c) {
                said.add(c);
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
        };
        var source = p.createCommandSourceStack().withSource(rec).withPermission(4);
        String who = String.format(java.util.Locale.ROOT, "@a[x=%.2f,y=%.2f,z=%.2f,distance=..0.5]", colleague.getX(), colleague.getY(), colleague.getZ());
        var commands = h.getLevel().getServer().getCommands();
        try {
            commands.performPrefixedCommand(source, "headoffice trust add " + who);
            commands.performPrefixedCommand(source, "headoffice trust add " + who);
            h.assertTrue(hasKey(said, "command.headinajar.trusted") && hasKey(said, "command.headinajar.already_trusted"), "the lines: " + said);
            commands.performPrefixedCommand(source, "headoffice trust remove " + who);
            commands.performPrefixedCommand(source, "headoffice trust remove " + who);
            h.assertTrue(hasKey(said, "command.headinajar.untrusted") && hasKey(said, "command.headinajar.never_trusted"), "the lines: " + said);
        } finally {
            HeadRegistry.get(p).setTrust(p.getUUID(), colleague.getUUID(), false);
        }
        done(h, p, colleague);
    }

    @GameTest(template = "platform")
    public static void theGantrysRepairSaysWhatIsMissing(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.HOPPER), FLOOR.above());
        m.setNoAi(true);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < MechaFrame.HOPPER.totalSlots(); i++) slots.add(ItemStack.EMPTY);
        slots.set(MechaFrame.HOPPER.firstIndex(MechaModule.Slot.LEGS), new ItemStack(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS)));
        m.setModules(slots);
        m.setOwner(p.getUUID());
        m.setBattery(m.batteryCapacity());
        m.setHealth(m.getMaxHealth() - 30);
        var menu = gantry.createMenu(1, p.getInventory(), p);
        h.assertFalse(menu.clickMenuButton(p, AssemblyGantryMenu.REPAIR), "Repair with no plating claimed success");
        h.assertValueEqual(Refusals.lastDelivered(p), "gantry.no_plating", "the plating line");
        m.setHealth(m.getMaxHealth());
        h.assertFalse(menu.clickMenuButton(p, AssemblyGantryMenu.REPAIR), "Repair of a sound mecha claimed success");
        h.assertValueEqual(Refusals.lastDelivered(p), "gantry.nothing_to_repair", "the nothing-to-do line");
        done(h, p);
    }

    // --- the machines' upper halves, the rig's refusal, the sounds ----------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 30)
    public static void aMountOnATallMachinesUpperHalfOrTopIsStaff(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getBlockState(FLOOR.above()).getBlock() instanceof com.coolfreeze23.headinajar.machine.MachineTopBlock, "no upper half");
            BlockPos machine = h.absolutePos(FLOOR);
            // against the upper half's side, facing into it
            BlockPos side = FLOOR.above().east();
            h.setBlock(side, ModRegistry.EMPLOYEE_MOUNT.get().defaultBlockState().setValue(com.coolfreeze23.headinajar.block.MachineBlock.FACING, Direction.EAST));
            ((com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity) h.getBlockEntity(side)).use(p, jarredHead(h, EntityType.VILLAGER, net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
            h.assertValueEqual(com.coolfreeze23.headinajar.block.HeadJobs.staff(h.getLevel(), machine, "librarian"), 0.5f, "staff on the upper half's side");
            h.setBlock(side, Blocks.AIR);
            // on top of the machine
            BlockPos top = FLOOR.above(2);
            h.setBlock(top, ModRegistry.EMPLOYEE_MOUNT.get().defaultBlockState().setValue(com.coolfreeze23.headinajar.block.MachineBlock.FACING, Direction.NORTH));
            ((com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity) h.getBlockEntity(top)).use(p, jarredHead(h, EntityType.VILLAGER, net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
            h.assertValueEqual(com.coolfreeze23.headinajar.block.HeadJobs.staff(h.getLevel(), machine, "librarian"), 0.5f, "staff on top");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aLeydenJarDoesNotFeedTheOneBelowIt(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.LEYDEN_JAR.get());
        h.setBlock(FLOOR.above(2), ModRegistry.LEYDEN_JAR.get());
        var below = (com.coolfreeze23.headinajar.block.PowerBlockEntities.LeydenJar) h.getBlockEntity(FLOOR);
        var above = (com.coolfreeze23.headinajar.block.PowerBlockEntities.LeydenJar) h.getBlockEntity(FLOOR.above(2));
        above.energy().set(above.energy().getMaxEnergyStored());
        below.energy().set(0);
        // the jars' own ticks
        h.runAfterDelay(20, () -> {
            h.assertValueEqual(below.energy().getEnergyStored(), 0, "the lower jar's charge, fed through its upper half");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void theRigWithdrawsOnARefusalRatherThanPlayingARejection(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.INJECTION_RIG.get());
        var rig = (com.coolfreeze23.headinajar.block.LabMachines.InjectionRig) h.getBlockEntity(FLOOR);
        // a serum too heavy for a body (Ender Blink weighs 4; a body takes 3)
        ItemStack cassette = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
        new Serum(List.of(new Serum.Entry(Gene.ENDER_BLINK, 1f)), 100).writeTo(cassette);
        rig.items().setStackInSlot(0, cassette);
        rig.energy().set(20_000);
        ServerPlayer p = player(h, FLOOR.south(2));
        h.runAfterDelay(3, () -> h.assertTrue(rig.start(p), "Inject was refused before the jab"));
        h.runAfterDelay(45, () -> {
            long now = h.getLevel().getGameTime();
            boolean withdrew = rig.kit().since(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig.EV_CANCEL, now) < 20;
            boolean rejected = rig.kit().since(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig.EV_RESULT, now) < 40;
            boolean kept = !rig.items().getStackInSlot(0).isEmpty();
            String said = Refusals.lastDelivered(p);
            h.getLevel().getServer().getPlayerList().remove(p);
            h.assertTrue(withdrew && !rejected, "the rig played " + (rejected ? "a rejection" : "nothing") + " for a refusal");
            h.assertTrue(kept, "the cassette was used up");
            h.assertValueEqual(said, "lab.rig.too_heavy", "the refusal");
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void aGuardianTurretsLockIsHeard(GameTestHelper h) {
        List<Object> ears = new ArrayList<>();
        List<ResourceLocation> heard = listen(ears);
        try {
            var t = turret(h, FLOOR, EntityType.GUARDIAN, null);
            Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(5));
            zombie.setNoAi(true);
            fire(h, t);
            h.assertTrue(heard.contains(com.coolfreeze23.headinajar.machine.MachineSounds.TURRET_LOCK.get().getLocation()), "the lock was silent: " + heard);
        } finally {
            stop(ears);
        }
        h.succeed();
    }
}
