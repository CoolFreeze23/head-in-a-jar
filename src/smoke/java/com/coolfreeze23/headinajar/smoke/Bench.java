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
    // Long frames (over SPIKE_MS): when in the window, how long, the garbage collector's time in that frame, and stack
    // samples of the render and server threads taken while the frame was still running (by a watchdog thread), so a
    // one-second frame says what ran in it. Written to bench-spikes.txt beside the report.
    static final long SPIKE_MS = 200;
    private static volatile Thread renderThread;
    private static volatile Thread serverThread;
    private static volatile long frameStart;
    private static long lastGcMs;
    private static long lastGcCount;
    private static final List<String> spikes = new ArrayList<>();
    private static final java.util.Map<Long, List<String>> samples = new java.util.concurrent.ConcurrentHashMap<>();
    private static Thread watchdog;

    static void onFrame(RenderFrameEvent.Post event) {
        if (!on) return;
        long now = System.nanoTime();
        renderThread = Thread.currentThread();
        long gcMs = 0, gcCount = 0;
        for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            gcMs += Math.max(0, gc.getCollectionTime());
            gcCount += Math.max(0, gc.getCollectionCount());
        }
        if (lastFrame != 0) {
            long d = now - lastFrame;
            frames.add(d);
            if (d / 1_000_000 >= SPIKE_MS) {
                List<String> st = samples.remove(lastFrame);
                synchronized (spikes) {
                    spikes.add(String.format(Locale.ROOT, "t=%.2fs %d ms gc %d ms (%d collections)%s", (lastFrame - startNanos) / 1e9, d / 1_000_000,
                            gcMs - lastGcMs, gcCount - lastGcCount, st == null ? " no sample" : "\n" + String.join("\n", st)));
                }
            }
        }
        samples.keySet().removeIf(k -> k < now - 5_000_000_000L);
        lastGcMs = gcMs;
        lastGcCount = gcCount;
        lastFrame = now;
        frameStart = now;
    }

    /** The watchdog: every 20 ms, if the current frame has run past 250 ms, samples both threads (at most every 200 ms). */
    private static void watch() {
        long sampledFrame = 0, lastSample = 0;
        while (true) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                return;
            }
            long f = frameStart;
            if (!on || f == 0) continue;
            long now = System.nanoTime();
            if (now - f < 250_000_000L) continue;
            if (sampledFrame == f && now - lastSample < 200_000_000L) continue;
            sampledFrame = f;
            lastSample = now;
            StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "  sample at +%d ms", (now - f) / 1_000_000));
            try {
                var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                if (os instanceof com.sun.management.OperatingSystemMXBean sun)
                    b.append(String.format(Locale.ROOT, ", machine cpu %.0f%%, this process %.0f%%", sun.getCpuLoad() * 100, sun.getProcessCpuLoad() * 100));
                long javas = ProcessHandle.allProcesses().filter(h -> h.info().command().map(c -> c.toLowerCase(Locale.ROOT).endsWith("java.exe")).orElse(false)).count();
                b.append(", java processes ").append(javas);
            } catch (Throwable ignored) {
            }
            stack(b, "render", renderThread);
            stack(b, "server", serverThread);
            samples.computeIfAbsent(f, k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(b.toString());
        }
    }

    private static void stack(StringBuilder b, String label, Thread t) {
        if (t == null) return;
        var info = java.lang.management.ManagementFactory.getThreadMXBean().getThreadInfo(new long[]{t.threadId()}, true, false)[0];
        b.append("\n    ").append(label).append(" ").append(t.getState());
        if (info != null && info.getLockName() != null) b.append(" on ").append(info.getLockName()).append(" held by ").append(info.getLockOwnerName());
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(st.length, 28); i++) b.append("\n      at ").append(st[i]);
    }

    static void onServerPre(ServerTickEvent.Pre event) {
        if (on) tickStart = System.nanoTime();
        serverThread = Thread.currentThread();
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
        synchronized (spikes) {
            spikes.clear();
        }
        samples.clear();
        frameStart = 0;
        if (watchdog == null) {
            watchdog = new Thread(Bench::watch, "bench-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
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
        long over100 = java.util.Arrays.stream(f).filter(x -> x >= 100_000_000L).count();
        List<String> sp;
        synchronized (spikes) {
            sp = new ArrayList<>(spikes);
        }
        sb.append(String.format(Locale.ROOT, "; frames >=100 ms %d, >=%d ms %d", over100, SPIKE_MS, sp.size()));
        for (String x : sp) sb.append(" [").append(x.split("\n", 2)[0]).append("]");
        if (!sp.isEmpty()) {
            try {
                var out = java.nio.file.Path.of(System.getProperty("headinajar.smoke.out", "."), "bench-spikes.txt");
                java.nio.file.Files.createDirectories(out.getParent());
                java.nio.file.Files.writeString(out, "== " + name + "\n" + String.join("\n", sp) + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {
                sb.append(" (spike file: ").append(e).append(")");
            }
        }
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
