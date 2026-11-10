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
        camTick();
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

    // camlog NAME TICKS: the camera service's pitch kick and field-of-view pull on every frame
    private static String camName;
    private static int camLeft;
    private static long camStart;
    private static final List<String> CAM = new ArrayList<>();
    private static float camMaxFov, camMaxPitch;

    static void camlog(String n, int ticks, Consumer<String> report) {
        camName = n;
        camLeft = ticks;
        camStart = 0;
        CAM.clear();
        camMaxFov = camMaxPitch = 0;
        out = report;
    }

    static void camTick() {
        if (camName == null || --camLeft > 0) return;
        out.accept(String.format(java.util.Locale.ROOT, "camlog %s -> %d frames, max fov change %.2f deg, max pitch kick %.2f deg; nonzero frames (ms fov pitch): %s",
                camName, CAM.size() == 0 ? 0 : CAM.size(), camMaxFov, camMaxPitch, String.join(" ", CAM.subList(0, Math.min(40, CAM.size())))));
        camName = null;
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (camName != null) {
            long now = System.nanoTime();
            if (camStart == 0) camStart = now;
            float fov = com.coolfreeze23.headinajar.client.fx.CameraDesk.lastFov, pitch = com.coolfreeze23.headinajar.client.fx.CameraDesk.lastPitch;
            camMaxFov = Math.max(camMaxFov, Math.abs(fov));
            camMaxPitch = Math.max(camMaxPitch, Math.abs(pitch));
            if (fov != 0 || pitch != 0) CAM.add(String.format(java.util.Locale.ROOT, "%d:%.2f/%.2f", (now - camStart) / 1_000_000, fov, pitch));
        }
        if (name == null) return;
        long now = System.nanoTime();
        if (last != 0) FRAMES.add(now - last);
        last = now;
    }

    private Perf() {
    }
}
