package com.coolfreeze23.headinajar.clarity;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.client.ChargeHud;
import com.coolfreeze23.headinajar.item.ModuleItem;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Tooltips that say what a thing is for, then where it goes and which key works it (as the reader has bound it), with
 * the numbers under the sneak key. Numbers come from the same values the game uses; where those are the server's own
 * settings and this client doesn't have them (another player's server), the line is left out rather than guessed.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class Tooltips {
    private Tooltips() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ChargeHud.tick();
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tip = event.getToolTip();
        boolean server = Config.SPEC.isLoaded();
        if (stack.getItem() instanceof ModuleItem module) {
            ModuleType type = module.type();
            add(tip, Component.translatable("tooltip.headinajar.clarity.module.fit", Component.keybind("key.headinajar.jar_menu")));
            String keyed = switch (type) {
                case TELEKINESIS -> "key.headinajar.ability";
                case LACRIMAL_LASER -> "key.headinajar.secondary";
                case WHEELS -> "key.headinajar.drive";
                default -> null;
            };
            if (keyed != null) add(tip, Component.translatable("tooltip.headinajar.clarity.module." + type.id(), Component.keybind(keyed)));
            if (type == ModuleType.TELEKINESIS && server) {
                add(tip, Component.translatable("tooltip.headinajar.clarity.telekinesis.range", Config.TELEKINESIS_RANGE.get()));
            }
        } else if (stack.is(ModRegistry.SALINE_BOTTLE.get())) {
            add(tip, Component.translatable("tooltip.headinajar.clarity.saline", 250));
            if (server && Minecraft.getInstance().player != null) {
                int max = Minecraft.getInstance().player.getData(ModRegistry.JAR_STATE).maxSaline();
                long minutes = (long) max * Config.SALINE_DRAIN_SECONDS.get() / 60;
                add(tip, Component.translatable("tooltip.headinajar.clarity.saline.lasts", max, minutes / 60, minutes % 60));
            }
        } else if (stack.is(ModRegistry.GLASS_PATCH.get())) {
            add(tip, Component.translatable("tooltip.headinajar.clarity.patch", 5));
        } else if (stack.is(ModRegistry.REPLACEMENT_BLADE.get())) {
            add(tip, Component.translatable("tooltip.headinajar.clarity.blade", Component.translatable("block.headinajar.separation_chair")));
        }
    }

    private static void add(List<Component> tip, Component line) {
        tip.add(Math.min(tip.size(), 1 + ourLines(tip)), line.copy().withStyle(ChatFormatting.GRAY));
    }

    private static int ourLines(List<Component> tip) {
        int n = 0;
        for (int i = 1; i < tip.size(); i++) {
            if (tip.get(i).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && (t.getKey().startsWith("tooltip.headinajar.") || t.getKey().startsWith("module.headinajar."))) n = i;
        }
        return n;
    }
}
