package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.item.ModuleItem;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the mod keeps on a player: the form, the jar's tier, fitted modules and charge (FE),
 * the Relay Goggles and Collar Cam, and which Headless Body is theirs. Saved with the player; not copied on death.
 */
public final class JarState {
    public static final int MAX_SLOTS = 7;

    public static final Codec<JarState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Form.CODEC.optionalFieldOf("form", Form.BODY).forGetter(s -> s.form),
            Codec.INT.optionalFieldOf("slots", 3).forGetter(s -> s.slots),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("modules", List.of()).forGetter(s -> s.modules),
            Codec.INT.optionalFieldOf("charge", 0).forGetter(s -> s.charge),
            UUIDUtil.CODEC.optionalFieldOf("body").forGetter(s -> Optional.ofNullable(s.body)),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("goggles", ItemStack.EMPTY).forGetter(s -> s.goggles),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("collar", ItemStack.EMPTY).forGetter(s -> s.collar),
            Codec.BOOL.optionalFieldOf("carrying_head", false).forGetter(s -> s.carryingHead),
            Codec.INT.optionalFieldOf("roll_ticks", 0).forGetter(s -> s.rollTicks),
            Codec.INT.optionalFieldOf("saline", 0).forGetter(s -> s.saline),
            Codec.INT.optionalFieldOf("integrity", 10).forGetter(s -> s.integrity),
            Codec.BOOL.optionalFieldOf("compact", false).forGetter(s -> s.compact)
    ).apply(i, JarState::new));

    /** What clients need to draw and predict a player: no body id. */
    public static final StreamCodec<RegistryFriendlyByteBuf, JarState> SYNC_CODEC = StreamCodec.of((buf, s) -> {
        buf.writeVarInt(s.form.ordinal());
        buf.writeVarInt(s.slots);
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, s.modules);
        buf.writeVarInt(s.charge);
        buf.writeVarInt(s.heldEntity);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.goggles);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.collar);
        buf.writeBoolean(s.carryingHead);
        buf.writeVarInt(s.rollTicks);
        buf.writeByte(s.quality);
        buf.writeFloat(s.headDistance);
        buf.writeVarInt(s.saline);
        buf.writeVarInt(s.integrity);
        buf.writeFloat(s.bodyHealth);
        buf.writeFloat(s.bodyMaxHealth);
        buf.writeUtf(s.speech);
        buf.writeBoolean(s.wheels);
        buf.writeBoolean(s.tipped);
        buf.writeBoolean(s.compact);
        buf.writeByte(s.headAt);
    }, buf -> {
        JarState s = new JarState(Form.values()[buf.readVarInt()], buf.readVarInt(), ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf),
                buf.readVarInt(), Optional.empty(), ItemStack.EMPTY, ItemStack.EMPTY, false, 0, 0, 10, false);
        s.heldEntity = buf.readVarInt();
        s.goggles = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        s.collar = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        s.carryingHead = buf.readBoolean();
        s.rollTicks = buf.readVarInt();
        s.quality = buf.readByte();
        s.headDistance = buf.readFloat();
        s.saline = buf.readVarInt();
        s.integrity = buf.readVarInt();
        s.bodyHealth = buf.readFloat();
        s.bodyMaxHealth = buf.readFloat();
        s.speech = buf.readUtf();
        s.wheels = buf.readBoolean();
        s.tipped = buf.readBoolean();
        s.compact = buf.readBoolean();
        s.headAt = buf.readByte();
        return s;
    });

    private Form form;
    private int slots;
    private final List<ItemStack> modules;
    private int charge;
    private UUID body;
    private ItemStack goggles;
    private ItemStack collar;
    /** The player carries their own head (Head Satchel); the link is perfect. */
    private boolean carryingHead;
    /** Ticks left of the botched-cut upside-down view. */
    private int rollTicks;
    /** Cranial Saline in the jar, mB. */
    private int saline;
    /** Jar Integrity: the glass's own health pool, taken before the player's. */
    private int integrity;
    /** The idle body's health while the player is the jar (Life Sign Monitor). Not saved. */
    private float bodyHealth, bodyMaxHealth;
    /** The last thing the jar said through its Speaker Grille. Not saved. */
    private String speech = "";
    /** Wheels module deployed, or the bare jar tipped on its side to roll: how a jar gets about without legs. */
    private boolean wheels, tipped;
    /** The head is in a Compact Jar: head-sized, the head facing its front. Saved. */
    private boolean compact;
    private long speechUntil;

    /** Entity id held by Telekinesis, -1 for none. Not saved. */
    private int heldEntity = -1;
    /** Neuro-link quality 0-100, server computed. Not saved. */
    private int quality = 100;
    /** Blocks between body and head, for the HUD (-1 = other dimension). Not saved. */
    private float headDistance;
    /** Where a linked body's head is, for the HUD: one of the HEAD_ values. Server computed, not saved. */
    private int headAt;
    public static final int HEAD_UNKNOWN = 0, HEAD_PLACED = 1, HEAD_DOCKED = 2;
    /** Ticks the link has been at zero. Not saved. */
    private int deadLinkTicks;
    private boolean dirty;
    private boolean chargeDirty;

    public JarState() {
        this(Form.BODY, 3, List.of(), 0, Optional.empty(), ItemStack.EMPTY, ItemStack.EMPTY, false, 0, 0, 10, false);
    }

    private JarState(Form form, int slots, List<ItemStack> modules, int charge, Optional<UUID> body,
                     ItemStack goggles, ItemStack collar, boolean carryingHead, int rollTicks, int saline, int integrity, boolean compact) {
        this.form = form;
        this.slots = Math.max(1, Math.min(MAX_SLOTS, slots));
        this.modules = new ArrayList<>(modules);
        while (this.modules.size() < MAX_SLOTS) this.modules.add(ItemStack.EMPTY);
        this.charge = charge;
        this.body = body.orElse(null);
        this.goggles = goggles;
        this.collar = collar;
        this.carryingHead = carryingHead;
        this.rollTicks = rollTicks;
        this.saline = saline;
        this.integrity = integrity;
        this.compact = compact;
    }

    /** Copies what the server sent into the client's copy. */
    public void copyFrom(JarState other) {
        form = other.form;
        slots = other.slots;
        for (int i = 0; i < MAX_SLOTS; i++) modules.set(i, other.modules.get(i));
        charge = other.charge;
        heldEntity = other.heldEntity;
        goggles = other.goggles;
        collar = other.collar;
        carryingHead = other.carryingHead;
        rollTicks = other.rollTicks;
        quality = other.quality;
        headDistance = other.headDistance;
        saline = other.saline;
        integrity = other.integrity;
        bodyHealth = other.bodyHealth;
        bodyMaxHealth = other.bodyMaxHealth;
        if (!other.speech.isEmpty() && !other.speech.equals(speech)) speechUntil = System.currentTimeMillis() + 5000;
        speech = other.speech;
        wheels = other.wheels;
        tipped = other.tipped;
        compact = other.compact;
        headAt = other.headAt;
    }

    public boolean compact() {
        return compact;
    }

    public void setCompact(boolean compact) {
        this.compact = compact;
        dirty = true;
    }

    public boolean wheels() {
        return wheels;
    }

    public void setWheels(boolean wheels) {
        this.wheels = wheels;
    }

    public boolean tipped() {
        return tipped;
    }

    public void setTipped(boolean tipped) {
        this.tipped = tipped;
    }

    // --- life support (P2) -------------------------------------------------------------------------

    /** Saline capacity of this jar, by tier. */
    public int maxSaline() {
        return slots >= 7 ? 2000 : slots >= 5 ? 1500 : 1000;
    }

    /** Integrity capacity of this jar, by tier. */
    public int maxIntegrity() {
        return slots >= 7 ? 30 : slots >= 5 ? 20 : 10;
    }

    public int saline() {
        return saline;
    }

    public void setSaline(int saline) {
        int v = Math.max(0, Math.min(maxSaline(), saline));
        if (v != this.saline) {
            this.saline = v;
            chargeDirty = true;
        }
    }

    public int integrity() {
        return integrity;
    }

    public void setIntegrity(int integrity) {
        int v = Math.max(0, Math.min(maxIntegrity(), integrity));
        if (v != this.integrity) {
            this.integrity = v;
            dirty = true;
        }
    }

    /** Fills a fresh jar: full saline and integrity. */
    public void fillJar() {
        saline = maxSaline();
        integrity = maxIntegrity();
        dirty = true;
    }

    public float bodyHealth() {
        return bodyHealth;
    }

    public float bodyMaxHealth() {
        return bodyMaxHealth;
    }

    public void setBodyHealth(float health, float max) {
        if (Math.abs(health - bodyHealth) >= 0.5f || max != bodyMaxHealth) {
            bodyHealth = health;
            bodyMaxHealth = max;
            dirty = true;
        }
    }

    public String speech() {
        return System.currentTimeMillis() < speechUntil ? speech : "";
    }

    public void say(String text) {
        speech = text.length() > 60 ? text.substring(0, 60) + "..." : text;
        dirty = true;
    }

    public Form form() {
        return form;
    }

    public void setForm(Form form) {
        this.form = form;
        dirty = true;
    }

    public int slots() {
        return slots;
    }

    public void setSlots(int slots) {
        this.slots = Math.max(1, Math.min(MAX_SLOTS, slots));
        dirty = true;
    }

    public ItemStack module(int slot) {
        return modules.get(slot);
    }

    public void setModule(int slot, ItemStack stack) {
        modules.set(slot, stack);
        dirty = true;
    }

    public List<ItemStack> modules() {
        return modules;
    }

    /** Whether a module of this type is fitted in an active slot, and powered if it needs power. */
    public boolean has(ModuleType type) {
        if (!form.isJarred()) return false;
        if (type.powered && charge <= 0) return false;
        return fitted(type);
    }

    /** Whether a module of this type is fitted in an active slot, powered or not. */
    public boolean fitted(ModuleType type) {
        for (int i = 0; i < slots; i++) {
            if (modules.get(i).getItem() instanceof ModuleItem m && m.type() == type) return true;
        }
        return false;
    }

    public int charge() {
        return charge;
    }

    /** What a basic Life Support Jar (or a Compact Jar) holds: the config's charge. */
    public static int maxCharge() {
        return Config.SPEC.isLoaded() ? Config.MAX_CHARGE.get() : 100_000;
    }

    /** Charge capacity of this jar, by tier: basic and Compact 1x, Reinforced 2.5x, Executive 5x the base (100k, 250k, 500k). */
    public int chargeCapacity() {
        return capacityFor(slots);
    }

    /** Charge capacity of a jar with this many module slots (its tier). */
    public static int capacityFor(int slots) {
        long base = maxCharge();
        return (int) Math.min(Integer.MAX_VALUE, slots >= 7 ? base * 5 : slots >= 5 ? base * 5 / 2 : base);
    }

    public void setCharge(int charge) {
        int c = Math.max(0, Math.min(chargeCapacity(), charge));
        if (c != this.charge) {
            this.charge = c;
            chargeDirty = true;
        }
    }

    /** Spends charge (FE) if there is any; returns whether the action is powered. */
    public boolean spend(int amount) {
        if (charge <= 0) return false;
        setCharge(charge - amount);
        return true;
    }

    public UUID body() {
        return body;
    }

    public void setBody(UUID body) {
        this.body = body;
        dirty = true;
    }

    public ItemStack goggles() {
        return goggles;
    }

    public void setGoggles(ItemStack goggles) {
        this.goggles = goggles;
        dirty = true;
    }

    public ItemStack collar() {
        return collar;
    }

    public void setCollar(ItemStack collar) {
        this.collar = collar;
        dirty = true;
    }

    public boolean carryingHead() {
        return carryingHead;
    }

    public void setCarryingHead(boolean carrying) {
        this.carryingHead = carrying;
        dirty = true;
    }

    public int rollTicks() {
        return rollTicks;
    }

    public void setRollTicks(int ticks) {
        if (ticks != rollTicks) {
            rollTicks = ticks;
            dirty = true;
        }
    }

    /** Counts the roll down on both sides without a sync each tick. */
    public void tickRoll() {
        if (rollTicks > 0) rollTicks--;
    }

    public int heldEntity() {
        return heldEntity;
    }

    public void setHeldEntity(int id) {
        if (id != heldEntity) {
            heldEntity = id;
            dirty = true;
        }
    }

    public int quality() {
        return quality;
    }

    public float headDistance() {
        return headDistance;
    }

    public void setLink(int quality, float distance) {
        if (quality != this.quality || Math.abs(distance - headDistance) >= 1) {
            this.quality = quality;
            this.headDistance = distance;
            dirty = true;
        }
    }

    public int headAt() {
        return headAt;
    }

    public void setHeadAt(int where) {
        if (where != headAt) {
            headAt = where;
            dirty = true;
        }
    }

    public int deadLinkTicks() {
        return deadLinkTicks;
    }

    public void setDeadLinkTicks(int ticks) {
        deadLinkTicks = ticks;
    }

    /** Whether the form or modules changed since the last sync; charge-only changes count when {@code chargeToo}. */
    public boolean takeDirty(boolean chargeToo) {
        boolean d = dirty || chargeToo && chargeDirty;
        if (d) dirty = chargeDirty = false;
        return d;
    }

    public void markDirty() {
        dirty = true;
    }
}
