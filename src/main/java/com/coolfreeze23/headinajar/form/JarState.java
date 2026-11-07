package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.item.ModuleItem;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the mod keeps on a player: the form, the jar's tier, fitted modules and charge,
 * and which Headless Body is theirs. Saved with the player; not copied on death.
 */
public final class JarState {
    public static final int MAX_SLOTS = 5;

    public static final Codec<JarState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Form.CODEC.optionalFieldOf("form", Form.BODY).forGetter(s -> s.form),
            Codec.INT.optionalFieldOf("slots", 3).forGetter(s -> s.slots),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("modules", List.of()).forGetter(s -> s.modules),
            Codec.INT.optionalFieldOf("charge", 0).forGetter(s -> s.charge),
            UUIDUtil.CODEC.optionalFieldOf("body").forGetter(s -> Optional.ofNullable(s.body))
    ).apply(i, JarState::new));

    /** What clients need to draw and predict a player: no body id. */
    public static final StreamCodec<RegistryFriendlyByteBuf, JarState> SYNC_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Form.values()[i], Form::ordinal), s -> s.form,
            ByteBufCodecs.VAR_INT, s -> s.slots,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, s -> s.modules,
            ByteBufCodecs.VAR_INT, s -> s.charge,
            ByteBufCodecs.VAR_INT, s -> s.heldEntity,
            JarState::fromSync);

    private Form form;
    private int slots;
    private final List<ItemStack> modules;
    private int charge;
    private UUID body;
    /** Entity id held by Telekinesis, -1 for none. Not saved. */
    private int heldEntity = -1;
    private boolean dirty;
    private boolean chargeDirty;

    public JarState() {
        this(Form.BODY, 3, List.of(), 0, Optional.empty());
    }

    private JarState(Form form, int slots, List<ItemStack> modules, int charge, Optional<UUID> body) {
        this.form = form;
        this.slots = Math.max(1, Math.min(MAX_SLOTS, slots));
        this.modules = new ArrayList<>(modules);
        while (this.modules.size() < MAX_SLOTS) this.modules.add(ItemStack.EMPTY);
        this.charge = charge;
        this.body = body.orElse(null);
    }

    private static JarState fromSync(Form form, int slots, List<ItemStack> modules, int charge, int held) {
        JarState s = new JarState(form, slots, modules, charge, Optional.empty());
        s.heldEntity = held;
        return s;
    }

    /** Copies what the server sent into the client's copy. */
    public void copyFrom(JarState other) {
        form = other.form;
        slots = other.slots;
        for (int i = 0; i < MAX_SLOTS; i++) modules.set(i, other.modules.get(i));
        charge = other.charge;
        heldEntity = other.heldEntity;
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

    public static int maxCharge() {
        return Config.SPEC.isLoaded() ? Config.MAX_CHARGE.get() : 1000;
    }

    public void setCharge(int charge) {
        int c = Math.max(0, Math.min(maxCharge(), charge));
        if (c != this.charge) {
            this.charge = c;
            chargeDirty = true;
        }
    }

    /** Spends charge if there is any; returns whether the action is powered. */
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

    public int heldEntity() {
        return heldEntity;
    }

    public void setHeldEntity(int id) {
        if (id != heldEntity) {
            heldEntity = id;
            dirty = true;
        }
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
