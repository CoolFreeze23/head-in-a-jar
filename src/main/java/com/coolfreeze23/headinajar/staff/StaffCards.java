package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;

/**
 * The placed head's file card: filled in on the server for one viewer (the name only for its employer and those they
 * trust), and renamed from it. A game without the card's channel gets its first lines in chat instead.
 */
public final class StaffCards {
    /** How far from the head a rename is accepted (blocks). */
    public static final double REACH = 8;
    /** The red of "Trophy" (the card's red ink). */
    private static final int RED = 0x96362F;

    private StaffCards() {
    }

    /** Opens the card of the head for this player (registering the head first if it somehow has no file). */
    public static void open(ServerPlayer player, JarredHeadBlockEntity head) {
        EntityType<?> type = head.entityType();
        if (type == null) return;
        StaffFile f = head.staff();
        if (f == null) {
            f = Staff.create(player.server, head.getLevel(), type);
            f.note("retro", Staff.day(head.getLevel()), "");
            head.setStaff(f);
        }
        Staff.refresh(f, player.server);
        boolean mine = Staff.maySeeName(f, player);
        CompoundTag card = card(head, f, mine, player.registryAccess());
        player.level().playSound(null, head.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1, 1);
        if (StaffNet.canShow(player)) {
            StaffNet.send(player, new StaffNet.Card(head.getBlockPos(), card, mine));
        } else {
            // an older game: the card's first lines, in chat
            player.sendSystemMessage(title(f));
            player.sendSystemMessage(Component.translatable("jade.headinajar.staff.name", mine ? Staff.name(f) : Component.translatable("staff.headinajar.on_file"),
                    type.getDescription()));
            player.sendSystemMessage(department(type));
        }
    }

    static Component title(StaffFile f) {
        return Component.translatable("screen.headinajar.file.title_number", Staff.number(f.number));
    }

    static Component department(EntityType<?> type) {
        String id = Staff.department(type);
        return Staff.departmentName(id).copy().append(" · ").append(Component.translatable("department.headinajar." + id + ".line"));
    }

    /** The card's lines (see {@link StaffNet.Card}). */
    public static CompoundTag card(JarredHeadBlockEntity head, StaffFile f, boolean mine, HolderLookup.Provider registries) {
        EntityType<?> type = head.entityType();
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        CompoundTag record = head.entityTag();
        if (record.contains("VillagerData")) tag.put("VillagerData", record.get("VillagerData").copy());
        if (record.contains("Size")) tag.putInt("Size", record.getInt("Size"));
        tag.putString("Jar", head.jar());
        tag.putString("Title", json(title(f), registries));
        ListTag rows = new ListTag();
        var level = head.getLevel();
        long day = Staff.day(level);
        row(rows, "name", mine ? Staff.name(f) : Component.translatable("staff.headinajar.on_file"), registries);
        row(rows, "species", type.getDescription(), registries);
        row(rows, "department", department(type), registries);
        row(rows, "employer", Staff.employer(f), registries);
        row(rows, "joined", Component.translatable("screen.headinajar.file.joined_value", Staff.shown(f.joined), Math.max(0, day - f.joined)), registries);
        row(rows, "morale", morale(f, type), registries);
        row(rows, "wage", wage(f, type, day), registries);
        var jar = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(head.jar()));
        row(rows, "jar", jar.map(i -> (Component) i.getDescription()).orElse(Component.literal(head.jar())), registries);
        row(rows, "genes", genes(type), registries);
        tag.put("Rows", rows);
        ListTag history = new ListTag();
        for (StaffFile.Entry e : f.history) history.add(StringTag.valueOf(json(Staff.line(e), registries)));
        tag.put("History", history);
        if (mine) tag.putString("Name", f.customName == null ? "" : f.customName);
        return tag;
    }

    private static Component morale(StaffFile f, EntityType<?> type) {
        if (!f.staff()) {
            return Component.translatable(type == EntityType.VILLAGER ? "screen.headinajar.file.trophy.villager" : "screen.headinajar.file.trophy")
                    .withStyle(s -> s.withColor(RED));
        }
        Band band = Band.of(f);
        MutableComponent word = Component.translatable(band.key());
        return com.coolfreeze23.headinajar.Config.get(com.coolfreeze23.headinajar.Config.MORALE, true) ? word.append(" (" + f.morale + ")") : word;
    }

    private static Component wage(StaffFile f, EntityType<?> type, long day) {
        StaffTable.Wage wage = StaffTable.wage(type);
        if (wage.none()) return Component.translatable("screen.headinajar.file.wage_none");
        MutableComponent line = Component.translatable("screen.headinajar.file.wage_value", wage.count(), wage.name());
        if (!f.staff()) return line;
        Component status = f.paidThrough >= day ? Component.translatable("screen.headinajar.file.paid_today")
                : Component.translatable("screen.headinajar.file.unpaid", f.paidThrough < 0 ? 1 : day - f.paidThrough);
        return line.append(" · ").append(status);
    }

    private static Component genes(EntityType<?> type) {
        var genes = com.coolfreeze23.headinajar.genetics.Gene.of(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        if (genes.isEmpty()) return Component.translatable("screen.headinajar.file.genes_none");
        MutableComponent out = Component.empty();
        for (int i = 0; i < genes.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(Component.translatable("gene.headinajar." + genes.get(i).id()));
        }
        return out;
    }

    private static void row(ListTag rows, String label, Component value, HolderLookup.Provider registries) {
        CompoundTag row = new CompoundTag();
        row.putString("label", json(Component.translatable("screen.headinajar.file." + label), registries));
        row.putString("value", json(value, registries));
        rows.add(row);
    }

    private static String json(Component c, HolderLookup.Provider registries) {
        return Component.Serializer.toJson(c, registries);
    }

    /**
     * A rename sent from the card: from within {@value #REACH} blocks, by the employer or someone they trust; formatting
     * and control characters stripped, trimmed, cut to 16 characters; a blank keeps the old name. The name lives only in
     * the file, never as a vanilla name.
     */
    public static void rename(ServerPlayer player, BlockPos pos, String name) {
        if (!(player.level().getBlockEntity(pos) instanceof JarredHeadBlockEntity head) || head.staff() == null) return;
        StaffFile f = head.staff();
        Refuser refuser = Refuser.at(head);
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH) {
            Refusals.deny(player, refuser, "staff.too_far");
            return;
        }
        if (!Staff.maySeeName(f, player)) {
            Refusals.deny(player, refuser, "staff.not_yours", Staff.name(f), Staff.employer(f));
            return;
        }
        String clean = clean(name);
        if (!clean.isEmpty() && !clean.equals(f.customName)) {
            f.customName = clean;
            f.note("renamed", Staff.day(player.level()), player.getGameProfile().getName());
            head.setStaff(f);
        }
        player.displayClientMessage(Component.translatable("screen.headinajar.file.renamed", Staff.name(f)), true);
    }

    /** A typed name as the file keeps it. */
    public static String clean(String name) {
        String s = net.minecraft.util.StringUtil.filterText(ChatFormatting.stripFormatting(name == null ? "" : name));
        s = s == null ? "" : s.trim();
        return s.length() > StaffFile.NAME ? s.substring(0, StaffFile.NAME).trim() : s;
    }
}
