package com.coolfreeze23.headinajar.clarity;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The reader's end of DOLORES's pager: a memo arriving beeps once (or is not shown at all with the {@code memos}
 * option off), and its handbook link opens the entry through a client command, so nothing about it reaches the server.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class MemoClient {
    /** The client command a memo's link runs: {@code /headinajar_handbook <entry>}. */
    public static final String COMMAND = "headinajar_handbook";
    private static final ResourceLocation BEEP = HeadInAJar.id("chair.beep");

    private MemoClient() {
    }

    /** Whether a chat line is one of DOLORES's memos. */
    public static boolean isMemo(Component line) {
        return line.getContents() instanceof TranslatableContents t && t.getKey().equals("memo.headinajar.from");
    }

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent.System event) {
        if (!isMemo(event.getMessage())) return;
        if (Config.CLIENT_SPEC.isLoaded() && !Config.MEMOS.get()) {
            event.setCanceled(true);
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(com.coolfreeze23.headinajar.fx.HouseSounds.MEMO_BEEP.get(), 1.0f, 0.8f));
    }

    private static boolean clashesTold;

    /**
     * Once a session, on joining a world: each of the mod's keys that another control also answers in play (another mod
     * may share it), named with both controls and where to rebind. The ability key and sprint are left out: that pair
     * has its own notice from the server, and may be meant.
     */
    @SubscribeEvent
    public static void joined(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        if (clashesTold) return;
        clashesTold = true;
        Minecraft mc = Minecraft.getInstance();
        for (var line : clashes(mc.options.keyMappings)) event.getPlayer().displayClientMessage(line, false);
    }

    /** The lines for every clash among these mappings that involves one of the mod's keys. */
    public static java.util.List<Component> clashes(net.minecraft.client.KeyMapping[] all) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        for (var mine : all) {
            if (!mine.getName().startsWith("key.headinajar.") || mine.isUnbound()) continue;
            for (var other : all) {
                if (other == mine || other.isUnbound() || !mine.same(other)) continue;
                if (!mine.getKeyConflictContext().conflicts(other.getKeyConflictContext())) continue;
                if (other.getName().startsWith("key.headinajar.")) continue;
                if (mine.getName().equals("key.headinajar.ability") && other.getName().equals("key.sprint")) continue;
                out.add(Component.translatable("message.headinajar.clarity.key_clash", Component.translatable(mine.getName()),
                        mine.getTranslatedKeyMessage(), Component.translatable(other.getName()),
                        Component.translatable("controls.title")).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND).then(Commands.argument("entry", StringArgumentType.greedyString())
                .executes(ctx -> {
                    ResourceLocation entry = ResourceLocation.tryParse(StringArgumentType.getString(ctx, "entry"));
                    if (entry != null && ModList.get().isLoaded("patchouli")) Minecraft.getInstance().execute(() -> PatchouliOpen.open(entry));
                    return 1;
                })));
    }

    /** Kept apart so no Patchouli class is loaded unless Patchouli is there. */
    static final class PatchouliOpen {
        static void open(ResourceLocation entry) {
            vazkii.patchouli.api.PatchouliAPI.get().openBookEntry(HeadInAJar.id("handbook"), entry, 0);
        }
    }
}
