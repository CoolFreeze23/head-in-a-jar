package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.menu.SeparationChairMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The chair console: the parts, a checklist, blade wear, and Form 27-B with a Sign button that must be held
 * for two seconds by the player sitting in the chair.
 */
public class SeparationChairScreen extends AbstractContainerScreen<SeparationChairMenu> {
    private static final ResourceLocation BACKGROUND = HeadInAJar.id("textures/gui/separation_chair.png");
    private static final int SIGN_X = 74, SIGN_Y = 70, SIGN_W = 94, SIGN_H = 16;
    private static final int HOLD_TICKS = 40;
    private int held;
    private boolean sent;

    public SeparationChairScreen(SeparationChairMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 180;
        inventoryLabelY = imageHeight - 94;
    }

    private List<String> missing() {
        List<String> out = new ArrayList<>();
        if (menu.energy() < SeparationChairBlockEntity.energyCost()) out.add("power");
        if (menu.getSlot(0).getItem().isEmpty()) out.add("jar");
        if (menu.getSlot(1).getItem().isEmpty()) out.add("goggles");
        if (menu.getSlot(2).getItem().isEmpty()) out.add("collar");
        if (menu.getSlot(3).getItem().getCount() < SeparationChairBlockEntity.SALINE_NEEDED) out.add("saline");
        return out;
    }

    private boolean canSign() {
        return menu.opTick() < 0 && menu.viewerSeated(minecraft.player) && missing().isEmpty();
    }

    private boolean overSign(double mx, double my) {
        return mx >= leftPos + SIGN_X && mx < leftPos + SIGN_X + SIGN_W && my >= topPos + SIGN_Y && my < topPos + SIGN_Y + SIGN_H;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        boolean holding = canSign() && minecraft.mouseHandler.isLeftPressed() && overSign(mx, my);
        held = holding ? held + 1 : 0;
        if (held >= HOLD_TICKS && !sent) {
            sent = true;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SeparationChairMenu.BUTTON_SIGN);
        }
        if (!holding) sent = false;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // energy bar under the slots
        int energy = menu.energy();
        int filled = Math.round(42f * Math.min(1, energy / (float) SeparationChairBlockEntity.CAPACITY));
        g.fill(leftPos + 26, topPos + 70, leftPos + 68, topPos + 75, 0xFF26323A);
        g.fill(leftPos + 26, topPos + 70, leftPos + 26 + filled, topPos + 75, energy >= SeparationChairBlockEntity.energyCost() ? 0xFF3FD8F0 : 0xFFE0A030);
        // the Sign button: paper with a fill that grows while held
        int x = leftPos + SIGN_X, y = topPos + SIGN_Y;
        boolean ok = canSign();
        g.fill(x, y, x + SIGN_W, y + SIGN_H, ok ? 0xFF6E5A3A : 0xFF5A5A5A);
        g.fill(x + 1, y + 1, x + SIGN_W - 1, y + SIGN_H - 1, ok ? 0xFFEDE3C8 : 0xFFB0B0B0);
        if (held > 0) g.fill(x + 1, y + 1, x + 1 + (SIGN_W - 2) * Math.min(held, HOLD_TICKS) / HOLD_TICKS, y + SIGN_H - 1, 0xFF8FD08F);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        g.drawString(font, Component.translatable("gui.headinajar.chair.energy", menu.energy() / 1000), 26, 77, 0x404040, false);

        int y = 16;
        if (menu.opTick() >= 0) {
            int pct = Math.round(100f * menu.opTick() / SeparationChairBlockEntity.OP_TICKS);
            g.drawString(font, Component.translatable("gui.headinajar.chair.running", pct).withStyle(ChatFormatting.DARK_RED), 74, y, 0xFFFFFF, false);
        } else {
            List<String> missing = missing();
            for (String key : new String[]{"power", "jar", "goggles", "collar", "saline"}) {
                boolean ok = !missing.contains(key);
                g.drawString(font, Component.literal(ok ? "+ " : "- ").append(Component.translatable("gui.headinajar.chair.need." + key)),
                        74, y, ok ? 0x2A7A2A : 0x9A2020, false);
                y += 9;
            }
        }
        int wear = menu.wear();
        g.drawString(font, Component.translatable("gui.headinajar.chair.wear", wear), 74, 61, wear > 80 ? 0x9A2020 : 0x404040, false);
        Component sign = menu.viewerSeated(minecraft.player)
                ? Component.translatable(held > 0 ? "gui.headinajar.chair.signing" : "gui.headinajar.chair.sign")
                : Component.translatable("gui.headinajar.chair.sit");
        g.drawCenteredString(font, sign, SIGN_X + SIGN_W / 2, SIGN_Y + 4, 0x3A2A10);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (overSign(mouseX, mouseY)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.headinajar.chair.form").withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.headinajar.chair.form.body", minecraft.player.getName()).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.headinajar.chair.form.hold").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderSlot(GuiGraphics g, net.minecraft.world.inventory.Slot slot) {
        super.renderSlot(g, slot);
        // ghost hints in the empty input slots
        if (slot.index < 4 && !slot.hasItem()) {
            ItemStack ghost = switch (slot.index) {
                case 0 -> new ItemStack(com.coolfreeze23.headinajar.registry.ModRegistry.LIFE_SUPPORT_JAR.get());
                case 1 -> new ItemStack(com.coolfreeze23.headinajar.registry.ModRegistry.RELAY_GOGGLES.get());
                case 2 -> new ItemStack(com.coolfreeze23.headinajar.registry.ModRegistry.COLLAR_CAM.get());
                default -> new ItemStack(com.coolfreeze23.headinajar.registry.ModRegistry.SALINE_BOTTLE.get(), 4);
            };
            g.renderFakeItem(ghost, slot.x, slot.y);
            g.fill(net.minecraft.client.renderer.RenderType.guiGhostRecipeOverlay(), slot.x, slot.y, slot.x + 16, slot.y + 16, 0x808B8B8B);
        }
    }
}
