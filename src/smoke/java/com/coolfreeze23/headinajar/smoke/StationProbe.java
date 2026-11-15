package com.coolfreeze23.headinajar.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Dev-only reads of what the Board's stations do with their heads, the same on 0.5.4 and 0.6.0 (only methods both have),
 * for the old-world check. Changes nothing.
 * <ul>
 * <li>{@code station X Y Z}: a Cerebral Array's heads, FE/t before broth ({@code potential}), FE/t now ({@code output})
 * and energy; an Employee Mount's profession, paid, days unpaid and its head; a Turret's or Socket's head;</li>
 * <li>{@code staffed X Y Z PROFESSION}: how well the machine at X Y Z is staffed for that profession (1 paid, 0.5
 * unpaid, 0 none), and a Saline Mixer's batch length in ticks.</li>
 * </ul>
 */
final class StationProbe {
    private StationProbe() {
    }

    static void run(Minecraft mc, String[] parts, List<String> report) {
        String[] a = parts.length > 1 ? parts[1].trim().split("\\s+") : new String[0];
        var server = mc.getSingleplayerServer();
        var dim = mc.level.dimension();
        server.execute(() -> {
            String out;
            try {
                var level = server.getLevel(dim);
                BlockPos pos = new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                var be = level.getBlockEntity(pos);
                if (parts[0].equals("staffed")) {
                    float s = com.coolfreeze23.headinajar.block.HeadJobs.staff(level, pos, a[3]);
                    out = "staffed " + pos.toShortString() + " " + a[3] + " -> " + s
                            + (be instanceof com.coolfreeze23.headinajar.block.SalineMixerBlockEntity m ? ", mixer batch " + m.batchTicks() + " ticks" : "");
                } else if (be instanceof com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity arr) {
                    StringBuilder kinds = new StringBuilder();
                    for (int i = 0; i < 9; i++) {
                        var t = arr.headInfo(i).type();
                        if (!arr.items().getStackInSlot(i).isEmpty()) kinds.append(t == null ? arr.items().getStackInSlot(i).getItem().toString() : net.minecraft.world.entity.EntityType.getKey(t).getPath()).append(',');
                    }
                    out = "station " + pos.toShortString() + " array: heads " + arr.heads() + " [" + kinds + "] potential " + arr.potential() + " FE/t, output "
                            + arr.output() + " FE/t, energy " + arr.energy().getEnergyStored() + ", broth " + arr.broth().getFluidInTank(0).getAmount() + " mB";
                } else if (be instanceof com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity m) {
                    out = "station " + pos.toShortString() + " mount: head " + m.head().getHoverName().getString() + ", profession " + m.profession()
                            + ", paid " + m.paid() + ", days unpaid " + m.daysMissed() + ", day " + level.getDayTime() / 24000L;
                } else if (be instanceof com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t) {
                    out = "station " + pos.toShortString() + " turret: head " + t.head().getHoverName().getString() + ", energy " + t.energy().getEnergyStored()
                            + ", target " + t.targetId() + ", powered " + t.powered();
                } else if (be instanceof com.coolfreeze23.headinajar.block.HasItems h) {
                    out = "station " + pos.toShortString() + " " + be.getClass().getSimpleName() + ": slot 0 " + h.items().getStackInSlot(0).getHoverName().getString();
                } else {
                    out = "station " + pos.toShortString() + " -> " + (be == null ? "no block entity" : be.getClass().getSimpleName());
                }
            } catch (RuntimeException e) {
                out = parts[0] + " -> failed: " + e;
            }
            synchronized (report) {
                report.add(out);
            }
        });
    }
}
