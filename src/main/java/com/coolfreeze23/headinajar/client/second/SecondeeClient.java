package com.coolfreeze23.headinajar.client.second;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.HeadInfo;
import com.coolfreeze23.headinajar.staff.second.SecondeeSync;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * On the client: the collared hosts this game has been told of (entity id to what sits on the neck), filled by the
 * {@code headinajar:secondee} payload and read by {@link SecondeeLayer} and Jade. Forgotten on logout, on a change of
 * level (a dimension change or a new world) and when the host leaves this game's view. A team-sync quarter going up
 * runs the teal ring down the body, as a player's sync does.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class SecondeeClient {
    /** A collared host as this client knows it. */
    public static final class Seen {
        public boolean seated, dry;
        /** The canister's sight glass (0 full to 3 empty), the jar's glass stage (0-3), team sync in quarters (0-4). */
        public int canister, glass, quarter;
        /** The seated head (null: an empty collar), its villager data (or null), slime size, jar and its shape. */
        public EntityType<?> head;
        public CompoundTag villager;
        public int size;
        public String jar = HeadInfo.LIFE_SUPPORT;
        public boolean compact;
    }

    private static final Int2ObjectMap<Seen> SEEN = new Int2ObjectOpenHashMap<>();
    private static ClientLevel heardIn;

    private SecondeeClient() {
    }

    /** Forgets everything when the level is not the one it was heard in. */
    private static void check() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != heardIn) {
            SEEN.clear();
            heardIn = level;
        }
    }

    /** From the server: what sits on this host's neck now (no collar: forgotten). */
    public static void view(SecondeeSync.View msg) {
        check();
        if (!msg.collar()) {
            SEEN.remove(msg.entity());
            return;
        }
        Seen before = SEEN.get(msg.entity());
        int wasQuarter = before == null || !before.seated ? -1 : before.quarter;
        Seen s = new Seen();
        s.seated = msg.seated();
        s.dry = msg.dry();
        s.canister = msg.canister();
        s.glass = msg.glass();
        s.quarter = msg.quarter();
        ResourceLocation id = msg.head().isEmpty() ? null : ResourceLocation.tryParse(msg.head());
        s.head = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        s.villager = msg.villager().isEmpty() ? null : msg.villager();
        s.size = msg.size();
        if (!msg.jar().isEmpty()) s.jar = msg.jar();
        s.compact = HeadInfo.COMPACT.equals(s.jar);
        if (s.head == null) s.seated = false;
        SEEN.put(msg.entity(), s);
        // team sync arriving in quarters: the teal ring runs down the body (a larger pale one at 100)
        if (wasQuarter >= 0 && s.seated && s.quarter > wasQuarter && heardIn != null
                && heardIn.getEntity(msg.entity()) instanceof Mob host) {
            com.coolfreeze23.headinajar.client.body.HostFx.syncPulse(host, s.quarter >= 4);
        }
    }

    /** What sits on this creature's neck, or null (not a collared host, as far as this client was told). */
    public static Seen of(Entity e) {
        if (SEEN.isEmpty() || e.level() != heardIn) return null;
        return SEEN.get(e.getId());
    }

    /** Wears a collar (with or without a head), as this client was told. */
    public static boolean collared(Entity e) {
        return of(e) != null;
    }

    /** A head is seated on its collar, as this client was told. */
    public static boolean seated(Entity e) {
        Seen s = of(e);
        return s != null && s.seated;
    }

    /** For the harness: how many collared hosts this client knows. */
    public static int count() {
        return SEEN.size();
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SEEN.clear();
        heardIn = null;
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getLevel() == heardIn) SEEN.remove(event.getEntity().getId());
    }
}
