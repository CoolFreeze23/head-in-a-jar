package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.smoke.mixin.KeyboardHandlerAccess;
import com.coolfreeze23.headinajar.smoke.mixin.MouseHandlerAccess;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-input verbs for the smoke client. Every one of them goes through the same entry points GLFW's callbacks use
 * (KeyboardHandler.keyPress, MouseHandler.onPress/onScroll/onMove, KeyboardHandler.charTyped), so key mappings,
 * conflict contexts, open screens, crosshair targeting and NeoForge's input events all behave as for a real player.
 */
public final class Harness {
    /** GLFW key codes the harness holds down; InputConstants.isKeyDown reports them as down (Shift-click, Ctrl). */
    public static final Set<Integer> HELD_KEYS = ConcurrentHashMap.newKeySet();

    private final List<String> report;
    private final Observer observer;
    private final List<Scheduled> scheduled = new ArrayList<>();
    private long tick;
    private final Set<Integer> heldButtons = new HashSet<>();

    private record Scheduled(long at, Runnable task) {
    }

    Harness(List<String> report, Observer observer) {
        this.report = report;
        this.observer = observer;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static long window() {
        return mc().getWindow().getWindow();
    }

    private static MouseHandlerAccess mouse() {
        return (MouseHandlerAccess) mc().mouseHandler;
    }

    /** Runs due scheduled releases; called every client tick before the script. */
    void tick() {
        tick++;
        List<Scheduled> due = new ArrayList<>();
        scheduled.removeIf(s -> {
            if (s.at <= tick) {
                due.add(s);
                return true;
            }
            return false;
        });
        for (Scheduled s : due) s.task.run();
    }

    private void later(int ticks, Runnable task) {
        scheduled.add(new Scheduled(tick + Math.max(1, ticks), task));
    }

    /**
     * The hidden window never has focus, so the game would never take the mouse. Mark the window active and the mouse
     * grabbed, as after a click into a focused window (GLFW does nothing to the real cursor for an unfocused window).
     */
    static void grab() {
        Minecraft mc = mc();
        mc.setWindowActive(true);
        if (mc.screen == null) mouse().hij$setGrabbed(true);
    }

    // ---------------------------------------------------------------- key bindings

    static KeyMapping mapping(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        Map<String, String> alias = Map.of("menu", "key.headinajar.jar_menu", "wheels", "key.headinajar.drive",
                "collar_zoom", "key.headinajar.zoom", "sneak", "key.sneak", "crouch", "key.sneak", "use", "key.use",
                "attack", "key.attack", "forward", "key.forward", "back", "key.back");
        String full = alias.getOrDefault(n, n);
        KeyMapping[] all = mc().options.keyMappings;
        for (String candidate : new String[]{full, "key." + full, "key.headinajar." + full}) {
            for (KeyMapping k : all) if (k.getName().equalsIgnoreCase(candidate)) return k;
        }
        for (KeyMapping k : all) if (k.getName().toLowerCase(Locale.ROOT).endsWith("." + n)) return k;
        return null;
    }

    private static int modifierKey(KeyMapping k) {
        return switch (k.getKeyModifier()) {
            case CONTROL -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case SHIFT -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case ALT -> GLFW.GLFW_KEY_LEFT_ALT;
            default -> -1;
        };
    }

    /** Presses (down=true) or releases a key mapping's bound key through the real keyboard or mouse callback. */
    private void bindingInput(KeyMapping k, boolean down) {
        InputConstants.Key key = k.getKey();
        int mod = modifierKey(k);
        if (down && mod >= 0) rawKey(mod, true);
        switch (key.getType()) {
            case MOUSE -> mouseButton(key.getValue(), down, 0);
            case KEYSYM -> rawKey(key.getValue(), down);
            case SCANCODE -> {
                mc().keyboardHandler.keyPress(window(), -1, key.getValue(), down ? 1 : 0, mods());
            }
        }
        if (!down && mod >= 0) rawKey(mod, false);
    }

    private static int mods() {
        int m = 0;
        if (HELD_KEYS.contains(GLFW.GLFW_KEY_LEFT_SHIFT) || HELD_KEYS.contains(GLFW.GLFW_KEY_RIGHT_SHIFT)) m |= GLFW.GLFW_MOD_SHIFT;
        if (HELD_KEYS.contains(GLFW.GLFW_KEY_LEFT_CONTROL) || HELD_KEYS.contains(GLFW.GLFW_KEY_RIGHT_CONTROL)) m |= GLFW.GLFW_MOD_CONTROL;
        if (HELD_KEYS.contains(GLFW.GLFW_KEY_LEFT_ALT) || HELD_KEYS.contains(GLFW.GLFW_KEY_RIGHT_ALT)) m |= GLFW.GLFW_MOD_ALT;
        return m;
    }

    /** Notes, on the current input's line, every mapping bound to a key as it goes down (vanilla's and mods'). */
    private static void notePressed(InputConstants.Key key) {
        List<String> names = new ArrayList<>();
        for (KeyMapping k : mc().options.keyMappings) if (k.getKey().equals(key)) names.add(k.getName() + (k.getKeyModifier().name().equals("NONE") ? "" : "+" + k.getKeyModifier().name().toLowerCase(Locale.ROOT)));
        Observer.note(key.getName() + " bound to " + (names.isEmpty() ? "nothing" : String.join(",", names)));
    }

    private static void rawKey(int code, boolean down) {
        if (down) notePressed(InputConstants.Type.KEYSYM.getOrCreate(code));
        if (down) HELD_KEYS.add(code);
        else HELD_KEYS.remove(code);
        mc().keyboardHandler.keyPress(window(), code, GLFW.glfwGetKeyScancode(code), down ? 1 : 0, mods());
    }

    private void mouseButton(int button, boolean down, int extraMods) {
        if (down) notePressed(InputConstants.Type.MOUSE.getOrCreate(button));
        if (down) heldButtons.add(button);
        else heldButtons.remove(button);
        mouse().hij$press(window(), button, down ? 1 : 0, mods() | extraMods);
    }

    static int rawCode(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        Map<String, String> alias = Map.of("esc", "escape", "shift", "left.shift", "ctrl", "left.control", "control", "left.control",
                "alt", "left.alt", "return", "enter");
        n = alias.getOrDefault(n, n).replace('_', '.');
        InputConstants.Key key = InputConstants.getKey("key.keyboard." + n);
        return key == InputConstants.UNKNOWN ? -1 : key.getValue();
    }

    /** "down", "up", "hold N" or (default) a tap: press now, release next tick. Returns ticks the script should wait. */
    private int timed(String[] a, int from, Runnable down, Runnable up) {
        String mode = a.length > from ? a[from] : "tap";
        switch (mode) {
            case "down" -> {
                down.run();
                return 1;
            }
            case "up" -> {
                up.run();
                return 1;
            }
            case "hold" -> {
                int n = Integer.parseInt(a[from + 1]);
                down.run();
                later(n, up);
                return n + 1;
            }
            default -> {
                down.run();
                later(1, up);
                return 2;
            }
        }
    }

    private static int holdTicks(String[] a, int from) {
        return a.length > from + 1 && a[from].equals("hold") ? Integer.parseInt(a[from + 1]) : 0;
    }

    // ---------------------------------------------------------------- screens

    private static List<AbstractWidget> widgets() {
        List<AbstractWidget> out = new ArrayList<>();
        if (mc().screen != null) collect(mc().screen.children(), out);
        return out;
    }

    private static void collect(List<? extends GuiEventListener> children, List<AbstractWidget> out) {
        for (GuiEventListener c : children) {
            if (c instanceof AbstractWidget w) out.add(w);
            if (c instanceof ContainerEventHandler h && h != mc().screen) collect(h.children(), out);
        }
    }

    /** Moves the real cursor to a point in GUI coordinates (what screens see as mouseX/mouseY). */
    static void moveTo(double gx, double gy) {
        var w = mc().getWindow();
        double sx = gx * w.getScreenWidth() / w.getGuiScaledWidth();
        double sy = gy * w.getScreenHeight() / w.getGuiScaledHeight();
        mouse().hij$move(window(), sx, sy);
    }

    private Slot findSlot(AbstractContainerScreen<?> screen, String spec) {
        var menu = screen.getMenu();
        var inv = mc().player.getInventory();
        if (spec.startsWith("inv:")) {
            int k = Integer.parseInt(spec.substring(4));
            for (Slot s : menu.slots) if (s.container == inv && s.getContainerSlot() == k) return s;
            return null;
        }
        if (spec.startsWith("item:")) {
            String id = spec.substring(5);
            if (!id.contains(":")) id = "minecraft:" + id;
            for (Slot s : menu.slots) {
                if (!s.getItem().isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).toString().equals(id)) return s;
            }
            return null;
        }
        int n = Integer.parseInt(spec);
        return n >= 0 && n < menu.slots.size() ? menu.slots.get(n) : null;
    }

    private static int button(String s) {
        return switch (s) {
            case "right" -> 1;
            case "middle" -> 2;
            default -> 0;
        };
    }

    // ---------------------------------------------------------------- verbs

    /** Runs a harness verb. Returns the ticks to wait after it, or -1 if the verb is not one of these. */
    int run(String line, Deque<String> script) {
        String[] a = line.split("\\s+");
        Minecraft mc = mc();
        switch (a[0]) {
            case "key" -> {
                // key NAME [tap|down|up|hold N]
                KeyMapping k = mapping(a[1]);
                if (k == null) {
                    report.add("key " + a[1] + " -> NO SUCH KEY MAPPING");
                    return 1;
                }
                if (k.isUnbound()) {
                    report.add("key " + a[1] + " -> UNBOUND (" + k.getName() + ")");
                    return 1;
                }
                if (!(a.length > 2 && a[2].equals("up"))) observer.begin(line, holdTicks(a, 2));
                return timed(a, 2, () -> bindingInput(k, true), () -> bindingInput(k, false));
            }
            case "press" -> {
                // press KEYNAME [tap|down|up|hold N]: a raw keyboard key (escape, enter, e, left_shift, f3 ...)
                int code = rawCode(a[1]);
                if (code < 0) {
                    report.add("press " + a[1] + " -> NO SUCH KEY");
                    return 1;
                }
                if (!(a.length > 2 && a[2].equals("up"))) observer.begin(line, holdTicks(a, 2));
                return timed(a, 2, () -> rawKey(code, true), () -> rawKey(code, false));
            }
            case "sneak", "jump", "sprint" -> {
                // shortcuts: sneak [tap|down|up|hold N]
                KeyMapping k = mapping(a[0]);
                if (!(a.length > 1 && a[1].equals("up"))) observer.begin(line, holdTicks(a, 1));
                return timed(a, 1, () -> bindingInput(k, true), () -> bindingInput(k, false));
            }
            case "hotbar" -> {
                // hotbar 1..9: the hotbar key, as pressing the number
                KeyMapping k = mapping("hotbar." + a[1]);
                observer.begin(line, 0);
                return timed(new String[0], 0, () -> bindingInput(k, true), () -> bindingInput(k, false));
            }
            case "click" -> {
                // click left|right|middle [tap|down|up|hold N]: at the crosshair in the world, or at the cursor in a screen
                int b = button(a[1]);
                if (!(a.length > 2 && a[2].equals("up"))) observer.begin(line, holdTicks(a, 2));
                return timed(a, 2, () -> mouseButton(b, true, 0), () -> mouseButton(b, false, 0));
            }
            case "scroll" -> {
                // scroll N: wheel notches, positive is up (hotbar to the left), negative down
                double n = Double.parseDouble(a[1]);
                observer.begin(line, 0);
                int steps = (int) Math.abs(n);
                for (int i = 0; i < steps; i++) mouse().hij$scroll(window(), 0, Math.signum(n));
                return 2;
            }
            case "turn" -> {
                // turn DYAW DPITCH [TICKS]: relative mouse movement converted from degrees at the current sensitivity
                float dyaw = Float.parseFloat(a[1]);
                float dpitch = Float.parseFloat(a[2]);
                int ticks = a.length > 3 ? Integer.parseInt(a[3]) : 1;
                double s = mc.options.sensitivity().get() * 0.6 + 0.2;
                double degPerPixel = s * s * s * 8.0 * 0.15;
                observer.begin(line, ticks);
                // a zero move first: after the mouse is taken the game ignores the first move it sees
                mouse().hij$move(window(), mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
                for (int i = 0; i < ticks; i++) {
                    Runnable step = () -> {
                        var m = mc.mouseHandler;
                        mouse().hij$move(window(), m.xpos() + dyaw / ticks / degPerPixel, m.ypos() + dpitch / ticks / degPerPixel);
                    };
                    if (i == 0) step.run();
                    else later(i, step);
                }
                return ticks + 1;
            }
            case "mouse" -> {
                // mouse X Y: move the cursor to GUI coordinates (screens); mouse rel X Y: relative to the open container's corner
                double x, y;
                if (a[1].equals("rel") && mc.screen instanceof AbstractContainerScreen<?> c) {
                    x = c.getGuiLeft() + Double.parseDouble(a[2]);
                    y = c.getGuiTop() + Double.parseDouble(a[3]);
                } else {
                    x = Double.parseDouble(a[1]);
                    y = Double.parseDouble(a[2]);
                }
                moveTo(x, y);
                return 1;
            }
            case "type" -> {
                // type TEXT: characters into the open screen's focused field
                String text = line.substring(line.indexOf(' ') + 1);
                observer.begin(line, 0);
                for (int i = 0; i < text.length(); ) {
                    int cp = text.codePointAt(i);
                    ((KeyboardHandlerAccess) mc.keyboardHandler).hij$charTyped(window(), cp, 0);
                    i += Character.charCount(cp);
                }
                return 2;
            }
            case "widgets" -> {
                // widgets: list the open screen's widgets with their index
                List<AbstractWidget> ws = widgets();
                report.add("widgets in " + (mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName()) + ": " + ws.size());
                for (int i = 0; i < ws.size(); i++) {
                    AbstractWidget w = ws.get(i);
                    report.add(String.format(Locale.ROOT, "  #%d \"%s\" %s at %d,%d %dx%d%s%s", i, w.getMessage().getString(), w.getClass().getSimpleName(),
                            w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.active ? "" : " inactive", w.visible ? "" : " hidden"));
                }
                if (mc.screen instanceof AbstractContainerScreen<?> c) {
                    report.add("  container corner " + c.getGuiLeft() + "," + c.getGuiTop() + " size " + c.getXSize() + "x" + c.getYSize());
                }
                return 1;
            }
            case "widget" -> {
                // widget LABEL|#N [left|right|shift] [hold N]
                String rest = line.substring(line.indexOf(' ') + 1);
                String[] mods = {};
                String target = rest;
                int q = rest.lastIndexOf('"');
                if (rest.startsWith("\"") && q > 0) {
                    target = rest.substring(1, q);
                    mods = rest.substring(q + 1).trim().isEmpty() ? new String[0] : rest.substring(q + 1).trim().split("\\s+");
                } else {
                    String[] p = rest.split("\\s+");
                    target = p[0];
                    mods = java.util.Arrays.copyOfRange(p, 1, p.length);
                }
                List<AbstractWidget> ws = widgets();
                AbstractWidget found = null;
                if (target.startsWith("#")) {
                    int i = Integer.parseInt(target.substring(1));
                    if (i < ws.size()) found = ws.get(i);
                } else {
                    for (AbstractWidget w : ws) if (w.getMessage().getString().equalsIgnoreCase(target)) found = w;
                    if (found == null) for (AbstractWidget w : ws) if (w.getMessage().getString().toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT))) found = w;
                }
                if (found == null) {
                    report.add("widget " + target + " -> NOT FOUND");
                    return 1;
                }
                moveTo(found.getX() + found.getWidth() / 2.0, found.getY() + found.getHeight() / 2.0);
                return clickHere(line, mods);
            }
            case "at" -> {
                // at X Y [left|right|shift] [hold N]: click GUI coordinates; at rel X Y ...: relative to the container corner
                int i = 1;
                double x, y;
                if (a[1].equals("rel") && mc.screen instanceof AbstractContainerScreen<?> c) {
                    x = c.getGuiLeft() + Double.parseDouble(a[2]);
                    y = c.getGuiTop() + Double.parseDouble(a[3]);
                    i = 4;
                } else {
                    x = Double.parseDouble(a[1]);
                    y = Double.parseDouble(a[2]);
                    i = 3;
                }
                moveTo(x, y);
                return clickHere(line, java.util.Arrays.copyOfRange(a, i, a.length));
            }
            case "slots" -> {
                // slots: list the open container's slots
                if (!(mc.screen instanceof AbstractContainerScreen<?> c)) {
                    report.add("slots -> no container screen");
                    return 1;
                }
                var inv = mc.player.getInventory();
                StringBuilder sb = new StringBuilder("slots in " + c.getClass().getSimpleName() + ":");
                for (int i = 0; i < c.getMenu().slots.size(); i++) {
                    Slot s = c.getMenu().slots.get(i);
                    String where = s.container == inv ? "inv:" + s.getContainerSlot() : s.container.getClass().getSimpleName();
                    sb.append(String.format(Locale.ROOT, " %d=%s%s", i, where, s.getItem().isEmpty() ? "" : "(" + BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).getPath() + " x" + s.getItem().getCount() + ")"));
                }
                report.add(sb.toString());
                return 1;
            }
            case "slot" -> {
                // slot N|inv:K|item:ID [left|right|middle|shift|double] [hold N]
                if (!(mc.screen instanceof AbstractContainerScreen<?> c)) {
                    report.add("slot " + a[1] + " -> no container screen");
                    return 1;
                }
                Slot s = findSlot(c, a[1]);
                if (s == null) {
                    report.add("slot " + a[1] + " -> NOT FOUND");
                    return 1;
                }
                moveTo(c.getGuiLeft() + s.x + 8, c.getGuiTop() + s.y + 8);
                return clickHere(line, java.util.Arrays.copyOfRange(a, 2, a.length));
            }
            case "craft" -> {
                // craft ROW/ROW/ROW with cells "id,id,_": fills the open crafting grid from the inventory by clicks, then
                // shift-clicks the result. Expands into slot lines at the front of the script.
                return craft(a[1], a.length > 2 && a[2].matches("x[0-9]+") ? Integer.parseInt(a[2].substring(1)) : 1, script);
            }
            case "observe" -> {
                // observe N: the window, in ticks, an input is watched for
                observer.window = Integer.parseInt(a[1]);
                return 1;
            }
            case "grab" -> {
                grab();
                return 1;
            }
            default -> {
                return -1;
            }
        }
    }

    /** Clicks at the cursor with modifiers: left (default), right, middle, shift, double, hold N. */
    private int clickHere(String line, String[] mods) {
        int b = 0;
        boolean shift = false, dbl = false;
        int hold = 0;
        for (int i = 0; i < mods.length; i++) {
            switch (mods[i]) {
                case "right" -> b = 1;
                case "middle" -> b = 2;
                case "shift" -> shift = true;
                case "double" -> dbl = true;
                case "hold" -> hold = Integer.parseInt(mods[++i]);
                default -> {
                }
            }
        }
        observer.begin(line, hold);
        int button = b;
        if (shift) rawKey(GLFW.GLFW_KEY_LEFT_SHIFT, true);
        mouseButton(button, true, 0);
        boolean shifted = shift;
        if (hold > 0) {
            later(hold, () -> {
                mouseButton(button, false, 0);
                if (shifted) rawKey(GLFW.GLFW_KEY_LEFT_SHIFT, false);
            });
            return hold + 2;
        }
        mouseButton(button, false, 0);
        if (dbl) {
            mouseButton(button, true, 0);
            mouseButton(button, false, 0);
        }
        if (shift) rawKey(GLFW.GLFW_KEY_LEFT_SHIFT, false);
        return 3;
    }

    private int craft(String pattern, int times, Deque<String> script) {
        Minecraft mc = mc();
        if (!(mc.screen instanceof AbstractContainerScreen<?> c)) {
            report.add("craft -> no container screen");
            return 1;
        }
        var menu = c.getMenu();
        var inv = mc.player.getInventory();
        List<Integer> grid = new ArrayList<>();
        int result = -1;
        Map<Integer, String> item = new HashMap<>();
        Map<Integer, Integer> count = new HashMap<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s instanceof ResultSlot) result = i;
            else if (s.container instanceof CraftingContainer) grid.add(i);
            else if (s.container == inv && !s.getItem().isEmpty()) {
                item.put(i, BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).toString());
                count.put(i, s.getItem().getCount());
            }
        }
        if (result < 0 || grid.isEmpty()) {
            report.add("craft -> no crafting grid in " + c.getClass().getSimpleName());
            return 1;
        }
        int width = (int) Math.round(Math.sqrt(grid.size()));
        String[] rows = pattern.split("/");
        List<String> lines = new ArrayList<>();
        for (int r = 0; r < rows.length; r++) {
            String[] cells = rows[r].split(",");
            for (int col = 0; col < cells.length; col++) {
                String id = cells[col].trim();
                if (id.isEmpty() || id.equals("_") || id.equals("-")) continue;
                if (!id.contains(":")) id = "minecraft:" + id;
                if (r >= width || col >= width) {
                    report.add("craft -> pattern bigger than the " + width + "x" + width + " grid");
                    return 1;
                }
                int src = -1;
                for (var e : item.entrySet()) {
                    if (e.getValue().equals(id) && count.get(e.getKey()) >= times) {
                        src = e.getKey();
                        break;
                    }
                }
                if (src < 0) {
                    report.add("craft -> no " + id + " left in the inventory");
                    return 1;
                }
                if (count.get(src) < times) {
                    report.add("craft -> only " + count.get(src) + " " + id + " in one stack, need " + times);
                    return 1;
                }
                count.merge(src, -times, Integer::sum);
                int dst = grid.get(r * width + col);
                lines.add("slot " + src + " left");
                for (int t = 0; t < times; t++) lines.add("slot " + dst + " right");
                lines.add("slot " + src + " left");
            }
        }
        lines.add("slot " + result + " shift");
        for (int i = lines.size() - 1; i >= 0; i--) script.addFirst(lines.get(i));
        report.add("craft " + pattern + (times > 1 ? " x" + times : "") + " -> " + lines.size() + " clicks");
        return 1;
    }

    /** Lets go of everything still held (end of script). */
    void releaseAll() {
        for (Scheduled s : new ArrayList<>(scheduled)) s.task.run();
        scheduled.clear();
        for (int b : new ArrayList<>(heldButtons)) mouseButton(b, false, 0);
        for (int k : new ArrayList<>(HELD_KEYS)) rawKey(k, false);
    }

    static String stackName(ItemStack s) {
        return s.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }
}
