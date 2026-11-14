package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.menu.JarMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Module slots, charge and the feed slot. */
public class JarScreen extends AbstractContainerScreen<JarMenu> {
    private static final ResourceLocation BACKGROUND = HeadInAJar.id("textures/gui/jar.png");
    /** The charge line and its bar, then the two buttons, all under the module row and clear of each other. */
    static final int CHARGE_Y = 50, BAR_X = 8, BAR_Y = 60, BAR_W = 132, BUTTON_Y = 67;

    public JarScreen(JarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.headinajar.let_go"), b -> confirmLetGo())
                .bounds(leftPos + 8, topPos + BUTTON_Y, 64, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.headinajar.let_go.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.headinajar.courier"),
                        b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, JarMenu.BUTTON_COURIER))
                .bounds(leftPos + 76, topPos + BUTTON_Y, 64, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.headinajar.courier.tooltip")))
                .build());
    }

    private void confirmLetGo() {
        var self = this;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, JarMenu.BUTTON_LET_GO);
            }
            minecraft.setScreen(yes ? null : self);
        }, Component.translatable("gui.headinajar.let_go.title"), Component.translatable("gui.headinajar.let_go.body")));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        JarState state = menu.state();
        // every module slot gets its own frame, so the first is as plain to see as the rest
        for (int i = 0; i < JarState.MAX_SLOTS; i++) {
            int x = leftPos + JarMenu.MODULE_X + i * 18 - 1, y = topPos + JarMenu.MODULE_Y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
            graphics.fill(x, y, x + 17, y + 1, 0xFF373737);
            graphics.fill(x, y, x + 1, y + 17, 0xFF373737);
            graphics.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
            graphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        }
        // shut slots past the jar's tier
        for (int i = state.slots(); i < JarState.MAX_SLOTS; i++) {
            int x = leftPos + JarMenu.MODULE_X + i * 18;
            int y = topPos + JarMenu.MODULE_Y;
            graphics.blit(BACKGROUND, x - 1, y - 1, 176, 0, 18, 18);
        }
        float fraction = state.charge() / (float) state.chargeCapacity();
        ChargeHud.drawBar(graphics, leftPos + BAR_X, topPos + BAR_Y, BAR_W, fraction);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.headinajar.modules"), JarMenu.MODULE_X, 18, 0x404040, false);
        JarState state = menu.state();
        graphics.drawString(font, Component.translatable("gui.headinajar.charge", Math.round(100f * state.charge() / state.chargeCapacity())), BAR_X, CHARGE_Y, 0x404040, false);
        // (the inventory's own label would sit under the buttons; the inventory needs no caption)
    }

    /** The reader's own number format (5,000 in English, 5.000 in Portuguese). */
    private java.util.Locale locale() {
        return java.util.Locale.forLanguageTag(minecraft.getLanguageManager().getSelected().replace('_', '-'));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // the empty feed slot says what it takes and what each is worth
        if (hoveredSlot != null && hoveredSlot.index == JarState.MAX_SLOTS && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.renderComponentTooltip(font, java.util.List.of(Component.translatable("gui.headinajar.feed.title"),
                    Component.translatable("gui.headinajar.feed.redstone", String.format(locale(), "%,d", JarMenu.chargeValue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE))),
                            String.format(locale(), "%,d", JarMenu.chargeValue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE_BLOCK)))).withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.translatable("gui.headinajar.feed.saline").withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        JarState state = menu.state();
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= BAR_X && mx < BAR_X + BAR_W && my >= CHARGE_Y - 1 && my < BAR_Y + 6) {
            graphics.renderComponentTooltip(font, java.util.List.of(Component.translatable("gui.headinajar.clarity.charge_hover",
                    String.format(locale(), "%,d", state.charge()), String.format(locale(), "%,d", state.chargeCapacity()))), mouseX, mouseY);
            return;
        }
        // an empty open slot says what goes in it
        if (hoveredSlot != null && hoveredSlot.index < state.slots() && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.renderComponentTooltip(font, java.util.List.of(Component.translatable("gui.headinajar.clarity.slot_empty")), mouseX, mouseY);
            return;
        }
        // a shut slot says what opens it
        for (int i = state.slots(); i < JarState.MAX_SLOTS; i++) {
            int x = JarMenu.MODULE_X + i * 18;
            if (mx >= x - 1 && mx < x + 17 && my >= JarMenu.MODULE_Y - 1 && my < JarMenu.MODULE_Y + 17) {
                graphics.renderComponentTooltip(font, java.util.List.of(Component.translatable("gui.headinajar.clarity.slot_shut",
                        Component.translatable("item.headinajar.reinforced_life_support_jar"),
                        Component.translatable("item.headinajar.executive_life_support_jar"))), mouseX, mouseY);
                return;
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
