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

    public JarScreen(JarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.headinajar.let_go"), b -> confirmLetGo())
                .bounds(leftPos + 8, topPos + 54, 56, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.headinajar.let_go.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.headinajar.courier"),
                        b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, JarMenu.BUTTON_COURIER))
                .bounds(leftPos + 66, topPos + 54, 56, 16)
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
        // shut slots past the jar's tier
        for (int i = state.slots(); i < JarState.MAX_SLOTS; i++) {
            int x = leftPos + JarMenu.MODULE_X + i * 18;
            int y = topPos + JarMenu.MODULE_Y;
            graphics.blit(BACKGROUND, x - 1, y - 1, 176, 0, 18, 18);
        }
        float fraction = state.charge() / (float) state.chargeCapacity();
        ChargeHud.drawBar(graphics, leftPos + 80, topPos + 61, 62, fraction);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.headinajar.modules"), JarMenu.MODULE_X, 18, 0x404040, false);
        JarState state = menu.state();
        graphics.drawString(font, Component.translatable("gui.headinajar.charge", Math.round(100f * state.charge() / state.chargeCapacity())), 80, 50, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
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
        renderTooltip(graphics, mouseX, mouseY);
    }
}
