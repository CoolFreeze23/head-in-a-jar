package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A jar of leeches for mob farms: every 30 s it takes a small, low-quality blood sample from a creature standing on or
 * beside it (never players, bosses or the bloodless). Holds nine samples; use it to empty it.
 */
public class LeechJarBlockEntity extends BlockEntity implements HasItems {
    public static final int INTERVAL = 600, MIN = 15, MAX = 35;
    private final ItemStackHandler items = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int timer;

    public LeechJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.LEECH_JAR_BE.get(), pos, state);
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LeechJarBlockEntity jar) {
        if (++jar.timer < INTERVAL) return;
        jar.timer = 0;
        jar.feed(level);
    }

    /** One feeding: returns whether a sample was taken. */
    public boolean feed(Level level) {
        AABB around = new AABB(worldPosition).inflate(0.6, 1, 0.6);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, around, e -> e.isAlive() && !(e instanceof Player)
                && !e.getType().is(BloodType.THICK_HIDE) && !BloodType.of(e).bloodless)) {
            int base = MIN + level.random.nextInt(MAX - MIN + 1);
            ItemStack sample = GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), e.getType(), GeneticsItems.SyringeItem.quality(e, base));
            for (int i = 0; i < items.getSlots(); i++) {
                if (items.getStackInSlot(i).isEmpty()) {
                    items.setStackInSlot(i, sample);
                    e.hurt(level.damageSources().cactus(), 0.5f);
                    level.playSound(null, worldPosition, SoundEvents.HONEY_DRINK, SoundSource.BLOCKS, 0.5f, 0.6f);
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    public void empty(Player player) {
        int n = 0;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            FormActions.give((ServerPlayer) player, stack.copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
            n++;
        }
        player.displayClientMessage(Component.translatable("message.headinajar.leech_jar", n), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Timer", timer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Items"));
        timer = tag.getInt("Timer");
    }
}
