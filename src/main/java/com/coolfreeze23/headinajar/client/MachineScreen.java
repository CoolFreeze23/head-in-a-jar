package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared machine screen, drawn without a texture: a grey panel, slot frames, an energy bar on the left,
 * fluid gauges and a progress arrow, all read from the menu's synced numbers.
 */
public class MachineScreen<M extends MachineMenu> extends AbstractContainerScreen<M> {
    /** A vertical gauge: data index, capacity, colour, and a tooltip key taking the amount and capacity. */
    public record Gauge(int x, int y, int w, int h, int index, int capacity, int scale, int color, String key) {
    }

    /** A left-to-right progress arrow filled from data[index] / max. */
    public record Arrow(int x, int y, int index, int max) {
    }

    private final List<Gauge> gauges = new ArrayList<>();
    private final List<Arrow> arrows = new ArrayList<>();
    private final List<String[]> lines = new ArrayList<>();
    private final List<int[]> buttons = new ArrayList<>();
    private final List<String> buttonKeys = new ArrayList<>();

    public MachineScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    public MachineScreen<M> hideInventoryLabel() {
        inventoryLabelY = 10_000;
        return this;
    }

    public MachineScreen<M> energy(int capacity) {
        gauges.add(new Gauge(8, 17, 6, 56, 0, capacity, 10, 0xFF3FD8F0, "gui.headinajar.energy"));
        return this;
    }

    public MachineScreen<M> gauge(int x, int y, int w, int h, int index, int capacity, int color, String key) {
        gauges.add(new Gauge(x, y, w, h, index, capacity, 1, color, key));
        return this;
    }

    public MachineScreen<M> arrow(int x, int y, int index, int max) {
        arrows.add(new Arrow(x, y, index, max));
        return this;
    }

    /** A status line at (x, y): translation key and the data indexes passed to it. */
    public MachineScreen<M> line(int x, int y, String key, int... indexes) {
        String[] row = new String[2 + indexes.length];
        row[0] = x + "," + y;
        row[1] = key;
        for (int i = 0; i < indexes.length; i++) row[2 + i] = String.valueOf(indexes[i]);
        lines.add(row);
        return this;
    }

    /** A button that sends menu button {@code id}. */
    public MachineScreen<M> button(int x, int y, int w, int h, String key, int id) {
        buttons.add(new int[]{x, y, w, h, id});
        buttonKeys.add(key);
        return this;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < buttons.size(); i++) {
            int[] b = buttons.get(i);
            int id = b[4];
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable(buttonKeys.get(i)),
                    btn -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id)).bounds(leftPos + b[0], topPos + b[1], b[2], b[3]).build());
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFFFFFFF);
        g.fill(x + 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFFC6C6C6);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
            g.fill(sx, sy, sx + 17, sy + 17, 0xFFFFFFFF);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        }
        for (Gauge gauge : gauges) {
            int gx = x + gauge.x, gy = y + gauge.y;
            g.fill(gx - 1, gy - 1, gx + gauge.w + 1, gy + gauge.h + 1, 0xFF373737);
            g.fill(gx, gy, gx + gauge.w, gy + gauge.h, 0xFF26323A);
            long amount = (long) menu.data(gauge.index) * gauge.scale;
            int filled = (int) Math.min(gauge.h, amount * gauge.h / Math.max(1, gauge.capacity));
            g.fill(gx, gy + gauge.h - filled, gx + gauge.w, gy + gauge.h, gauge.color);
        }
        for (Arrow arrow : arrows) {
            int ax = x + arrow.x, ay = y + arrow.y;
            g.fill(ax, ay + 3, ax + 22, ay + 8, 0xFF8B8B8B);
            int filled = arrow.max <= 0 ? 0 : 22 * menu.data(arrow.index) / arrow.max;
            g.fill(ax, ay + 3, ax + filled, ay + 8, 0xFF55C060);
            for (int i = 0; i < 5; i++) g.fill(ax + 22 + i, ay + i, ax + 23 + i, ay + 11 - i, filled >= 22 ? 0xFF55C060 : 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        // a paused machine says why, top right, opposite its name
        int status = menu.statusIndex() < 0 ? 0 : menu.data(menu.statusIndex());
        if (status > 0 && status < MachineMenu.STALLS.length) {
            Component why = Component.translatable("gui.headinajar.stall." + MachineMenu.STALLS[status]);
            g.drawString(font, why, imageWidth - 7 - font.width(why), titleLabelY, 0x902020, false);
        }
        for (String[] row : lines) {
            String[] xy = row[0].split(",");
            Object[] args = new Object[row.length - 2];
            for (int i = 2; i < row.length; i++) args[i - 2] = menu.data(Integer.parseInt(row[i]));
            g.drawString(font, Component.translatable(row[1], args), Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), 0x404040, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        for (Gauge gauge : gauges) {
            int gx = leftPos + gauge.x, gy = topPos + gauge.y;
            if (mouseX >= gx && mouseX < gx + gauge.w && mouseY >= gy && mouseY < gy + gauge.h) {
                g.renderTooltip(font, Component.translatable(gauge.key, (long) menu.data(gauge.index) * gauge.scale, gauge.capacity), mouseX, mouseY);
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }
}
