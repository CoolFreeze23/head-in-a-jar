package com.coolfreeze23.headinajar.compat;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Band;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade's server half of the staff line (no client classes here): the number, department, employer and band for
 * anyone, and the name only for the employer and those they trust; a head's title, lunch and desk status; why a Leech
 * Jar cannot draw the head it touches. It travels on Jade's own channel, so nothing in the mod's network changes; a
 * server without it sends nothing and the line is simply not shown.
 */
final class JadeStaffData {
    static final ResourceLocation STAFF = HeadInAJar.id("staff");

    private JadeStaffData() {
    }

    /** What one file says, written into Jade's data for {@code viewer}. */
    static void write(CompoundTag data, StaffFile f, EntityType<?> type, String department, Band band, Player viewer) {
        if (f == null) return;
        data.putString("No", Staff.number(f.number));
        data.putString("Dept", department);
        data.putString("Species", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        if (f.employer != null && !f.employerName.isEmpty()) data.putString("Emp", f.employerName);
        if (band != Band.NONE) data.putString("Band", band.key());
        if (Staff.maySeeName(f, viewer)) {
            if (f.customName != null && !f.customName.isEmpty()) data.putString("Name", f.customName);
            else data.putInt("NameIndex", Math.floorMod(f.nameIndex, Staff.NAMES));
        }
    }

    /** Placed heads and the four stations. */
    static final class Blocks implements IServerDataProvider<BlockAccessor> {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            var be = accessor.getBlockEntity();
            Player viewer = accessor.getPlayer();
            if (be instanceof JarredHeadBlockEntity head && head.entityType() != null) {
                write(data, head.staff(), head.entityType(), Staff.department(head.entityType()), Band.of(head.staff()), viewer);
                desk(data, head);
            } else if (be instanceof com.coolfreeze23.headinajar.block.LeechJarBlockEntity && accessor.getLevel() != null) {
                // a Leech Jar touching a head it cannot draw says why
                String why = com.coolfreeze23.headinajar.block.HeadDonor.leechRefusal(accessor.getLevel(), accessor.getPosition());
                if (why != null) data.putString("Leech", why);
            } else if (be instanceof CerebralArrayBlockEntity array) {
                int n = 0;
                StaffFile first = null;
                for (int i = 0; i < CerebralArrayBlockEntity.HEAD_SLOTS; i++) {
                    ItemStack s = array.items().getStackInSlot(i);
                    if (!s.is(ModRegistry.JARRED_HEAD.get())) continue;
                    n++;
                    if (first == null) first = Staff.of(s);
                }
                if (n == 0) return;
                data.putInt("Board", n);
                if (first != null && first.employer != null && !first.employerName.isEmpty()) data.putString("Emp", first.employerName);
            } else if (be instanceof TurretPedestalBlockEntity t) {
                station(data, t.head(), Band.CONTENT, viewer);
            } else if (be instanceof EmployeeMountBlockEntity m) {
                station(data, m.head(), m.band(), viewer);
            } else if (be instanceof NeuralSocketBlockEntity s) {
                station(data, s.head(), Band.CONTENT, viewer);
            }
        }

        private static void station(CompoundTag data, ItemStack head, Band band, Player viewer) {
            if (!head.is(ModRegistry.JARRED_HEAD.get())) return;
            EntityType<?> type = JarredHeadItem.type(head);
            if (type == null) return;
            write(data, Staff.of(head), type, Staff.department(type), band, viewer);
            var custom = head.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            data.putString("TitleKey", com.coolfreeze23.headinajar.block.HeadSpeak.titleKey(type,
                    custom == null ? new CompoundTag() : custom.getUnsafe().getCompound("Entity")));
        }

        /**
         * A placed head's desk: its title's key (the viewer's language decides whether it shows), its lunch, and its desk
         * status: what its job does when fed (an allay's item, a goat's horns left), "not on lunch" when its bowl is empty.
         */
        private static void desk(CompoundTag data, JarredHeadBlockEntity head) {
            EntityType<?> type = head.entityType();
            CompoundTag record = head.record();
            data.putString("TitleKey", com.coolfreeze23.headinajar.block.HeadSpeak.titleKey(type, record));
            data.putInt("Bowl", head.bowl);
            String job = com.coolfreeze23.headinajar.staff.StaffTable.desk(type);
            if (job == null) return;
            if (!head.fed()) {
                data.putString("Status", "unfed");
                return;
            }
            if (type == EntityType.ALLAY) {
                ItemStack held = com.coolfreeze23.headinajar.block.DeskJobs.allayItem(head.getLevel(), head);
                if (held.isEmpty()) {
                    data.putString("Status", "allay_empty");
                } else {
                    data.putString("Status", job);
                    data.putString("StatusItem", BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
                }
                return;
            }
            data.putString("Status", job);
            if (type == EntityType.GOAT) {
                int horns = (!record.contains("HasLeftHorn") || record.getBoolean("HasLeftHorn") ? 1 : 0)
                        + (!record.contains("HasRightHorn") || record.getBoolean("HasRightHorn") ? 1 : 0);
                data.putInt("StatusN", horns);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return STAFF;
        }
    }

    /** Living creatures on file (sampled, hosts, Utility Shells). */
    static final class Entities implements IServerDataProvider<EntityAccessor> {
        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (!(accessor.getEntity() instanceof Mob mob)) return;
            // a host printed to order says so (the host line itself is drawn from the client's host data)
            if (com.coolfreeze23.headinajar.host.PrintedHosts.printed(mob)) data.putBoolean("Printed", true);
            StaffFile f = Staff.of(mob);
            if (f == null) return;
            String dept = Hosts.isHost(mob) && Hosts.data(mob).shell ? Staff.TEMPS : Staff.department(mob.getType());
            write(data, f, mob.getType(), dept, Band.NONE, accessor.getPlayer());
            work(data, mob);
        }

        /** A worker's job (its card's, if it holds or works one) and its status, as the Roster shows them (0.7.0). */
        private static void work(CompoundTag data, Mob mob) {
            var d = com.coolfreeze23.headinajar.staff.work.Workforce.data(mob);
            if (d == null || !(mob.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
            String job = com.coolfreeze23.headinajar.staff.work.Order.jobOf(level, d);
            if (job != null) data.putString("WJob", "staff.headinajar.job." + job);
            String state = com.coolfreeze23.headinajar.staff.work.Workforce.paused ? "break" : switch (d.state) {
                case AWAITING -> "awaiting";
                case TO_CLOCK -> "waiting";
                case OFF_SHIFT -> "off_shift";
                case ON_JOB -> "on_job";
                case OFF_SITE, SHUTTLE -> "off_site";
                case NOTICE -> "notice";
                case DETAIL -> "detail";
            };
            data.putString("WState", "staff.headinajar.status." + state);
            if (d.status != null) data.putString("WStatus", d.status);
        }

        @Override
        public ResourceLocation getUid() {
            return STAFF;
        }
    }

    /** The lines the client draws from that data. */
    static java.util.List<Component> lines(CompoundTag data) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        if (data.contains("Board")) {
            out.add(Component.translatable("jade.headinajar.staff.board", data.getInt("Board"),
                    data.contains("Emp") ? Component.literal(data.getString("Emp")) : Component.translatable("staff.headinajar.on_file")));
            return out;
        }
        if (data.contains("Leech")) out.add(Component.translatable("jade.headinajar.library." + data.getString("Leech")));
        staff(data, out);
        work(data, out);
        if (data.getBoolean("Printed")) out.add(Component.translatable("jade.headinajar.printed.no_loot"));
        // the title, when the viewer's language has one for it
        String title = data.getString("TitleKey");
        if (!title.isEmpty() && net.minecraft.locale.Language.getInstance().has(title)) {
            out.add(0, Component.translatable("jade.headinajar.title", Component.translatable(title)));
        }
        if (data.contains("Bowl")) out.add(Component.translatable("jade.headinajar.lunch", data.getInt("Bowl")));
        if (data.contains("Status")) out.add(status(data));
        return out;
    }

    /** The desk status line: its job's line (an allay's with its item's name, a goat's with its horns), or not on lunch. */
    private static Component status(CompoundTag data) {
        String key = "desk.headinajar.status." + data.getString("Status");
        if (data.contains("StatusItem")) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString("StatusItem"));
            Component item = id == null ? Component.empty() : BuiltInRegistries.ITEM.getOptional(id)
                    .map(i -> (Component) i.getDescription()).orElse(Component.literal(id.toString()));
            return Component.translatable(key, item);
        }
        if (data.contains("StatusN")) return Component.translatable(key, data.getInt("StatusN"));
        return Component.translatable(key);
    }

    /**
     * A worker's line under the staff lines: its job and status. A status that is a stop line needing its words (a
     * "%s") shows the plain status of its state instead, since the words are not kept.
     */
    private static void work(CompoundTag data, java.util.List<Component> out) {
        if (!data.contains("WState")) return;
        String status = data.getString("WStatus");
        if (status.isEmpty() || !net.minecraft.locale.Language.getInstance().has(status)
                || net.minecraft.locale.Language.getInstance().getOrDefault(status).contains("%")) status = data.getString("WState");
        Component s = Component.translatable(status);
        out.add(data.contains("WJob") ? Component.translatable("jade.headinajar.staff.job", Component.translatable(data.getString("WJob")), s)
                : Component.translatable("jade.headinajar.staff.status", s));
    }

    /** The staff lines: number, department and employer; the name for those allowed; the band. */
    private static void staff(CompoundTag data, java.util.List<Component> out) {
        if (!data.contains("No")) return;
        Component dept = Staff.departmentName(data.getString("Dept"));
        out.add(data.contains("Emp") ? Component.translatable("jade.headinajar.staff.employer", data.getString("No"), dept, data.getString("Emp"))
                : Component.translatable("jade.headinajar.staff.on_file", data.getString("No"), dept));
        ResourceLocation id = ResourceLocation.tryParse(data.getString("Species"));
        Component species = id == null ? Component.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescription).orElse(Component.empty());
        if (data.contains("Name")) out.add(Component.translatable("jade.headinajar.staff.name", data.getString("Name"), species));
        else if (data.contains("NameIndex")) out.add(Component.translatable("jade.headinajar.staff.name",
                Component.translatable("staff.headinajar.name." + data.getInt("NameIndex")), species));
        if (data.contains("Band")) out.add(Component.translatable("jade.headinajar.staff.band", Component.translatable(data.getString("Band"))));
    }
}
