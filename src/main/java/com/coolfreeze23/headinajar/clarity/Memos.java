package com.coolfreeze23.headinajar.clarity;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.Handbook;
import com.coolfreeze23.headinajar.module.ModuleType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * DOLORES's pager: six memos, each sent once to each player the first time the moment it is about happens (the
 * moments a new employee is most likely to be lost in, or about to lose something). A memo is one chat line with a link
 * to the handbook entry that says more; the reader's client plays one beep for it, and can switch memos off
 * ({@code memos} in the client config). Which memos a player has had is kept in the part of their data that survives
 * death, so a memo is never repeated.
 */
public final class Memos {
    /** Remembered with the handbook flag: a bit per memo. */
    public static final String SENT = "headinajar_memos";

    /** The memos, in bit order (never reorder: the bits are saved). */
    public enum Memo {
        NO_HANDS("jar/no_hands"),
        HEAD_LEFT("life_support/saline"),
        CHARGE_LOW("jar/charge"),
        HEAD_DRYING("separation/when_it_goes_wrong"),
        FIRST_HOST("staffing/riding"),
        MECHA_BUILT("mecha/piloting"),
        WELCOME("orientation/welcome");

        public final String entry;

        Memo(String entry) {
            this.entry = entry;
        }

        public String key() {
            return "memo.headinajar." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** What was sent to whom, for tests (newest last). */
    private static final List<String> HISTORY = new ArrayList<>();

    private Memos() {
    }

    static void check(ServerPlayer player, JarState state, boolean justAssembled) {
        Form form = state.form();
        if (form == Form.JAR && !state.has(ModuleType.MANIPULATOR_ARM)) {
            send(player, Memo.NO_HANDS, Component.keybind("key.headinajar.jar_menu"),
                    Component.translatable("item.headinajar.manipulator_arm"));
        }
        if (form == Form.LINKED && !state.carryingHead() && state.headAt() != JarState.HEAD_UNKNOWN) {
            send(player, Memo.HEAD_LEFT, Component.translatable("item.headinajar.head_satchel"), Component.keybind("key.headinajar.swap"));
            if (state.headAt() == JarState.HEAD_PLACED && state.saline() * 4 < state.maxSaline()) {
                send(player, Memo.HEAD_DRYING, Component.translatable("item.headinajar.saline_bottle"),
                        Component.translatable("item.headinajar.head_satchel"));
            }
        }
        // a new employee: handed the first-day handbook with no history here, still whole, no steel made yet (a veteran
        // of an upgraded world, or a player who already had the book, is never welcomed as new)
        if (form == Form.BODY && Handbook.newEmployee(player)) {
            var steel = player.server.getAdvancements().get(HeadInAJar.id("steel"));
            if (steel != null && !player.getAdvancements().getOrStartProgress(steel).isDone()) send(player, Memo.WELCOME);
        }
        if (form.isJarred() && state.charge() * 4 < state.chargeCapacity()) {
            send(player, Memo.CHARGE_LOW, Component.translatable("block.headinajar.charging_dock"),
                    Component.translatable("block.headinajar.life_support_cradle"));
        }
        // the Gantry has just built this player's first mecha (seen as its advancement being earned since the last look)
        if (justAssembled) {
            send(player, Memo.MECHA_BUILT, Component.keybind("key.headinajar.swap"), Component.keybind("key.sneak"),
                    Component.keybind("key.headinajar.jar_menu"));
        }
        if (form == Form.HOSTED && player.getVehicle() != null && Hosts.isHost(player.getVehicle()) && !Hosts.data(player.getVehicle()).shell) {
            send(player, Memo.FIRST_HOST, Component.keybind("key.headinajar.ability"), Component.keybind("key.sneak"));
        }
    }


    /** Whether a player has had a memo. */
    public static boolean sent(Player player, Memo memo) {
        return (player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(SENT) & (1 << memo.ordinal())) != 0;
    }

    /** Sends a memo if this player has never had it. Returns whether it was sent. */
    public static boolean send(ServerPlayer player, Memo memo, Object... args) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer || sent(player, memo)) return false;
        CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        kept.putInt(SENT, kept.getInt(SENT) | (1 << memo.ordinal()));
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        player.sendSystemMessage(line(memo, args));
        HISTORY.add(player.getUUID() + ":" + memo.name());
        return true;
    }

    /** The memo as its reader sees it: who it is from, the text, and the handbook entry that says more. */
    public static MutableComponent line(Memo memo, Object... args) {
        MutableComponent text = Component.translatable("memo.headinajar.from").withStyle(ChatFormatting.GOLD)
                .append(" ").append(Component.translatable(memo.key(), args).withStyle(ChatFormatting.WHITE));
        if (Handbook.patchouli()) {
            String target = HeadInAJar.MOD_ID + ":" + memo.entry;
            text.append(" ").append(Component.translatable("memo.headinajar.link").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + MemoClient.COMMAND + " " + target))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("memo.headinajar.link.hover")))));
        }
        return text;
    }

    public static List<String> history() {
        return HISTORY;
    }
}
