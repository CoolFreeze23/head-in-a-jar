package com.coolfreeze23.headinajar.supplier;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.host.HostDefinition;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffEvents;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffShake;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Locale;
import java.util.Map;

/**
 * The new arrivals, and the new things the supplier delivers, at the company's tools and machines that are not a
 * species row (0.8.0): Embalming Fluid, Neural Lace and honeycomb on their bodies, the Body Locker, the Galvanic Pile,
 * the Saline Mixer, the Charging Dock, the Surgical Wrench, a mecha, the Cerebral Array, the saw on a statue, bundles,
 * and the Exit Interview's reasons for leaving that only they have. Everything is named by its {@code minecraft:} id or
 * by a tag (the game's own where it has one, else {@code headinajar:supplied/*} with optional entries), never by the
 * supplier: in a game without them none of it is ever true, and nothing changes.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class SuppliedMachines {
    public static final String CREAKING = "minecraft:creaking", COPPER_GOLEM = "minecraft:copper_golem", HAPPY_GHAST = "minecraft:happy_ghast",
            NAUTILUS = "minecraft:nautilus", CAMEL_HUSK = "minecraft:camel_husk", DRIED_GHAST = "minecraft:dried_ghast",
            CREAKING_HEART = "minecraft:creaking_heart";

    /** The game's own tags (26.2 has each; 1.21.1 has none of them, so they are empty without the supplier). */
    public static final TagKey<Item> STATUE_ITEMS = item(ResourceLocation.withDefaultNamespace("copper_golem_statues")),
            BUNDLES = item(ResourceLocation.withDefaultNamespace("bundles")),
            HARNESSES = item(ResourceLocation.withDefaultNamespace("harnesses")),
            COPPER_BLOCKS = item(ResourceLocation.withDefaultNamespace("copper"));
    public static final TagKey<Block> STATUES = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("copper_golem_statues"));
    /** The company's own (every entry optional): resin, copper that is not Mecha Plating, a nautilus's armour. */
    public static final TagKey<Item> RESIN = item(HeadInAJar.id("supplied/resin")), COPPER_PARTS = item(HeadInAJar.id("supplied/copper_parts")),
            NAUTILUS_ARMOR = item(HeadInAJar.id("supplied/nautilus_armor"));

    /** How far from its heart a creaking was when it was torn down for being too far (the heart lets it go past 34). */
    public static final double HEART_FAR = 32;
    /** The time a copper golem was checked into a Body Locker, kept in its stored record (its weathering clock stops). */
    public static final String CHECKED_IN = "HeadInAJarCheckedIn";
    private static final String WEATHER_AGE = "next_weather_age";

    /** The reason keys of Embalming Fluid and Neural Lace on a body that never rots (its host file decays at 0). */
    private static final Map<String, String> EMBALM = Map.of(CREAKING, "host.embalm_creaking", CAMEL_HUSK, "host.embalm_camel_husk",
            COPPER_GOLEM, "host.embalm_copper_golem");
    private static final Map<String, String> LACE = Map.of(CREAKING, "host.lace_creaking", CAMEL_HUSK, "host.lace_camel_husk",
            COPPER_GOLEM, "host.lace_copper_golem");

    private SuppliedMachines() {
    }

    private static TagKey<Item> item(ResourceLocation id) {
        return TagKey.create(Registries.ITEM, id);
    }

    /** A creature's id string. */
    public static String id(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    // --- bodies: Embalming Fluid, Neural Lace, honeycomb -------------------------------------------------------------

    /** Whether this body never rots: its host file says it decays at 0. */
    private static boolean neverRots(Mob host) {
        HostDefinition def = Hosts.definition(host);
        return def != null && def.decayPerMinute() <= 0;
    }

    /** Embalming Fluid on a new arrival's body that never rots: the reason it is kept, or null (it embalms as any). */
    public static String embalmRefusal(Mob host) {
        String why = EMBALM.get(id(host));
        return why != null && neverRots(host) ? why : null;
    }

    /** Neural Lace on a new arrival's body that never rots: the reason it is kept, or null (it laces as any). */
    public static String laceRefusal(Mob host) {
        String why = LACE.get(id(host));
        return why != null && neverRots(host) ? why : null;
    }

    /** Whether a copper golem is waxed: its own record keeps its weathering clock at -2 (the game's mark for it). */
    public static boolean waxed(Mob golem) {
        return golem.saveWithoutId(new CompoundTag()).getLong(WEATHER_AGE) == -2;
    }

    /**
     * Honeycomb on a copper golem's body is the golem's own business: the game waxes it. True when this click should go
     * to the creature itself: a person (a bare jar has no hands), not taking a lead back, holding honeycomb at a copper
     * golem host that is not waxed yet. The client lets every such click through and the server decides.
     */
    public static boolean waxesItself(Player clicker, ItemStack held, Mob host) {
        if (!held.is(Items.HONEYCOMB) || !COPPER_GOLEM.equals(id(host))) return false;
        if (host.isLeashed() && host.getLeashHolder() == clicker || FormEvents.lockedOut(clicker)) return false;
        var state = FormActions.state(clicker);
        if (state.form() == Form.JAR || state.form() == Form.LINKED && state.carryingHead()) return false;
        return clicker.level().isClientSide || !waxed(host);
    }

    /** Honeycomb on a new arrival's body (a person's): the reason it is refused, or null (not one of theirs). */
    public static String honeycombRefusal(ItemStack held, Mob host) {
        if (!held.is(Items.HONEYCOMB)) return null;
        String id = id(host);
        if (!Supplier.NEW_ARRIVALS.contains(id)) return null;
        if (id.equals(COPPER_GOLEM)) return waxed(host) ? "host.honeycomb_again" : null;
        return id.equals(CREAKING) ? "host.honeycomb_wood" : "host.honeycomb";
    }

    // --- the Body Locker ----------------------------------------------------------------------------------------------

    /** A body the locker will not take, by its reason, or null. A creaking bound to a heart belongs to the heart. */
    public static String lockerRefusal(Mob host) {
        String id = id(host);
        if (id.equals(HAPPY_GHAST)) return "locker.too_happy";
        if (id.equals(CREAKING) && Workforce.heartBound(host)) return "locker.heart";
        return null;
    }

    /** The line for a body stored (its name is the argument). */
    public static String storedLine(Mob host) {
        return COPPER_GOLEM.equals(id(host)) ? "message.headinajar.locker_stored_copper" : "message.headinajar.locker_stored";
    }

    /** At check-in: a copper golem's record notes the time, so its weathering clock can stop while it is inside. */
    public static void checkIn(Mob host, CompoundTag stored, long now) {
        if (COPPER_GOLEM.equals(id(host))) stored.putLong(CHECKED_IN, now);
    }

    /**
     * The record to let out (a copy; the stored one is untouched until the body is out). A copper golem's weathering
     * runs on the game's clock ({@code next_weather_age} is a game time), so its next stage is moved on by the time it
     * spent inside: it comes out as weathered as it went in. Waxed (-2), unset (-1) and fully weathered (0) stay.
     */
    public static CompoundTag checkOut(CompoundTag stored, long now) {
        CompoundTag out = stored.copy();
        if (!out.contains(CHECKED_IN)) return out;
        long inside = Math.max(0, now - out.getLong(CHECKED_IN));
        out.remove(CHECKED_IN);
        long next = out.getLong(WEATHER_AGE);
        if (out.contains(WEATHER_AGE) && next > 0) out.putLong(WEATHER_AGE, next + inside);
        return out;
    }

    // --- machines and tools -------------------------------------------------------------------------------------------

    /** A dried ghast (the item). */
    public static boolean driedGhast(ItemStack stack) {
        return !stack.isEmpty() && DRIED_GHAST.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** What a mecha says to a thing it will not take for its plating or its body, by reason, or null. */
    public static String mechaRefusal(ItemStack held) {
        if (held.is(NAUTILUS_ARMOR)) return "mecha.nautilus_armor";
        if (held.is(HARNESSES)) return "mecha.harness";
        if (held.is(COPPER_PARTS)) return "mecha.copper";
        return null;
    }

    /** The mecha's frame's name ("Kite"). */
    public static Component frameName(MechaEntity mecha) {
        return Component.translatable("entity.headinajar." + mecha.frame().name().toLowerCase(Locale.ROOT));
    }

    /** The Surgical Wrench on a copper golem: Facilities' current is not lightning. Before the golem's own use and a host's. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void wrench(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(ModRegistry.SURGICAL_WRENCH.get()) || !(event.getTarget() instanceof Mob golem)
                || !COPPER_GOLEM.equals(id(golem)) || FormEvents.lockedOut(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (event.getEntity() instanceof ServerPlayer player) Refusals.deny(player, StaffShake.at(golem), "wrench.copper_golem");
    }

    /** The saw on a copper golem statue: it is a colleague's likeness, and an axe is the tool. Before the statue's own use. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void sawAStatue(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(ModRegistry.SURGICAL_SAW.get()) || FormEvents.lockedOut(event.getEntity())) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(STATUES)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        Vec3 at = event.getHitVec().getLocation();
        Refuser statue = () -> at;
        Refusals.deny(player, statue, "saw.statue");
        // the blade skids off the copper: a few flecks of it where it touched
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 6, 0.08, 0.08, 0.08, 0.02);
    }

    /**
     * A jarred head and a bundle: a colleague does not go in luggage. Either way round (the head carried onto a bundle,
     * or a bundle carried onto a head), before either item's own click rule; both sides stop it, the server says why.
     */
    @SubscribeEvent
    public static void bundle(ItemStackedOnOtherEvent event) {
        ItemStack carried = event.getCarriedItem(), under = event.getStackedOnItem();
        boolean head = carried.getItem() instanceof JarredHeadItem && under.is(BUNDLES) || under.getItem() instanceof JarredHeadItem && carried.is(BUNDLES);
        if (!head) return;
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer player) Refusals.deny(player, "bundle.colleague");
    }

    // --- leaving the company without dying ----------------------------------------------------------------------------

    /**
     * Staff who leave the world without a death the game reports: a creaking torn down by its heart (broken, or left too
     * far behind) and a copper golem turned to a statue where it stood. The Exit Interview goes out as for a death; the
     * statue's has no Condolence Card (nobody died). Any other removal (a Body Locker, a chunk unloading) is not leaving.
     */
    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;
        var removal = mob.getRemovalReason();
        if (removal != Entity.RemovalReason.DISCARDED && removal != Entity.RemovalReason.KILLED) return;
        String id = id(mob);
        if (!id.equals(CREAKING) && !id.equals(COPPER_GOLEM)) return;
        StaffFile f = Staff.of(mob);
        if (f == null || f.left != null || f.employer == null) return;
        if (!Hosts.isHost(mob) && !Workforce.isWorker(mob)) return;
        String why = id.equals(CREAKING) ? creakingReason(level, mob) : statue(level, mob) ? "staff.headinajar.reason.retired" : null;
        if (why == null) return;
        StaffEvents.close(level, mob, f, why, !why.endsWith(".retired"));
    }

    /** Whether a copper golem stands where a statue now is (the game places the statue, then lets the golem go). */
    static boolean statue(ServerLevel level, Mob golem) {
        return level.getBlockState(golem.blockPosition()).is(STATUES);
    }

    /**
     * Why a creaking bound to a heart was torn down, from its own record ({@code home_pos}): its heart is gone ("its heart
     * was broken"), or it was more than {@link #HEART_FAR} from it ("wandered too far from its heart"); else null.
     */
    public static String creakingReason(ServerLevel level, Mob creaking) {
        var home = NbtUtils.readBlockPos(creaking.saveWithoutId(new CompoundTag()), "home_pos");
        if (home.isEmpty()) return null;
        BlockPos at = home.get();
        if (!level.isLoaded(at)) return null;
        if (!CREAKING_HEART.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()).toString())) {
            return "staff.headinajar.reason.heart_broken";
        }
        return creaking.position().distanceTo(Vec3.atBottomCenterOf(at)) > HEART_FAR ? "staff.headinajar.reason.heart_far" : null;
    }
}
