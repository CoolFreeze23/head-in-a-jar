package com.coolfreeze23.headinajar.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * "frames NAME TICKS": saves every rendered frame for TICKS client ticks, at the real frame rate, as PNGs in
 * $SMOKE_FRAMES_DIR (default D:\hij-study\frames)\DATE-TIME_NAME\NAME_00000.png, with frames.csv (frame, client tick,
 * partial tick, milliseconds since the first frame). The script carries on meanwhile, so inputs can be made during it.
 * PNGs are written by worker threads; when 48 frames are waiting the render thread waits for them (the CSV marks
 * those frames "stalled").
 */
final class Frames {
    private static final ExecutorService WRITERS = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "smoke-frames");
        t.setDaemon(true);
        return t;
    });
    private static final Semaphore BACKLOG = new Semaphore(48);

    private final List<String> report;
    private String name;
    private Path folder;
    private int ticksLeft;
    private int frame;
    private long firstNanos;
    private long tick;
    private int stalled;
    private final List<String> csv = new ArrayList<>();

    Frames(List<String> report) {
        this.report = report;
    }

    static Path root() {
        String env = System.getenv("SMOKE_FRAMES_DIR");
        return Path.of(env != null && !env.isBlank() ? env : "D:\\hij-study\\frames");
    }

    void start(String name, int ticks) throws IOException {
        if (this.name != null) stop();
        this.name = name;
        this.folder = root().resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "_" + name);
        Files.createDirectories(folder);
        this.ticksLeft = ticks;
        this.frame = 0;
        this.stalled = 0;
        this.csv.clear();
        this.csv.add("frame,tick,partial,ms,stalled");
    }

    boolean active() {
        return name != null;
    }

    void tick() {
        tick++;
        if (name != null && --ticksLeft <= 0) stop();
    }

    void onFrame(RenderFrameEvent.Post event) {
        if (name == null) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (frame == 0) firstNanos = now;
        boolean waited = !BACKLOG.tryAcquire();
        if (waited) {
            stalled++;
            BACKLOG.acquireUninterruptibly();
        }
        NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        Path file = folder.resolve(String.format(Locale.ROOT, "%s_%05d.png", name, frame));
        WRITERS.execute(() -> {
            try (image) {
                image.writeToFile(file);
            } catch (IOException e) {
                // reported by the count at the end
            } finally {
                BACKLOG.release();
            }
        });
        csv.add(String.format(Locale.ROOT, "%d,%d,%.3f,%.1f,%s", frame, tick, event.getPartialTick().getGameTimeDeltaPartialTick(false),
                (now - firstNanos) / 1e6, waited ? "1" : "0"));
        frame++;
    }

    void stop() {
        if (name == null) return;
        try {
            Files.write(folder.resolve("frames.csv"), csv);
        } catch (IOException e) {
            report.add("frames " + name + " csv failed: " + e);
        }
        double seconds = csv.size() > 2 ? Double.parseDouble(csv.get(csv.size() - 1).split(",")[3]) / 1000 : 0;
        report.add(String.format(Locale.ROOT, "frames %s -> %d frames in %.2f s (%.1f fps), %d stalled, in %s", name, frame, seconds,
                seconds > 0 ? (frame - 1) / seconds : 0, stalled, folder));
        name = null;
    }

    /** Waits for every PNG to be written (end of the run). */
    static void drain() {
        BACKLOG.acquireUninterruptibly(48);
        BACKLOG.release(48);
        WRITERS.shutdown();
        try {
            WRITERS.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
