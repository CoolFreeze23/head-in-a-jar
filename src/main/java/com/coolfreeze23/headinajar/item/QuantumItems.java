package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Collar Cam upgrades, the Entangled Pair and its shards. */
public final class QuantumItems {
    private QuantumItems() {
    }

    // --- Collar Cam upgrades ---------------------------------------------------------------------------------

    public enum Upgrade {
        WIDE_LENS, PERISCOPE_STALK, NIGHT_OPTICS, WATERPROOF_SEAL, SHIELDED_HOUSING, ANTENNA_WHIP, NOISE_FILTER, THERMAL_OVERLAY, ZOOM_OPTIC;

        public String id() {
            return "collar_" + name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int UPGRADE_SLOTS = 4;

    public static List<Upgrade> upgrades(ItemStack collar) {
        List<Upgrade> out = new ArrayList<>();
        CustomData data = collar.get(DataComponents.CUSTOM_DATA);
        if (data == null) return out;
        ListTag list = data.copyTag().getList("Upgrades", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                out.add(Upgrade.valueOf(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public static boolean has(ItemStack collar, Upgrade upgrade) {
        return !collar.isEmpty() && upgrades(collar).contains(upgrade);
    }

    /** Whether the player's fitted Collar Cam carries this upgrade. */
    public static boolean has(Player player, Upgrade upgrade) {
        return has(player.getData(ModRegistry.JAR_STATE).collar(), upgrade);
    }

    /** Fits an upgrade into a collar: false if it is full or already has one. */
    public static boolean install(ItemStack collar, Upgrade upgrade) {
        List<Upgrade> now = upgrades(collar);
        if (now.contains(upgrade) || now.size() >= UPGRADE_SLOTS) return false;
        CustomData.update(DataComponents.CUSTOM_DATA, collar, tag -> {
            ListTag list = tag.getList("Upgrades", Tag.TAG_STRING);
            list.add(StringTag.valueOf(upgrade.name()));
            tag.put("Upgrades", list);
        });
        return true;
    }

    /** An upgrade: use it to fit it into your own Collar Cam (fitted on your neck), or into a Collar Cam in your other hand. */
    public static class UpgradeItem extends Item {
        public final Upgrade upgrade;

        public UpgradeItem(Upgrade upgrade, Properties properties) {
            super(properties);
            this.upgrade = upgrade;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            JarState state = player.getData(ModRegistry.JAR_STATE);
            ItemStack collar = other.getItem() instanceof CollarCamItem ? other : state.form() != Form.BODY ? state.collar() : ItemStack.EMPTY;
            if (collar.isEmpty() || !install(collar, upgrade)) {
                player.displayClientMessage(Component.translatable("collar.headinajar.no_room"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (collar == state.collar() && player instanceof ServerPlayer sp) {
                state.setCollar(collar);
                Network.sync(sp);
            }
            stack.shrink(1);
            if (upgrade == Upgrade.PERISCOPE_STALK) com.coolfreeze23.headinajar.Advance.grant(player, "periscope");
            level.playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, player.getSoundSource(), 0.8f, 1.4f);
            player.displayClientMessage(Component.translatable("collar.headinajar.installed", Component.translatable(getDescriptionId())), true);
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.headinajar." + upgrade.id() + ".desc").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("collar.headinajar.how").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // --- Entangled Pair ---------------------------------------------------------------------------------------

    public static Optional<UUID> entangled(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        CompoundTag tag = data.copyTag();
        return tag.hasUUID("Entangled") ? Optional.of(tag.getUUID("Entangled")) : Optional.empty();
    }

    public static void entangle(ItemStack stack, UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID("Entangled", id));
    }

    /** Goggles and collar that share one entangled pair: the link crosses dimensions. */
    public static boolean linked(JarState state) {
        var a = entangled(state.goggles());
        return a.isPresent() && a.equals(entangled(state.collar()));
    }

    /** The Entangled Pair: use it to split it into two shards that share one state, one for the goggles, one for the collar. */
    public static class PairItem extends Item {
        public PairItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            UUID id = UUID.randomUUID();
            for (int i = 0; i < 2; i++) {
                ItemStack shard = new ItemStack(ModRegistry.ENTANGLED_SHARD.get());
                entangle(shard, id);
                if (player instanceof ServerPlayer sp) FormActions.give(sp, shard);
            }
            stack.shrink(1);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, player.getSoundSource(), 1, 0.6f);
            return InteractionResultHolder.consume(stack);
        }
    }

    /** One half of a pair. Craft it with Relay Goggles or a Collar Cam to entangle them. */
    public static class ShardItem extends Item {
        public ShardItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            entangled(stack).ifPresent(id -> tooltip.add(Component.translatable("item.headinajar.entangled_shard.id", id.toString().substring(0, 8))
                    .withStyle(ChatFormatting.LIGHT_PURPLE)));
            tooltip.add(Component.translatable("item.headinajar.entangled_shard.how").withStyle(ChatFormatting.DARK_GRAY));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }
    }
}
