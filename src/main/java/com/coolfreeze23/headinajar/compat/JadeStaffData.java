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
 * anyone, and the name only for the employer and those they trust. It travels on Jade's own channel, so nothing in the
 * mod's network changes; a server without it sends nothing and the line is simply not shown.
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
            if (type != null) write(data, Staff.of(head), type, Staff.department(type), band, viewer);
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
            StaffFile f = Staff.of(mob);
            if (f == null) return;
            String dept = Hosts.isHost(mob) && Hosts.data(mob).shell ? Staff.TEMPS : Staff.department(mob.getType());
            write(data, f, mob.getType(), dept, Band.NONE, accessor.getPlayer());
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
        if (!data.contains("No")) return out;
        Component dept = Staff.departmentName(data.getString("Dept"));
        out.add(data.contains("Emp") ? Component.translatable("jade.headinajar.staff.employer", data.getString("No"), dept, data.getString("Emp"))
                : Component.translatable("jade.headinajar.staff.on_file", data.getString("No"), dept));
        ResourceLocation id = ResourceLocation.tryParse(data.getString("Species"));
        Component species = id == null ? Component.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescription).orElse(Component.empty());
        if (data.contains("Name")) out.add(Component.translatable("jade.headinajar.staff.name", data.getString("Name"), species));
        else if (data.contains("NameIndex")) out.add(Component.translatable("jade.headinajar.staff.name",
                Component.translatable("staff.headinajar.name." + data.getInt("NameIndex")), species));
        if (data.contains("Band")) out.add(Component.translatable("jade.headinajar.staff.band", Component.translatable(data.getString("Band"))));
        return out;
    }
}
