package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.client.ChargeHud;
import com.coolfreeze23.headinajar.client.ChatRoom;
import com.coolfreeze23.headinajar.client.staff.PersonnelFileScreen;
import com.coolfreeze23.headinajar.staff.StaffNet;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

/**
 * Dev-only: the Personnel File and the chat over the jar's bars, without a server that fills cards in.
 * <ul>
 * <li>{@code card cow|villager|slime [norename]}: opens a full test card (eight history lines, a 16-character name, the
 * longest rows the file has), in English or, when the game's language is Portuguese, in Portuguese;</li>
 * <li>{@code chatlift}: reports the bars' place, the chat's right edge and the lift; {@code chatlift mouse} puts the
 * cursor on the middle of the lowest chat line where it is drawn; {@code chatlift off|on} the dev switch.</li>
 * </ul>
 */
final class StaffProbe {
    private StaffProbe() {
    }

    static String run(Minecraft mc, String[] parts) {
        String[] a = parts.length > 1 ? parts[1].trim().split("\\s+") : new String[0];
        return parts[0].equals("card") ? card(mc, a) : chatLift(mc, a);
    }

    private static String card(Minecraft mc, String[] a) {
        String kind = a.length > 0 ? a[0] : "cow";
        boolean rename = !(a.length > 1 && a[1].equals("norename"));
        boolean pt = mc.options.languageCode.startsWith("pt");
        CompoundTag tag = new CompoundTag();
        String name = "Sir Moosalot III";
        String species;
        switch (kind) {
            case "villager" -> {
                tag.putString("Type", "minecraft:villager");
                CompoundTag data = new CompoundTag();
                data.putString("type", "minecraft:plains");
                data.putString("profession", "minecraft:librarian");
                data.putInt("level", 2);
                tag.put("VillagerData", data);
                name = "Hrmm Bookwright";
                species = pt ? "Aldeão (Bibliotecário)" : "Villager (Librarian)";
            }
            case "slime" -> {
                tag.putString("Type", "minecraft:slime");
                tag.putInt("Size", 3);
                name = "Gloria Wobbleton";
                species = pt ? "Slime (tamanho 4)" : "Slime (size 4)";
            }
            default -> {
                tag.putString("Type", "minecraft:cow");
                species = pt ? "Vaca" : "Cow";
            }
        }
        tag.putString("Jar", "headinajar:life_support_jar");
        tag.putString("Title", json(mc, Component.literal(pt ? "FICHA FUNCIONAL  Nº 0042" : "PERSONNEL FILE  No. 0042")));
        String[][] rows = pt ? new String[][]{
                {"Nome", name}, {"Espécie", species},
                {"Departamento", "Laticínios e Reclamações: mantém o leite fluindo, quase sempre"},
                {"Empregador", "Dev"}, {"Admissão", "Dia 12 (31 dias de serviço)"},
                {"Moral", "Satisfeito"}, {"Salário", "1 Trigo por dia; sem pagamento há 3 dias, e o RH sabe"},
                {"Pote", "Pote de Suporte Vital"}, {"Genes", "Couro Grosso, Lactação, Teimosia, Mau Hálito"}}
                : new String[][]{
                {"Name", name}, {"Species", species},
                {"Department", "Dairy & Grievances: keeps the milk flowing, mostly"},
                {"Employer", "Dev"}, {"Joined", "Day 12 (31 days of service)"},
                {"Morale", "Content"}, {"Wage", "1 Wheat a day; unpaid for 3 days, and HR has noticed"},
                {"Jar", "Life Support Jar"}, {"Genes", "Thick Hide, Lactation, Stubbornness, Halitosis"}};
        ListTag list = new ListTag();
        for (String[] row : rows) {
            CompoundTag r = new CompoundTag();
            r.putString("label", json(mc, Component.literal(row[0])));
            r.putString("value", json(mc, Component.literal(row[1])));
            list.add(r);
        }
        tag.put("Rows", list);
        String[] history = pt ? new String[]{"Engarrafado no dia 12.", "Colocado por Dev no dia 12.", "Posto na folha por Dev no dia 13.",
                "Lotado no Pedestal de Torreta por Dev no dia 15.", "Renomeado por Dev no dia 16.", "Reclamou do plano dentário no dia 20.",
                "Formulário 66-G arquivado em triplicata no dia 21.", "Descontado um trigo por encarar a gerência no dia 30."}
                : new String[]{"Jarred day 12.", "Placed by Dev day 12.", "Put on the payroll by Dev day 13.",
                "Posted to the Turret Pedestal by Dev day 15.", "Renamed by Dev day 16.", "Complained about dental, day 20.",
                "Form 66-G filed in triplicate, day 21.", "Docked one wheat for staring at management, day 30."};
        ListTag lines = new ListTag();
        for (String line : history) lines.add(StringTag.valueOf(json(mc, Component.literal(line))));
        tag.put("History", lines);
        if (rename) tag.putString("Name", name);
        PersonnelFileScreen.open(new StaffNet.Card(mc.player.blockPosition(), tag, rename));
        var connection = mc.getConnection();
        return "card " + kind + (rename ? " (rename)" : " (read only)") + " -> " + (mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName())
                + ", " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + ", rename channel "
                + (connection != null && connection.hasChannel(StaffNet.Rename.TYPE) ? "present" : "absent");
    }

    private static String json(Minecraft mc, Component c) {
        return Component.Serializer.toJson(c, mc.level.registryAccess());
    }

    private static String chatLift(Minecraft mc, String[] a) {
        if (a.length > 0 && (a[0].equals("off") || a[0].equals("on"))) {
            ChatRoom.debugOff = a[0].equals("off");
            return "chatlift " + a[0];
        }
        var chat = mc.gui.getChat();
        double scale = chat.getScale();
        int height = mc.getWindow().getGuiScaledHeight();
        int chatRight = (int) Math.ceil((Mth.ceil(chat.getWidth() / scale) + 12) * scale);
        int chatBottom = (int) Math.floor(Mth.floor((height - 40) / scale) * scale);
        int drawn = ChatRoom.drawnLift();
        String out = "chatlift: bars " + (ChargeHud.drewBars() ? "top " + ChargeHud.barsTop() + " left " + ChargeHud.barsLeft() : "not drawn")
                + ", chat right " + chatRight + " bottom " + chatBottom + ", lift " + ChatRoom.lift() + ", drawn with " + drawn
                + ", gui " + mc.getWindow().getGuiScaledWidth() + "x" + height;
        if (a.length > 0 && a[0].equals("mouse")) {
            double y = chatBottom - drawn - 4.5 * scale;
            Harness.moveTo(24, y);
            Style style = chat.getClickedComponentStyleAt(24, y);
            out += "; cursor at 24, " + y + " on " + (style == null ? "no text" : "text" + (style.getHoverEvent() != null ? " with a hover" : "")
                    + (style.getClickEvent() != null ? " and a click " + style.getClickEvent().getValue() : ""));
        }
        if (a.length > 0 && a[0].equals("link")) {
            // the cursor on the first text with a click on the lowest four lines where they are drawn (a memo's [Handbook])
            for (int line = 0; line < 4; line++) {
                double y = chatBottom - drawn - (4.5 + 9 * line) * scale;
                for (int x = 2; x < chatRight; x += 2) {
                    Style style = chat.getClickedComponentStyleAt(x, y);
                    if (style != null && style.getClickEvent() != null) {
                        Harness.moveTo(x + 4, y);
                        return out + "; cursor at " + (x + 4) + ", " + y + " (line " + line + ") on a click " + style.getClickEvent().getValue();
                    }
                }
            }
            out += "; no text with a click on the lowest four lines";
        }
        return out;
    }
}
