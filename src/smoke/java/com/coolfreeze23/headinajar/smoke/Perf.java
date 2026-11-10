package com.coolfreeze23.headinajar.smoke;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** perf NAME TICKS: frame times measured for TICKS client ticks (the script carries on); the report gets one line. */
final class Perf {
    private static String name;
    private static int ticksLeft;
    private static long last;
    private static final List<Long> FRAMES = new ArrayList<>();
    private static Consumer<String> out;

    static void start(String n, int ticks, Consumer<String> report) {
        name = n;
        ticksLeft = ticks;
        last = 0;
        FRAMES.clear();
        out = report;
    }

    static void tick() {
        if (ticksLeft <= 0 || name == null) return;
        if (--ticksLeft == 0) {
            List<Long> f = new ArrayList<>(FRAMES);
            Collections.sort(f);
            double mean = f.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
            double p99 = f.isEmpty() ? 0 : f.get(Math.min(f.size() - 1, (int) (f.size() * 0.99))) / 1e6;
            double median = f.isEmpty() ? 0 : f.get(f.size() / 2) / 1e6;
            out.accept(String.format(java.util.Locale.ROOT, "perf %s -> %d frames, mean %.2f ms (%.1f fps), median %.2f ms, p99 %.2f ms",
                    name, f.size(), mean, mean > 0 ? 1000 / mean : 0, median, p99));
            name = null;
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (name == null) return;
        long now = System.nanoTime();
        if (last != 0) FRAMES.add(now - last);
        last = now;
    }

    private Perf() {
    }
}
