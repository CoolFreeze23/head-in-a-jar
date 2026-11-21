package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Living Library: a placed head, or a head at a station, gives one sample a day to the right sampling tool, with no
 * harm done. 70 to 95, or 50 to 75 for a trophy or a head whose bowl is empty. The tool's own range does not apply.
 * Only its employer and those they trust may draw from a head with an employer. Also: the heads near a place, for the
 * desk jobs that look at their neighbours once a day.
 */
public final class HeadDonor {
    public static final int MIN = 70, MAX = 95, HUNGRY = 20, BANK = 7;
    /** Where a station head's last draw day is kept, in the head stack's own data. */
    public static final String DAY = "LibraryDay";
    /** What tests can read: draws, newest last ("species|quality"). */
    private static final List<String> DRAWN = new ArrayList<>();

    private HeadDonor() {
    }

    /** How many wrong tools were refused with the sound and the head's shake (tests). */
    public static int TOOLS_REFUSED;

    public static List<String> drawn() {
        return DRAWN;
    }

    public static boolean on() {
        return Config.get(Config.LIVING_LIBRARY, true);
    }

    /** The tool routing and refusals of {@link GeneticsItems.SyringeItem#draw}, for a head's species: null when it may draw. */
    static String wrongTool(ServerPlayer player, GeneticsItems.SyringeItem tool, EntityType<?> type) {
        BloodType blood = BloodType.of(type);
        boolean right = tool.kind().bloodless ? blood == tool.kind() : !blood.bloodless;
        if (!right) {
            Item takes = tool.kind().bloodless && blood.bloodless ? GeneticsItems.SyringeItem.toolFor(blood) : null;
            player.displayClientMessage(takes != null ? Component.translatable("message.headinajar.other_bloodless_tool", takes.getDescription())
                    : Component.translatable(blood.bloodless ? "message.headinajar.bloodless" : "message.headinajar.wrong_tool"), true);
            return "tool";
        }
        if (type.is(BloodType.THICK_HIDE) && !tool.bosses()) {
            player.displayClientMessage(Component.translatable("message.headinajar.boss_hide"), true);
            return "hide";
        }
        return null;
    }

    /**
     * The wrong tool for a head's blood: its line (said by {@link #wrongTool}) comes with the refusal sound and the
     * head's (or the station's) own refusal, as every other no here does. Returns true (handled). The same tool on the
     * live creature answers the same way ({@code GeneticsItems.SyringeItem#draw}).
     */
    public static boolean toolRefused(ServerPlayer player, Refuser refuser) {
        net.minecraft.world.phys.Vec3 at = refuser.refusalOrigin();
        player.serverLevel().playSound(null, at.x, at.y, at.z, com.coolfreeze23.headinajar.registry.ModSounds.FEEDBACK_REFUSED.get(),
                refuser.refusalSource(), 1, 1);
        refuser.showRefusal(player, "library.tool");
        TOOLS_REFUSED++;
        return true;
    }

    /** Whether {@code player} may draw from a head with this file (anyone, for a head nobody employs). */
    static boolean mayDraw(ServerPlayer player, StaffFile f) {
        return f == null || f.employer == null || com.coolfreeze23.headinajar.form.HeadRegistry.get(player).trusts(f.employer, player);
    }

    /** The sample, the tool's wear, the jab, and the counts every draw keeps (Out for Blood). */
    private static void take(ServerPlayer player, ItemStack tool, InteractionHand hand, EntityType<?> type, int quality, BlockPos at,
                             net.minecraft.nbt.CompoundTag creature) {
        com.coolfreeze23.headinajar.form.FormActions.give(player, com.coolfreeze23.headinajar.genetics.Samples.mark(
                GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), type, quality), com.coolfreeze23.headinajar.genetics.Samples.nitwit(creature)));
        com.coolfreeze23.headinajar.genetics.Samples.drawn(player, type);
        int drawn = player.getPersistentData().getInt("headinajar_samples") + 1;
        player.getPersistentData().putInt("headinajar_samples", drawn);
        if (drawn >= 10) com.coolfreeze23.headinajar.Advance.grant(player, "out_for_blood");
        tool.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        player.serverLevel().playSound(null, at, com.coolfreeze23.headinajar.fx.HouseSounds.SYRINGE_JAB.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.4f);
        DRAWN.add(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type) + "|" + quality);
    }

    private static int quality(ServerLevel level, boolean full) {
        int q = MIN + level.random.nextInt(MAX - MIN + 1);
        return full ? q : q - HUNGRY;
    }

    /** A sampling tool used on a placed head. Returns false when the Living Library is off (the tool does what it did). */
    public static boolean plinth(ServerPlayer player, ItemStack stack, InteractionHand hand, JarredHeadBlockEntity head) {
        if (!on() || !(stack.getItem() instanceof GeneticsItems.SyringeItem tool) || head.entityType() == null) return false;
        ServerLevel level = player.serverLevel();
        EntityType<?> type = head.entityType();
        Refuser refuser = Refuser.at(head);
        StaffFile f = head.staff();
        if (!mayDraw(player, f)) {
            Refusals.deny(player, refuser, "staff.not_yours", Staff.name(f), Staff.employer(f));
            return true;
        }
        if (wrongTool(player, tool, type) != null) return toolRefused(player, refuser);
        long day = Staff.day(level);
        if (head.libraryDay == day) {
            Refusals.deny(player, refuser, "library.rested");
            return true;
        }
        boolean full = f != null && f.staff() && head.fed();
        int q = quality(level, full);
        head.libraryDay = day;
        head.libraryDays++;
        take(player, stack, hand, type, q, head.getBlockPos(), head.entityTag());
        level.blockEvent(head.getBlockPos(), head.getBlockState().getBlock(), JarredHeadBlockEntity.EV_WINCE, 0);
        if (f != null) {
            f.note("sampled", day, "");
            StaffBlocks.adjust(f, DeskJobs.SAMPLED);
            head.setStaff(f);
            StaffBlocks.show(head, f);
        }
        head.setChanged();
        if (head.libraryDays >= BANK) com.coolfreeze23.headinajar.Advance.grant(player, "blood_bank");
        com.coolfreeze23.headinajar.clarity.Memos.send(player, com.coolfreeze23.headinajar.clarity.Memos.Memo.FIRST_HEAD_DRAW);
        return true;
    }

    /** The day a station head last gave, from its own data (-1 never). */
    public static long day(ItemStack head) {
        CustomData data = head.get(DataComponents.CUSTOM_DATA);
        return data != null && data.getUnsafe().contains(DAY) ? data.getUnsafe().getLong(DAY) : -1;
    }

    /**
     * A sampling tool used on a station's head (a Turret Pedestal, Employee Mount or Neural Socket). Returns false when
     * the station has no jarred head or the Living Library is off (the station does what it did).
     */
    public static boolean station(ServerPlayer player, ItemStack stack, InteractionHand hand, BlockEntity station, ItemStack head) {
        if (!on() || !(stack.getItem() instanceof GeneticsItems.SyringeItem tool) || !head.is(ModRegistry.JARRED_HEAD.get())) return false;
        EntityType<?> type = JarredHeadItem.type(head);
        if (type == null) return false;
        Refuser refuser = Refuser.at(station);
        StaffFile f = Staff.of(head);
        if (!mayDraw(player, f)) {
            Refusals.deny(player, refuser, "staff.not_yours", Staff.name(f), Staff.employer(f));
            return true;
        }
        if (wrongTool(player, tool, type) != null) return toolRefused(player, refuser);
        ServerLevel level = player.serverLevel();
        long day = Staff.day(level);
        if (day(head) == day) {
            Refusals.deny(player, refuser, "library.rested");
            return true;
        }
        drawStation(player, stack, hand, station, head, type, day);
        return true;
    }

    private static void drawStation(ServerPlayer player, ItemStack stack, InteractionHand hand, BlockEntity station, ItemStack head, EntityType<?> type, long day) {
        ServerLevel level = player.serverLevel();
        // in place: the stack keeps its identity (a station's head cache is keyed by it)
        CustomData.update(DataComponents.CUSTOM_DATA, head, t -> t.putLong(DAY, day));
        StaffFile f = Staff.of(head);
        if (f != null) {
            f.note("sampled", day, "");
            Staff.write(head, f);
        }
        station.setChanged();
        CustomData record = head.get(DataComponents.CUSTOM_DATA);
        take(player, stack, hand, type, quality(level, true), station.getBlockPos(), record == null ? null : record.getUnsafe().getCompound("Entity"));
        com.coolfreeze23.headinajar.clarity.Memos.send(player, com.coolfreeze23.headinajar.clarity.Memos.Memo.FIRST_STATION_DRAW);
    }

    /**
     * A sampling tool held sneaking on a seconded host: the head is drawn through its collar, once a day, by the same
     * rules as a station's head (its file is on the host while it is seated). A plain use draws the body instead.
     */
    public static boolean collar(ServerPlayer player, ItemStack stack, InteractionHand hand, net.minecraft.world.entity.Mob host, ItemStack head) {
        if (!(stack.getItem() instanceof GeneticsItems.SyringeItem tool)) return false;
        EntityType<?> type = JarredHeadItem.type(head);
        if (type == null) return false;
        Refuser refuser = Refuser.at(host);
        StaffFile f = Staff.of(host);
        if (!mayDraw(player, f)) {
            Refusals.deny(player, refuser, "staff.not_yours", Staff.name(f), Staff.employer(f));
            return true;
        }
        if (wrongTool(player, tool, type) != null) return toolRefused(player, refuser);
        ServerLevel level = player.serverLevel();
        long day = Staff.day(level);
        if (day(head) == day) {
            Refusals.deny(player, refuser, "library.rested");
            return true;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, head, t -> t.putLong(DAY, day));
        if (f != null) {
            f.note("sampled", day, "");
            host.setData(ModRegistry.STAFF, f);
        }
        take(player, stack, hand, type, quality(level, true), host.blockPosition(), com.coolfreeze23.headinajar.staff.second.Secondees.entityTag(head));
        com.coolfreeze23.headinajar.clarity.Memos.send(player, com.coolfreeze23.headinajar.clarity.Memos.Memo.FIRST_STATION_DRAW);
        return true;
    }

    /** A sampling tool used on a Cerebral Array: the first jarred head that has not given today. */
    public static boolean array(ServerPlayer player, ItemStack stack, InteractionHand hand, CerebralArrayBlockEntity array) {
        if (!on() || !(stack.getItem() instanceof GeneticsItems.SyringeItem tool)) return false;
        ServerLevel level = player.serverLevel();
        long day = Staff.day(level);
        boolean any = false;
        String refused = null;
        for (int i = 0; i < CerebralArrayBlockEntity.HEAD_SLOTS; i++) {
            ItemStack head = array.items.getStackInSlot(i);
            if (!head.is(ModRegistry.JARRED_HEAD.get())) continue;
            EntityType<?> type = JarredHeadItem.type(head);
            if (type == null) continue;
            any = true;
            if (day(head) == day) continue;
            StaffFile f = Staff.of(head);
            if (!mayDraw(player, f)) {
                refused = "not_yours";
                continue;
            }
            // the wrong tool for this director: the next one may take it (the line says what this one needs)
            BloodType blood = BloodType.of(type);
            boolean right = tool.kind().bloodless ? blood == tool.kind() : !blood.bloodless;
            if (!right || type.is(BloodType.THICK_HIDE) && !tool.bosses()) {
                if (refused == null) refused = "tool:" + i;
                continue;
            }
            drawStation(player, stack, hand, array, head, type, day);
            return true;
        }
        Refuser refuser = Refuser.at(array);
        if (!any) return false;
        if (refused == null) Refusals.deny(player, refuser, "library.board_rested");
        else if (refused.equals("not_yours")) Refusals.deny(player, refuser, "library.board_not_yours");
        else if (wrongTool(player, tool, JarredHeadItem.type(array.items.getStackInSlot(Integer.parseInt(refused.substring(5))))) != null) toolRefused(player, refuser);
        return true;
    }

    /** The plinth a Leech Jar touches that can give today (a jar has no player: no memo, no advancement), or null. */
    public static JarredHeadBlockEntity leechable(Level level, BlockPos jar) {
        if (!on()) return null;
        long day = Staff.day(level);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (level.getBlockEntity(jar.relative(d)) instanceof JarredHeadBlockEntity head && head.entityType() != null && head.libraryDay != day) {
                BloodType blood = BloodType.of(head.entityType());
                if (!blood.bloodless && !head.entityType().is(BloodType.THICK_HIDE)) return head;
            }
        }
        return null;
    }

    /** Why a Leech Jar touching a head cannot draw it ("bloodless", "thick"), or null. */
    public static String leechRefusal(Level level, BlockPos jar) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (level.getBlockEntity(jar.relative(d)) instanceof JarredHeadBlockEntity head && head.entityType() != null) {
                if (BloodType.of(head.entityType()).bloodless) return "bloodless";
                if (head.entityType().is(BloodType.THICK_HIDE)) return "thick";
            }
        }
        return null;
    }

    /** The Leech Jar's draw from a touching head: the head's quality, then the jar's Butcher, up to 100. */
    public static net.minecraft.world.item.ItemStack leech(ServerLevel level, JarredHeadBlockEntity head, float butcher) {
        StaffFile f = head.staff();
        boolean full = f != null && f.staff() && head.fed();
        int q = Math.min(100, Math.round(quality(level, full) * (1 + 0.5f * butcher)));
        head.libraryDay = Staff.day(level);
        if (f != null) {
            f.note("sampled", head.libraryDay, "");
            StaffBlocks.adjust(f, DeskJobs.SAMPLED);
            head.setStaff(f);
        }
        head.setChanged();
        level.blockEvent(head.getBlockPos(), head.getBlockState().getBlock(), JarredHeadBlockEntity.EV_WINCE, 0);
        DRAWN.add(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(head.entityType()) + "|" + q);
        return com.coolfreeze23.headinajar.genetics.Samples.mark(GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), head.entityType(), q),
                com.coolfreeze23.headinajar.genetics.Samples.nitwit(head.entityTag()));
    }

    // --- heads near a place ------------------------------------------------------------------------------------------

    /** The block entities of the loaded chunks that a box of radius {@code r} round {@code pos} touches. */
    public static List<BlockEntity> blockEntitiesNear(Level level, BlockPos pos, int r) {
        List<BlockEntity> out = new ArrayList<>();
        int x0 = (pos.getX() - r) >> 4, x1 = (pos.getX() + r) >> 4, z0 = (pos.getZ() - r) >> 4, z1 = (pos.getZ() + r) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - pos.getX()) <= r && Math.abs(p.getY() - pos.getY()) <= r && Math.abs(p.getZ() - pos.getZ()) <= r) out.add(be);
                }
            }
        }
        return out;
    }

    /** The jarred heads on plinths and at stations within a box of radius {@code r}: where each is, and what it is. */
    public static Map<BlockPos, EntityType<?>> headsNear(Level level, BlockPos pos, int r) {
        Map<BlockPos, EntityType<?>> out = new HashMap<>();
        for (BlockEntity be : blockEntitiesNear(level, pos, r)) {
            EntityType<?> t = null;
            if (be instanceof JarredHeadBlockEntity h) t = h.entityType();
            else if (be instanceof TurretPedestalBlockEntity s) t = HeadJobs.type(s.head());
            else if (be instanceof EmployeeMountBlockEntity s) t = HeadJobs.type(s.head());
            else if (be instanceof NeuralSocketBlockEntity s) t = HeadJobs.type(s.head());
            else if (be instanceof CerebralArrayBlockEntity a) {
                for (int i = 0; i < CerebralArrayBlockEntity.HEAD_SLOTS; i++) {
                    EntityType<?> at = HeadJobs.type(a.items.getStackInSlot(i));
                    if (at != null) {
                        out.put(be.getBlockPos(), at);
                        break;
                    }
                }
                continue;
            }
            if (t != null) out.put(be.getBlockPos(), t);
        }
        return out;
    }
}
