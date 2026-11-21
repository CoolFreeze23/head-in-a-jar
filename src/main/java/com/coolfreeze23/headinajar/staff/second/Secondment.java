package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerIndex;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.UUID;

/**
 * The Secondment Collar at work on a host: fitting it, seating a jarred head on it, unseating the head (Sneak + Use
 * with an empty hand: file and grade kept), taking the collar back with shears, and what else a seconded host takes
 * through its collar (a Glass Patch for the jar, a bucket of broth for the canister, a sneak-held syringe for the head).
 * Every refusal in the book's order, the headcount cap, trust, the sixteen pairings DOLORES remarks on, the memo and
 * the advancement. Called first among a host's instruments ({@code HostInteractions.instrument}); a collar used on
 * anything that is not a host is refused here too.
 */
public final class Secondment {
    /** Where a head keeps its jar's glass while it is not seated (the head item's own data). */
    public static final String GLASS = "Glass";
    /** The pairings a player has heard, one bit each, in their persisted data. */
    public static final String PAIRINGS = "headinajar_pairings";
    /** The famous pairings, head then body, in bit order (never reorder: the bits are saved). */
    public static final List<String> PAIRS = List.of(
            "villager_iron_golem", "skeleton_horse", "zombie_horse", "skeleton_spider", "enderman_pig", "cow_creeper",
            "chicken_ghast", "zombie_ghast", "blaze_strider", "wolf_spider", "cat_iron_golem", "warden_chicken",
            "sniffer_pig", "villager_llama", "wandering_trader_camel", "piglin_strider");
    /** The vanilla heads that are trophies, not colleagues. */
    private static final List<net.minecraft.world.item.Item> TROPHIES = List.of(Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL,
            Items.ZOMBIE_HEAD, Items.CREEPER_HEAD, Items.PIGLIN_HEAD);
    /** What tests read: the pairing lines said, newest last ("player|pair"). */
    private static final List<String> SAID = new java.util.ArrayList<>();

    private Secondment() {
    }

    public static List<String> said() {
        return SAID;
    }

    // --- the dispatch ---------------------------------------------------------------------------------------------

    /**
     * A held item's (or an empty hand's) secondment use on a host, or null when it has none here. Decided the same way
     * on both sides; only the server acts ({@code player} null on the client).
     */
    public static InteractionResult instrument(Player clicker, ServerPlayer player, ItemStack held, Mob host, InteractionHand hand) {
        SecondeeData d = Secondees.data(host);
        boolean seated = d != null && d.seated();
        var state = FormActions.state(clicker);
        boolean jarLike = state.form() == Form.JAR || state.form() == Form.LINKED && state.carryingHead();
        // a jar climbing onto a body that already has a head on staff: whatever the jar holds
        if (seated && jarLike) return refuse(player, host, "secondment.boarding_seconded");
        if (held.isEmpty()) {
            if (!seated || hand != InteractionHand.MAIN_HAND) return null;
            if (clicker.isSecondaryUseActive()) {
                if (FormEvents.lockedOut(clicker)) return refuse(player, host, "host.no_hands");
                if (player != null) unseat(player, host, d);
                return InteractionResult.SUCCESS;
            }
            if (state.form() == Form.BODY || state.form() == Form.LINKED) {
                if (player != null) player.displayClientMessage(Component.translatable("message.headinajar.secondment.status",
                        Math.round(d.teamSync), d.canister), true);
                return InteractionResult.SUCCESS;
            }
            return null;
        }
        boolean collarItem = held.is(SecondRegistry.SECONDMENT_COLLAR.get());
        boolean headItem = held.is(ModRegistry.JARRED_HEAD.get());
        if (collarItem || headItem) {
            // the jar menu's own answer: a bare jar has no hands for a collar or a head
            if (FormEvents.lockedOut(clicker)) return refuse(player, host, "host.no_hands");
            if (player == null) return InteractionResult.SUCCESS;
            if (collarItem) fit(player, held, host, d);
            else seat(player, held, host, d);
            return InteractionResult.SUCCESS;
        }
        if (FormEvents.lockedOut(clicker) || jarLike) return null;
        if (held.is(Items.PLAYER_HEAD)) return refuse(player, host, "secondment.player_head");
        if (held.is(Items.DRAGON_HEAD)) return refuse(player, host, "secondment.dragon");
        for (var trophy : TROPHIES) if (held.is(trophy)) return refuse(player, host, "secondment.trophy");
        if (d == null) return null;
        if (held.is(Items.SHEARS)) {
            if (seated) return refuse(player, host, "secondment.seated_collar");
            if (player != null) collarOff(player, host, d);
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModFluids.BROTH.bucket.get())) {
            if (d.canister >= SecondeeData.CANISTER) return refuse(player, host, "secondment.canister_full");
            if (player != null) topUp(player, held, host, d);
            return InteractionResult.SUCCESS;
        }
        if (seated && held.is(ModRegistry.GLASS_PATCH.get())) {
            if (d.glass >= d.glassMax) return refuse(player, host, "secondment.glass_full");
            if (player != null) patch(player, held, host, d);
            return InteractionResult.SUCCESS;
        }
        if (seated && clicker.isSecondaryUseActive() && held.getItem() instanceof GeneticsItems.SyringeItem
                && com.coolfreeze23.headinajar.block.HeadDonor.on()) {
            if (player != null) com.coolfreeze23.headinajar.block.HeadDonor.collar(player, held, hand, host, d.head);
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    /**
     * A collar or a jarred head used on a creature that is not a host: the collar fits a host, a head goes on one. The
     * jar of someone riding a host is part of that host (it is what one clicks): the host is taken.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void notAHost(PlayerInteractEvent.EntityInteract event) {
        ItemStack held = event.getItemStack();
        boolean collar = held.is(SecondRegistry.SECONDMENT_COLLAR.get());
        if (!collar && !held.is(ModRegistry.JARRED_HEAD.get())) return;
        Entity target = com.coolfreeze23.headinajar.host.HostEvents.whole(event.getTarget());
        if (Hosts.isHost(target) || !(target instanceof LivingEntity)) return;
        if (FormEvents.lockedOut(event.getEntity())) return;
        Mob ridden = target instanceof Player rider && rider.getVehicle() instanceof Mob m && Hosts.isHost(m) ? m : null;
        // a head on a player or an armour stand is theirs to answer
        if (!collar && ridden == null && !(target instanceof Mob)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ridden != null) {
            refuse(player, ridden, "secondment.ridden");
        } else if (collar) {
            Refusals.deny(player, target instanceof Mob m ? com.coolfreeze23.headinajar.staff.StaffShake.at(m) : Refuser.at(target),
                    "secondment.not_host", Component.translatable(ModRegistry.SURGICAL_SAW.get().getDescriptionId()));
        } else {
            Refusals.deny(player, com.coolfreeze23.headinajar.staff.StaffShake.at((Mob) target), "host.not_a_host",
                    held.getHoverName(), Component.translatable(ModRegistry.SURGICAL_SAW.get().getDescriptionId()));
        }
    }

    // --- fitting the collar -----------------------------------------------------------------------------------------

    static void fit(ServerPlayer player, ItemStack held, Mob host, SecondeeData d) {
        if (!Secondees.enabled()) {
            refuse(player, host, "secondment.disabled");
            return;
        }
        if (host instanceof MechaEntity || host instanceof ShellEntity || Hosts.data(host).shell) {
            refuse(player, host, "secondment.machine");
            return;
        }
        if (Hosts.pilot(host) != null || host.isVehicle()) {
            refuse(player, host, "secondment.ridden");
            return;
        }
        if (d != null) {
            refuse(player, host, "secondment.already");
            return;
        }
        StaffFile body = Staff.of(host);
        UUID owner = body == null ? null : body.employer;
        if (owner != null && !mayManage(player, owner)) {
            refuse(player, host, "secondment.others", host.getDisplayName(), Staff.employer(body));
            return;
        }
        SecondeeData fitted = new SecondeeData();
        fitted.collar = true;
        fitted.employer = owner != null ? owner : player.getUUID();
        if (body != null && body.employer == null) {
            Staff.employ(body, player);
            host.setData(ModRegistry.STAFF, body);
        }
        host.setData(SecondRegistry.SECONDEE, fitted);
        held.consume(1, player);
        ServerLevel level = player.serverLevel();
        level.playSound(null, host.getX(), host.getY() + Hosts.neck(host).y, host.getZ(), SecondRegistry.COLLAR_FITTED.get(), SoundSource.NEUTRAL, 1, 1);
        player.displayClientMessage(Component.translatable("message.headinajar.secondment.fitted"), true);
        player.swing(InteractionHand.MAIN_HAND, true);
        SecondeeSync.changed(host);
    }

    /** Shears on an empty collared host: the collar comes off (with what was in its canister) and goes to the player. */
    static void collarOff(ServerPlayer player, Mob host, SecondeeData d) {
        if (!mayManage(player, d.employer)) {
            refuse(player, host, "secondment.others", host.getDisplayName(), employerName(player.serverLevel(), host, d));
            return;
        }
        host.removeData(SecondRegistry.SECONDEE);
        FormActions.give(player, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()));
        player.serverLevel().playSound(null, host.getX(), host.getY() + Hosts.neck(host).y, host.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.8f, 1.3f);
        player.displayClientMessage(Component.translatable("message.headinajar.secondment.collar_off"), true);
        SecondeeSync.changed(host);
    }

    // --- seating a head ---------------------------------------------------------------------------------------------

    /** Why this head may not be seated on this host by this player (a refusal key and its arguments), or null. */
    public static Object[] seatRefusal(ServerPlayer player, ItemStack head, Mob host, SecondeeData d) {
        if (!Secondees.enabled()) return new Object[]{"secondment.disabled"};
        if (host instanceof MechaEntity || host instanceof ShellEntity || Hosts.data(host).shell) return new Object[]{"secondment.machine"};
        if (Hosts.pilot(host) != null || host.isVehicle()) return new Object[]{"secondment.ridden"};
        if (d == null) return new Object[]{"secondment.no_collar", Component.translatable(SecondRegistry.SECONDMENT_COLLAR.get().getDescriptionId())};
        if (d.seated()) return new Object[]{"secondment.second_head"};
        EntityType<?> type = JarredHeadItem.type(head);
        if (type == EntityType.ENDER_DRAGON) return new Object[]{"secondment.dragon"};
        if (type == EntityType.CREEPER) return new Object[]{"secondment.creeper"};
        if (type == null || WorkTable.of(type) == null) {
            return new Object[]{"lanyard.unknown", type == null ? Component.translatable("staff.headinajar.another_employer") : type.getDescription()};
        }
        if (!mayManage(player, d.employer)) return new Object[]{"secondment.others", host.getDisplayName(), employerName(player.serverLevel(), host, d)};
        StaffFile f = Staff.of(head);
        if (f != null && f.employer != null && !mayManage(player, f.employer)) return new Object[]{"secondment.others_head", Staff.employer(f)};
        UUID employer = d.employer;
        int max = Config.get(Config.MAX_PER_PLAYER, 24);
        int have = WorkerIndex.get(player.server).count(employer);
        if (have >= max) return new Object[]{"secondment.cap", have};
        return null;
    }

    static void seat(ServerPlayer player, ItemStack held, Mob host, SecondeeData d) {
        Object[] no = seatRefusal(player, held, host, d);
        if (no != null) {
            refuse(player, host, (String) no[0], java.util.Arrays.copyOfRange(no, 1, no.length));
            return;
        }
        ServerLevel level = player.serverLevel();
        long day = Staff.day(level);
        EntityType<?> type = JarredHeadItem.type(held);
        ItemStack head = held.copyWithCount(1);
        held.consume(1, player);
        // the head's file goes onto the host while it is seated (numbered now if it never was); the body's is kept
        StaffFile f = Staff.of(head);
        if (f == null) {
            f = Staff.create(level.getServer(), level, type);
            Staff.numbered(player, f);
        }
        if (f.employer == null) Staff.employ(f, player);
        boolean hadBody = f.history.stream().anyMatch(e -> e.k().equals("seconded"));
        f.note("seconded", day, host.getType().getDescriptionId());
        CustomData.update(DataComponents.CUSTOM_DATA, head, t -> {
            t.remove(Staff.ON_HEAD);
            d.glassMax = Secondees.glassFor(com.coolfreeze23.headinajar.block.HeadInfo.jar(t));
            d.glass = t.contains(GLASS) ? Math.min(t.getInt(GLASS), d.glassMax) : d.glassMax;
            t.remove(GLASS);
        });
        StaffFile body = Staff.of(host);
        d.bodyFile = body == null ? null : body.save();
        host.setData(ModRegistry.STAFF, f);
        d.head = head;
        if (d.teamSyncFor != f.number) {
            d.teamSync = 0;
            d.teamSyncFor = f.number;
        }
        Workforce.enlist(level, host, d.employer, type);
        if (hadBody) Workforce.sayNow(host, Component.translatable("message.headinajar.secondment.new_team"));
        com.coolfreeze23.headinajar.body.BodyServer.seat(host, type);
        player.displayClientMessage(Component.translatable("message.headinajar.secondment.locked"), true);
        player.swing(InteractionHand.MAIN_HAND, true);
        Advance.grant(player, "secondment");
        Memos.send(player, Memos.Memo.SECONDMENT);
        pairing(player, type, host.getType());
        SecondeeSync.changed(host);
    }

    /** DOLORES's remark on a famous pairing, once per player. */
    static void pairing(ServerPlayer player, EntityType<?> head, EntityType<?> body) {
        String pair = BuiltInRegistries.ENTITY_TYPE.getKey(head).getPath() + "_" + BuiltInRegistries.ENTITY_TYPE.getKey(body).getPath();
        int bit = PAIRS.indexOf(pair);
        if (bit < 0) return;
        CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        int heard = kept.getInt(PAIRINGS);
        if ((heard & (1 << bit)) != 0) return;
        kept.putInt(PAIRINGS, heard | (1 << bit));
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        player.sendSystemMessage(Component.translatable("memo.headinajar.from").withStyle(ChatFormatting.GOLD).append(" ")
                .append(Component.translatable("dolores.headinajar.pairing." + pair).withStyle(ChatFormatting.WHITE)));
        SAID.add(player.getUUID() + "|" + pair);
    }

    // --- unseating --------------------------------------------------------------------------------------------------

    static void unseat(ServerPlayer player, Mob host, SecondeeData d) {
        if (!mayManage(player, d.employer)) {
            refuse(player, host, "secondment.others", Workforce.name(host), employerName(player.serverLevel(), host, d));
            return;
        }
        Component name = Workforce.name(host);
        ItemStack head = takeHead(host, d, "unseconded");
        FormActions.give(player, head);
        com.coolfreeze23.headinajar.body.BodyServer.unseat(host);
        player.displayClientMessage(Component.translatable("message.headinajar.secondment.unseated", name), true);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /**
     * Takes the seated head off its collar and gives it back as its Jarred Head item: its file (with {@code history},
     * when not null, added) written back into it with the jar's glass, the host's own file put back, and the worker
     * record removed (carry set down, card returned, off its clock). The collar stays, with its team sync kept for this
     * head. Used by an unseat and by the Redundancy Package.
     */
    public static ItemStack takeHead(Mob host, SecondeeData d, String history) {
        ItemStack head = d.head.copy();
        StaffFile f = Staff.of(host);
        Workforce.discharge(host);
        if (f != null) {
            if (history != null && host.level() instanceof ServerLevel level) f.note(history, Staff.day(level), host.getType().getDescriptionId());
            Staff.write(head, f);
        }
        int glass = d.glass;
        CustomData.update(DataComponents.CUSTOM_DATA, head, t -> t.putInt(GLASS, glass));
        if (d.bodyFile != null) {
            StaffFile body = StaffFile.load(d.bodyFile);
            if (body != null) host.setData(ModRegistry.STAFF, body);
            else host.removeData(ModRegistry.STAFF);
        } else {
            host.removeData(ModRegistry.STAFF);
        }
        d.bodyFile = null;
        d.head = ItemStack.EMPTY;
        d.dryTicks = 0;
        SecondeeSync.changed(host);
        return head;
    }

    // --- through the collar -----------------------------------------------------------------------------------------

    static void topUp(ServerPlayer player, ItemStack held, Mob host, SecondeeData d) {
        d.canister = SecondeeData.CANISTER;
        held.consume(1, player);
        FormActions.give(player, new ItemStack(Items.BUCKET));
        player.serverLevel().playSound(null, host.getX(), host.getY() + Hosts.neck(host).y, host.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.displayClientMessage(Component.translatable("message.headinajar.secondment.topped", d.canister), true);
        SecondeeSync.changed(host);
    }

    static void patch(ServerPlayer player, ItemStack held, Mob host, SecondeeData d) {
        d.glass = Math.min(d.glassMax, d.glass + 5);
        held.consume(1, player);
        player.serverLevel().playSound(null, host.getX(), host.getY() + Hosts.neck(host).y, host.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.8f, 1.3f);
        player.displayClientMessage(Component.translatable("message.headinajar.secondment.patched", d.glass, d.glassMax), true);
        SecondeeSync.changed(host);
    }

    // --- who may ------------------------------------------------------------------------------------------------------

    /** The collar's (or the head's) employer, or someone they trust. */
    public static boolean mayManage(Player player, UUID owner) {
        return owner == null || owner.equals(player.getUUID()) || HeadRegistry.get(player).trusts(owner, player);
    }

    static Component employerName(ServerLevel level, Mob host, SecondeeData d) {
        StaffFile f = Staff.of(host);
        if (f != null && d.employer != null && d.employer.equals(f.employer)) return Staff.employer(f);
        ServerPlayer online = d.employer == null ? null : level.getServer().getPlayerList().getPlayer(d.employer);
        return online != null ? online.getDisplayName() : Component.translatable("staff.headinajar.another_employer");
    }

    private static InteractionResult refuse(ServerPlayer player, Mob host, String reason, Object... args) {
        // the host says no as a refused creature does: a seated head shakes in its jar, a body with none shakes itself
        if (player != null) Refusals.deny(player, com.coolfreeze23.headinajar.staff.StaffShake.at(host), reason, args);
        return InteractionResult.CONSUME;
    }
}
