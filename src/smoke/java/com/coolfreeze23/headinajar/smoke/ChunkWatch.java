package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.staff.work.WorkerBrain;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only: counts server chunk loads, and those made while a worker's brain is thinking (the GameTest
 * {@code noChunkLoadsWhileThinking}'s counter, in the real client): {@code chunkloads start} (zeroes the counts and
 * listens), {@code chunkloads read} (the counts so far and the first 20 chunks loaded while thinking).
 */
final class ChunkWatch {
    private static boolean listening;
    private static int total;
    private static int thinking;
    private static final List<String> WHERE = new ArrayList<>();

    private ChunkWatch() {
    }

    static String run(String[] parts) {
        String a = parts.length > 1 ? parts[1].trim() : "read";
        if (a.equals("start")) {
            if (!listening) {
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ChunkEvent.Load.class, ChunkWatch::load);
                listening = true;
            }
            synchronized (WHERE) {
                total = 0;
                thinking = 0;
                WHERE.clear();
            }
            return "chunkloads start";
        }
        synchronized (WHERE) {
            return "chunkloads: " + total + " server chunk loads, " + thinking + " while staff think " + WHERE;
        }
    }

    private static void load(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel)) return;
        synchronized (WHERE) {
            total++;
            if (WorkerBrain.THINKING.get()) {
                thinking++;
                if (WHERE.size() < 20) WHERE.add(e.getChunk().getPos().toString());
            }
        }
    }
}
