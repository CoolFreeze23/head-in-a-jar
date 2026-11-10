package com.coolfreeze23.headinajar.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Timing for benchmarks, without writing any image:
 * "bench start NAME" starts recording the time between rendered frames (render thread) and the time each server tick
 * takes from its start to its end (server thread, so the integrated server's sleep between ticks is not counted);
 * "bench stop" writes one report line with count, average, median, 95th and 99th percentile and worst of both, the
 * 1% low frame rate, the server's own 100-tick average, the debug screen's fps string, the client connection's
 * packets per second received and sent (the numbers F3 shows as rx and tx) and the client's entity count.
 * "fpscap N [vsync 0|1]" sets the frame rate limit option (260 = unlimited) and vsync, as the video settings do.
 * "cmdout COMMAND" runs a server command at permission 4 and puts what it printed into the report.
 */
final class Bench {
    private static volatile boolean on;
    private static String name;
    private static final List<Long> frames = new ArrayList<>();
    private static final List<Long> ticks = new ArrayList<>();
    private static long lastFrame;
    private static long tickStart;
    private static long startNanos;

    static void onFrame(RenderFrameEvent.Post event) {
        if (!on) return;
        long now = System.nanoTime();
        if (lastFrame != 0) frames.add(now - lastFrame);
        lastFrame = now;
    }

    static void onServerPre(ServerTickEvent.Pre event) {
        if (on) tickStart = System.nanoTime();
    }

    static void onServerPost(ServerTickEvent.Post event) {
        if (on && tickStart != 0) {
            long d = System.nanoTime() - tickStart;
            synchronized (ticks) {
                ticks.add(d);
            }
        }
    }

    static String start(String n) {
        synchronized (ticks) {
            ticks.clear();
        }
        frames.clear();
        lastFrame = 0;
        tickStart = 0;
        name = n;
        startNanos = System.nanoTime();
        on = true;
        return "bench " + n + " started";
    }

    static String stop(Minecraft mc) {
        on = false;
        double secs = (System.nanoTime() - startNanos) / 1e9;
        long[] f = frames.stream().mapToLong(Long::longValue).toArray();
        long[] t;
        synchronized (ticks) {
            t = ticks.stream().mapToLong(Long::longValue).toArray();
        }
        var server = mc.getSingleplayerServer();
        var conn = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        StringBuilder sb = new StringBuilder("bench " + name + " -> ");
        sb.append(String.format(Locale.ROOT, "%.1f s; frames %s", secs, stats(f)));
        if (f.length > 0) {
            long[] s = f.clone();
            Arrays.sort(s);
            int k = Math.max(1, s.length / 100);
            double worst1 = 0;
            for (int i = s.length - k; i < s.length; i++) worst1 += s[i];
            worst1 /= k;
            sb.append(String.format(Locale.ROOT, ", fps %.1f, 1%% low %.1f fps", f.length / secs, 1e9 / worst1));
        }
        sb.append("; server ticks ").append(stats(t));
        if (server != null) sb.append(String.format(Locale.ROOT, ", server 100-tick avg %.2f ms", server.getAverageTickTimeNanos() / 1e6));
        if (conn != null) sb.append(String.format(Locale.ROOT, "; packets rx %.1f/s tx %.1f/s", conn.getAverageReceivedPackets(), conn.getAverageSentPackets()));
        if (mc.level != null) sb.append("; client entities ").append(mc.level.getEntityCount());
        sb.append("; debug \"").append(mc.fpsString).append("\"");
        return sb.toString();
    }

    private static String stats(long[] v) {
        if (v.length == 0) return "none";
        long[] s = v.clone();
        Arrays.sort(s);
        double sum = 0;
        for (long x : s) sum += x;
        return String.format(Locale.ROOT, "n %d avg %.2f p50 %.2f p95 %.2f p99 %.2f max %.2f ms", s.length, sum / s.length / 1e6,
                pct(s, 50) / 1e6, pct(s, 95) / 1e6, pct(s, 99) / 1e6, s[s.length - 1] / 1e6);
    }

    private static double pct(long[] sorted, double p) {
        int i = (int) Math.ceil(p / 100 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, i))];
    }

    static String fpsCap(Minecraft mc, String[] a) {
        int limit = Integer.parseInt(a[1]);
        mc.options.framerateLimit().set(limit);
        if (a.length > 3 && a[2].equals("vsync")) mc.options.enableVsync().set(a[3].equals("1"));
        return "fpscap -> limit " + mc.options.framerateLimit().get() + ", vsync " + mc.options.enableVsync().get()
                + ", window limit " + mc.getWindow().getFramerateLimit();
    }

    /** A command source that keeps what a command prints. */
    static final class Collector implements CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component component) {
            lines.add(component.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
