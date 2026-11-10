package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Records what the client showed after each input: action-bar, title and chat text, sounds started (even muted),
 * particles created, a screen opening or closing, an arm swing, a change of held item, inventory or open container,
 * of the player's form, vehicle or pose, movement, and a change at the block or entity under the crosshair when the
 * input was made. After the window closes the report gets one "INPUT" line; an input followed by none of these is
 * reported as NOTHING.
 * <p>
 * Ignored (listed in brackets, never counted): sounds in the ambient, weather, music and record categories or whose
 * id contains ".ambient"; particles made by the world's ambient tick; sounds and particles more than 16 blocks from
 * both the player and the input's target.
 */
public final class Observer {
    static Observer INSTANCE;
    /** Above zero while ClientLevel.animateTick runs. */
    public static int ambient;

    private final List<String> report;
    int window = 20;
    private int nextId = 1;
    private long now;
    private final List<Obs> open = new ArrayList<>();
    private Snapshot last;
    private Vec3 lastPos;
    private float lastYaw;
    private float lastPitch;

    Observer(List<String> report) {
        this.report = report;
        INSTANCE = this;
    }

    private static final class Obs {
        final int id;
        final String label;
        final long start;
        long end;
        final Vec3 startPos;
        final float startYaw;
        final float startPitch;
        BlockPos targetPos;
        BlockState targetState;
        Entity targetEntity;
        float targetHealth;
        Vec3 targetEntityPos;
        Vec3 focus;
        final Map<String, Integer> shown = new LinkedHashMap<>();
        final Map<String, Integer> ignored = new LinkedHashMap<>();
        final Map<String, Integer> info = new LinkedHashMap<>();
        boolean setup;
        double moved;
        float turned;
        boolean targetGone;

        Obs(int id, String label, long start, Vec3 pos, float yaw, float pitch) {
            this.id = id;
            this.label = label;
            this.start = start;
            this.startPos = pos;
            this.startYaw = yaw;
            this.startPitch = pitch;
        }
    }

    /** An input was made: open an observation window that ends {@code extra} ticks plus the window after now. */
    void begin(String label, int extra) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Obs obs = new Obs(nextId++, label, now, mc.player.position(), mc.player.getYRot(), mc.player.getXRot());
        obs.end = now + extra + window;
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            obs.targetPos = b.getBlockPos();
            obs.targetState = mc.level.getBlockState(obs.targetPos);
            obs.focus = Vec3.atCenterOf(obs.targetPos);
        } else if (hit instanceof EntityHitResult e) {
            obs.targetEntity = e.getEntity();
            obs.targetEntityPos = e.getEntity().position();
            obs.targetHealth = e.getEntity() instanceof LivingEntity l ? l.getHealth() : -1;
            obs.focus = e.getEntity().position();
        }
        open.add(obs);
    }

    /**
     * A server command or a setup verb ran: what follows belongs to it, and is not reported, until the next input
     * (so a /tp or /give between inputs is not counted for the input before it).
     */
    void setup(String label) {
        begin(label, 0);
        if (!open.isEmpty()) open.get(open.size() - 1).setup = true;
    }

    /** Begin with an explicit focus point (bystander actions: what happens around the bystander counts). */
    void begin(String label, int extra, Vec3 focus) {
        begin(label, extra);
        if (!open.isEmpty()) open.get(open.size() - 1).focus = focus;
    }

    private Obs current() {
        return open.isEmpty() ? null : open.get(open.size() - 1);
    }

    private static void add(Map<String, Integer> map, String key) {
        map.merge(key, 1, Integer::sum);
    }

    private boolean near(Obs obs, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.distanceToSqr(x, y, z) < 256) return true;
        return obs.focus != null && obs.focus.distanceToSqr(x, y, z) < 256;
    }

    public static void sound(SoundInstance sound) {
        Observer o = INSTANCE;
        if (o == null) return;
        Obs obs = o.current();
        if (obs == null) return;
        String id = String.valueOf(sound.getLocation());
        SoundSource source = sound.getSource();
        boolean background = source == SoundSource.AMBIENT || source == SoundSource.WEATHER || source == SoundSource.MUSIC
                || source == SoundSource.RECORDS || id.contains(".ambient") || ambient > 0;
        boolean far = !sound.isRelative() && !o.near(obs, sound.getX(), sound.getY(), sound.getZ());
        add(background || far ? obs.ignored : obs.shown, "sound " + id);
    }

    public static void particle(String type, double x, double y, double z) {
        Observer o = INSTANCE;
        if (o == null) return;
        Obs obs = o.current();
        if (obs == null) return;
        add(ambient > 0 || !o.near(obs, x, y, z) ? obs.ignored : obs.shown, "particle " + type);
    }

    public static void text(String kind, Component component) {
        Observer o = INSTANCE;
        if (o == null || component == null) return;
        Obs obs = o.current();
        if (obs == null) return;
        String s = component.getString();
        if (s.isBlank()) return;
        add(obs.shown, kind + " \"" + s + "\"");
    }

    /** A key mapping's click was consumed (information only: it is not something shown). */
    public static void consumed(String mapping) {
        Observer o = INSTANCE;
        if (o == null) return;
        Obs obs = o.current();
        if (obs != null) add(obs.info, "consumed " + mapping);
    }

    /** Information for the current input's line that does not count as shown (which bindings sit on a pressed key). */
    static void note(String text) {
        Observer o = INSTANCE;
        if (o == null) return;
        Obs obs = o.current();
        if (obs != null) add(obs.info, text);
    }

    public static void swing(LivingEntity entity, InteractionHand hand) {
        Observer o = INSTANCE;
        if (o == null) return;
        Obs obs = o.current();
        if (obs == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player) add(obs.shown, "swing " + hand.name().toLowerCase(Locale.ROOT));
        else {
            // another creature's swing counts if it is the input's target or a player (the bystander); mobs swing on their own
            boolean counts = entity == obs.targetEntity || entity instanceof net.minecraft.world.entity.player.Player;
            add(counts ? obs.shown : obs.ignored, "swing(" + entity.getName().getString() + ") " + hand.name().toLowerCase(Locale.ROOT));
        }
    }

    /** What the client state looked like on one tick. */
    private record Snapshot(String screen, int selected, String mainHand, String offHand, List<String> inventory, List<String> menu,
                            String carried, String form, String vehicle, String pose, boolean sprinting, boolean using,
                            boolean onGround, String field, String camera) {
    }

    private static String stack(ItemStack s) {
        return s.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString() + (s.getCount() > 1 ? " x" + s.getCount() : "");
    }

    private Snapshot snapshot(Minecraft mc) {
        var p = mc.player;
        List<String> inv = new ArrayList<>();
        var inventory = p.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) inv.add(stack(inventory.getItem(i)));
        List<String> menu = new ArrayList<>();
        if (p.containerMenu != p.inventoryMenu) {
            for (var slot : p.containerMenu.slots) {
                if (slot.container != inventory) menu.add(stack(slot.getItem()));
            }
        }
        String form;
        try {
            var state = p.getData(ModRegistry.JAR_STATE);
            form = state.form().name().toLowerCase(Locale.ROOT) + (state.compact() ? "(compact)" : "");
        } catch (RuntimeException e) {
            form = "?";
        }
        String vehicle = p.getVehicle() == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(p.getVehicle().getType()).toString();
        return new Snapshot(mc.screen == null ? "-" : mc.screen.getClass().getSimpleName(), inventory.selected,
                stack(p.getMainHandItem()), stack(p.getOffhandItem()), inv, menu, stack(p.containerMenu.getCarried()), form, vehicle,
                p.getPose().name().toLowerCase(Locale.ROOT), p.isSprinting(), p.isUsingItem(), p.onGround() || p.getVehicle() != null,
                mc.screen != null && mc.screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox box ? box.getValue() : null,
                mc.options.getCameraType().name().toLowerCase(Locale.ROOT));
    }

    private void diff(Snapshot a, Snapshot b, Obs obs) {
        if (!a.screen.equals(b.screen)) {
            add(obs.shown, a.screen.equals("-") ? "screen open " + b.screen : b.screen.equals("-") ? "screen close " + a.screen
                    : "screen " + a.screen + " -> " + b.screen);
        }
        if (a.selected != b.selected) add(obs.shown, "hotbar " + (a.selected + 1) + " -> " + (b.selected + 1));
        if (!a.mainHand.equals(b.mainHand)) add(obs.shown, "held " + a.mainHand + " -> " + b.mainHand);
        if (!a.offHand.equals(b.offHand)) add(obs.shown, "offhand " + a.offHand + " -> " + b.offHand);
        if (!a.carried.equals(b.carried)) add(obs.shown, "cursor " + a.carried + " -> " + b.carried);
        if (!a.form.equals(b.form)) add(obs.shown, "form " + a.form + " -> " + b.form);
        if (!a.vehicle.equals(b.vehicle)) add(obs.shown, "vehicle " + a.vehicle + " -> " + b.vehicle);
        if (!a.pose.equals(b.pose)) add(obs.shown, "pose " + a.pose + " -> " + b.pose);
        if (a.sprinting != b.sprinting) add(obs.shown, b.sprinting ? "sprint start" : "sprint stop");
        if (a.using != b.using) add(obs.shown, b.using ? "using item start" : "using item stop");
        if (!a.camera.equals(b.camera)) add(obs.shown, "camera " + a.camera + " -> " + b.camera);
        if (a.onGround != b.onGround) add(obs.shown, b.onGround ? "landed" : "left the ground");
        if (b.field != null && !b.field.equals(a.field)) add(obs.shown, "text field \"" + b.field + "\"");
        if (a.inventory.size() == b.inventory.size()) {
            for (int i = 0; i < a.inventory.size(); i++) {
                if (!a.inventory.get(i).equals(b.inventory.get(i))) add(obs.shown, "inventory[" + i + "] " + a.inventory.get(i) + " -> " + b.inventory.get(i));
            }
        }
        if (a.screen.equals(b.screen) && a.menu.size() == b.menu.size()) {
            for (int i = 0; i < a.menu.size(); i++) {
                if (!a.menu.get(i).equals(b.menu.get(i))) add(obs.shown, "container[" + i + "] " + a.menu.get(i) + " -> " + b.menu.get(i));
            }
        }
    }

    /** Called every client tick while the world is up. */
    void tick(Minecraft mc) {
        now++;
        if (mc.player == null || mc.level == null) return;
        Snapshot snap = snapshot(mc);
        Obs obs = current();
        if (last != null && obs != null) diff(last, snap, obs);
        last = snap;
        // movement and turning go to the current input, tick by tick, so a later input's effect is not counted twice
        Vec3 pos = mc.player.position();
        if (lastPos != null && obs != null) {
            obs.moved += pos.distanceTo(lastPos);
            obs.turned += Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - lastYaw)) + Math.abs(mc.player.getXRot() - lastPitch);
        }
        lastPos = pos;
        lastYaw = mc.player.getYRot();
        lastPitch = mc.player.getXRot();
        for (Obs o : open) checkTarget(mc, o);
        for (Iterator<Obs> it = open.iterator(); it.hasNext(); ) {
            Obs o = it.next();
            if (now >= o.end) {
                close(mc, o);
                it.remove();
            }
        }
    }

    /** The block or entity under the crosshair when the input was made: a change to it counts for that input. */
    private void checkTarget(Minecraft mc, Obs o) {
        if (o.targetPos != null) {
            BlockState after = mc.level.getBlockState(o.targetPos);
            if (after != o.targetState) {
                add(o.shown, "block " + o.targetPos.toShortString() + " " + (after.getBlock() == o.targetState.getBlock()
                        ? o.targetState + " -> " + after : name(o.targetState) + " -> " + name(after)));
                o.targetState = after;
            }
        }
        if (o.targetEntity != null && !o.targetGone) {
            Entity e = o.targetEntity;
            String n = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            if (e.isRemoved()) {
                add(o.shown, "target " + n + " removed");
                o.targetGone = true;
            } else if (e instanceof LivingEntity l && Math.abs(l.getHealth() - o.targetHealth) > 0.01f) {
                add(o.shown, String.format(Locale.ROOT, "target %s health %.1f -> %.1f", n, o.targetHealth, l.getHealth()));
                o.targetHealth = l.getHealth();
            }
        }
    }

    private void close(Minecraft mc, Obs o) {
        if (o.setup) return;
        checkTarget(mc, o);
        if (o.targetEntity != null && !o.targetGone && o.targetEntity.position().distanceTo(o.targetEntityPos) > 0.5) {
            add(o.shown, "target " + BuiltInRegistries.ENTITY_TYPE.getKey(o.targetEntity.getType()).getPath() + " moved");
        }
        if (o.moved > 0.25) add(o.shown, String.format(Locale.ROOT, "moved %.1f", o.moved));
        if (o.turned > 1) add(o.shown, String.format(Locale.ROOT, "turned %.0f deg", o.turned));
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "INPUT %d [%s] t=%d..%d -> ", o.id, o.label, o.start, o.end));
        if (o.shown.isEmpty()) line.append("NOTHING");
        else line.append(join(o.shown));
        if (!o.info.isEmpty()) line.append("   {").append(join(o.info)).append("}");
        if (!o.ignored.isEmpty()) line.append("   (ignored: ").append(join(o.ignored)).append(")");
        report.add(line.toString());
    }

    private static String name(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static String join(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (var e : map.entrySet()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(e.getKey());
            if (e.getValue() > 1) sb.append(" x").append(e.getValue());
        }
        return sb.toString();
    }

    /** Flushes every open window now (end of the script). */
    void flush(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        for (Obs o : open) close(mc, o);
        open.clear();
    }
}
