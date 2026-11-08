package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/** The genetics department's hand tools and the things they make. */
public final class GeneticsItems {
    private GeneticsItems() {
    }

    // --- samples ---------------------------------------------------------------------------------

    public static ItemStack sample(Item item, EntityType<?> species, int quality) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString("Species", BuiltInRegistries.ENTITY_TYPE.getKey(species).toString());
        tag.putInt("Quality", quality);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    public static EntityType<?> species(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(data.copyTag().getString("Species"));
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    public static int quality(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt("Quality");
    }

    /** A Blood Sample or DNA Fraction: shows its creature and quality. */
    public static class SampleItem extends Item {
        public SampleItem(Properties properties) {
            super(properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            EntityType<?> type = species(stack);
            return type == null ? super.getName(stack) : Component.translatable(getDescriptionId() + ".of", type.getDescription());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            EntityType<?> type = species(stack);
            if (type == null) return;
            tooltip.add(Component.translatable("item.headinajar.sample.quality", quality(stack)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("blood.headinajar." + BloodType.of(type).name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_RED));
            List<Gene> genes = Gene.of(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            if (!genes.isEmpty()) {
                tooltip.add(Component.translatable("item.headinajar.sample.genes", genes.size()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    // --- collecting --------------------------------------------------------------------------------

    /** A syringe or bloodless-collection tool: blood (or marrow, oil, ecto) from a living creature. */
    public static class SyringeItem extends Item {
        private final BloodType kind;
        private final int min, max;
        private final boolean harmless, bosses;

        /** {@code kind} WARM means a normal blood syringe (any non-bloodless type); MARROW, OIL, ECTO are the bloodless tools. */
        public SyringeItem(BloodType kind, int min, int max, boolean harmless, boolean bosses, Properties properties) {
            super(properties);
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.harmless = harmless;
            this.bosses = bosses;
        }

        /** Draws a sample from {@code target}. Returns the result to report. */
        public InteractionResult draw(ServerPlayer player, ItemStack stack, LivingEntity target, InteractionHand hand) {
            if (target instanceof Player) return InteractionResult.PASS;
            BloodType blood = BloodType.of(target);
            boolean right = kind.bloodless ? blood == kind : !blood.bloodless;
            if (!right) {
                player.displayClientMessage(Component.translatable(blood.bloodless ? "message.headinajar.bloodless" : "message.headinajar.wrong_tool"), true);
                return InteractionResult.FAIL;
            }
            if (target.getType().is(BloodType.THICK_HIDE) && !bosses) {
                player.displayClientMessage(Component.translatable("message.headinajar.boss_hide"), true);
                return InteractionResult.FAIL;
            }
            int quality = quality(target, min + player.getRandom().nextInt(max - min + 1));
            if (!harmless) target.hurt(player.damageSources().playerAttack(player), 1);
            com.coolfreeze23.headinajar.form.FormActions.give(player, sample(ModRegistry.BLOOD_SAMPLE.get(), target.getType(), quality));
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.BEE_STING, target.getSoundSource(), 0.8f, 1.4f);
            return InteractionResult.CONSUME;
        }

        /** A healthy, calm creature gives better blood. */
        public static int quality(LivingEntity target, int base) {
            float health = target.getHealth() / target.getMaxHealth();
            float factor = 0.5f + 0.5f * Math.max(0, Math.min(1, (health - 0.1f) / 0.9f));
            float q = base * factor;
            if (target.hasEffect(MobEffects.POISON) || target.hasEffect(MobEffects.WITHER) || target.isOnFire()) q -= 20;
            return Math.max(1, Math.min(100, Math.round(q)));
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.headinajar.syringe.quality", min, max).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(kind.bloodless ? "item.headinajar.syringe.bloodless" : "item.headinajar.syringe.how",
                    Component.translatable("blood.headinajar." + kind.name().toLowerCase(java.util.Locale.ROOT))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // --- serums ------------------------------------------------------------------------------------

    public static class SerumItem extends Item {
        public SerumItem(Properties properties) {
            super(properties);
        }

        /** Injects into a host mob (load 6, one serum) or a Chimera mecha's Gene Ports (load 8, three serums, never rejected). */
        public InteractionResult inject(ServerPlayer player, ItemStack stack, LivingEntity target) {
            var serum = Serum.of(stack);
            if (serum.isEmpty()) return InteractionResult.PASS;
            Genetics.Target kind;
            if (target instanceof com.coolfreeze23.headinajar.entity.MechaEntity mecha) {
                if (mecha.frame().genePorts() == 0) {
                    player.displayClientMessage(Component.translatable("message.headinajar.mecha_gene_port"), true);
                    return InteractionResult.FAIL;
                }
                if (!mecha.isOwnedBy(player)) {
                    player.displayClientMessage(Component.translatable("message.headinajar.not_your_mecha"), true);
                    return InteractionResult.FAIL;
                }
                kind = Genetics.Target.MECHA;
            } else if (target instanceof Mob host && Hosts.isHost(host)) {
                kind = Genetics.Target.HOST;
            } else {
                return InteractionResult.PASS;
            }
            Genetics.Result result = Genetics.inject(target, serum.get(), kind, player.getRandom());
            report(player, result);
            if (result == Genetics.Result.FULL || result == Genetics.Result.TOO_HEAVY) return InteractionResult.FAIL;
            stack.shrink(1);
            com.coolfreeze23.headinajar.form.FormActions.give(player, new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
            Network.syncGenes(target);
            return InteractionResult.CONSUME;
        }

        public static void report(ServerPlayer player, Genetics.Result result) {
            player.displayClientMessage(Component.translatable("message.headinajar.inject." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
            player.serverLevel().playSound(null, player.blockPosition(), result == Genetics.Result.REJECTED ? SoundEvents.SLIME_HURT : SoundEvents.BREWING_STAND_BREW,
                    player.getSoundSource(), 1, 1.2f);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            Serum.of(stack).ifPresent(serum -> {
                for (Serum.Entry e : serum.genes()) {
                    tooltip.add(Component.translatable("item.headinajar.serum.gene", Component.translatable("gene.headinajar." + e.gene().id()),
                            Math.round(e.potency() * 100)).withStyle(ChatFormatting.AQUA));
                }
                tooltip.add(Component.translatable("item.headinajar.serum.stats", serum.stability(), serum.load(),
                        Math.round(Genetics.mutationChance(serum.stability()) * 100)).withStyle(ChatFormatting.GRAY));
            });
            tooltip.add(Component.translatable("item.headinajar.serum.how").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Gene Wash: drink it to clear every gene and mutation from your own body. */
    public static class GeneWashItem extends Item {
        public GeneWashItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return ItemUtils.startUsingInstantly(level, player, hand);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (entity instanceof ServerPlayer player) {
                Form form = player.getData(ModRegistry.JAR_STATE).form();
                if (form == Form.BODY || form == Form.LINKED) {
                    Genetics.genes(player).clear();
                    Network.syncGenes(player);
                    player.displayClientMessage(Component.translatable("message.headinajar.washed"), true);
                }
                stack.consume(1, player);
            }
            return stack;
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return 32;
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.DRINK;
        }
    }
}
