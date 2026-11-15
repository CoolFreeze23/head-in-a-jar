package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.client.JarredHeadRenderer;
import com.coolfreeze23.headinajar.staff.StaffNet;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A placed head's Personnel File, as the server filled it in ({@link StaffNet.Card}): a manila card with the head's ID
 * photo (the head itself, live, in its jar), its particulars and its record. Those allowed may write it a new name and
 * file it. Opens over the world without pausing it; there is no menu behind it.
 */
public final class PersonnelFileScreen extends Screen {
    private static final ResourceLocation SHEET = HeadInAJar.id("textures/gui/personnel_file.png");
    // the sheet's layout (sheet px): the card from (0, 0); the tab; the photo's print and the window the head shows in;
    // the paperclip sprite and where it goes over the print's top edge
    static final int CARD_W = 208, CARD_H = 180;
    private static final int TAB_X0 = 8, TAB_X1 = 160, TAB_Y1 = 12, BODY_Y = 10;
    private static final int PRINT_X0 = 10, PRINT_Y0 = 20, PRINT_X1 = 54, PRINT_Y1 = 64;
    private static final int WIN_X0 = 12, WIN_Y0 = 22, WIN_X1 = 52, WIN_Y1 = 62;
    private static final int CLIP_U = 216, CLIP_V = 0, CLIP_W = 7, CLIP_H = 20, CLIP_X = 45, CLIP_Y = 14;
    /** The rules a line of text sits on: beside the photo (the first two stop short of the seal), then across the card. */
    private static final int[] ID_RULES = {29, 38, 47, 56, 65}, LOW_RULES = {74, 83, 92, 101, 110, 119, 128, 137, 146, 155, 164, 173};
    private static final int ID_X0 = 60, LOW_X0 = 10, RULE_X1 = 200, SEAL_X = 174;
    /** Typed values in blue-black ink; the form's printed labels in a faded brown, so the two read apart. */
    private static final int INK = 0xFF34405E, LABEL = 0xFF7A6544, GAP = 4;
    /** The rename row under the card. */
    private static final int ROW_GAP = 4, BOX_W = 118, BUTTON_W = 72, ROW_H = 20;

    private final StaffNet.Card card;
    private final Component cardTitle;
    private final List<Component[]> rows = new ArrayList<>();
    private final List<Component> history = new ArrayList<>();
    private final EntityType<?> type;
    private final CompoundTag villagerData;
    private final int size;
    private final String jar;
    /** Every line drawn on the card, where it is (card px) and its full text when it was cut short. */
    private final List<Line> lines = new ArrayList<>();
    private Boolean sheet;
    private EditBox name;
    private int cardX, cardY;
    private float scale = 1;

    private record Line(int x, int y, int width, FormattedCharSequence text, int color, List<FormattedCharSequence> whole) {
    }

    private PersonnelFileScreen(StaffNet.Card card) {
        super(Component.translatableWithFallback("screen.headinajar.file.title", "PERSONNEL FILE"));
        this.card = card;
        CompoundTag tag = card.card();
        Component title = parse(tag.getString("Title"));
        this.cardTitle = title != null ? title : Component.translatableWithFallback("screen.headinajar.file.title", "PERSONNEL FILE");
        for (Tag row : tag.getList("Rows", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) row;
            Component label = parse(r.getString("label")), value = parse(r.getString("value"));
            if (label != null || value != null) rows.add(new Component[]{label != null ? label : Component.empty(), value != null ? value : Component.empty()});
        }
        ListTag lines = tag.getList("History", Tag.TAG_STRING);
        for (int i = 0; i < lines.size(); i++) {
            Component line = parse(lines.getString(i));
            if (line != null) history.add(line);
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Type"));
        this.type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        this.villagerData = tag.contains("VillagerData", Tag.TAG_COMPOUND) ? tag.getCompound("VillagerData") : null;
        // a slime's record keeps vanilla's Size (0 for the smallest); the jar wants the size as the game shows it
        this.size = tag.contains("Size", Tag.TAG_ANY_NUMERIC) ? tag.getInt("Size") + 1 : 0;
        this.jar = tag.contains("Jar", Tag.TAG_STRING) && !tag.getString("Jar").isEmpty() ? tag.getString("Jar") : null;
    }

    /** Opens the card (the payload's handler, on the client's main thread). */
    public static void open(StaffNet.Card card) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        mc.setScreen(new PersonnelFileScreen(card));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }

    /** A line of the card from the server (a component as JSON), or null when there is none or it does not read. */
    private static Component parse(String json) {
        Minecraft mc = Minecraft.getInstance();
        if (json == null || json.isEmpty() || mc.level == null) return null;
        try {
            return Component.Serializer.fromJson(json, mc.level.registryAccess());
        } catch (RuntimeException e) {
            return Component.literal(json);
        }
    }

    @Override
    protected void init() {
        boolean rename = card.canRename();
        int rowH = rename ? ROW_GAP + ROW_H : 0;
        // the card at full size where it fits; else at the largest size that keeps each of its pixels a whole number of
        // screen pixels (3/4 at GUI 4 in a 1280x720 window), so the text stays sharp
        int gui = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
        scale = 1;
        for (int n = gui; n >= 1; n--) {
            scale = n / (float) gui;
            if (CARD_W * scale + 8 <= width && CARD_H * scale + rowH + 8 <= height) break;
        }
        int shownW = Math.round(CARD_W * scale), shownH = Math.round(CARD_H * scale);
        cardX = (width - shownW) / 2;
        cardY = Math.max(4, (height - shownH - rowH) / 2);
        layout();
        if (!rename) return;
        int rowW = BOX_W + ROW_GAP + BUTTON_W, x = (width - rowW) / 2, y = cardY + shownH + ROW_GAP;
        String was = name != null ? name.getValue() : card.card().getString("Name");
        name = new EditBox(font, x + 1, y + 1, BOX_W - 2, ROW_H - 2, Component.translatableWithFallback("screen.headinajar.file.rename", "New name"));
        name.setMaxLength(16);
        name.setValue(was);
        name.setHint(Component.translatableWithFallback("screen.headinajar.file.rename", "New name"));
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatableWithFallback("screen.headinajar.file.file_it", "File it"), b -> fileIt())
                .bounds(x + BOX_W + ROW_GAP, y, BUTTON_W, ROW_H).build());
        setInitialFocus(name);
    }

    /** Sends the name typed (the server cleans it, keeps 16 characters and ignores a blank) and closes the file. */
    private void fileIt() {
        var connection = minecraft.getConnection();
        if (name != null && connection != null && connection.hasChannel(StaffNet.Rename.TYPE)) {
            PacketDistributor.sendToServer(new StaffNet.Rename(card.pos(), name.getValue()));
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (name != null && name.isFocused() && (key == 257 || key == 335)) { // Enter files it
            fileIt();
            return true;
        }
        if ((name == null || !name.isFocused()) && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Where each line goes on the card, cut to its room (the whole line kept for its tooltip). */
    private void layout() {
        lines.clear();
        fit(cardTitle, TAB_X0 + 6, 3, TAB_X1 - TAB_X0 - 10, INK, null, false);
        List<int[]> rules = new ArrayList<>();
        for (int y : ID_RULES) rules.add(new int[]{ID_X0 + 2, y, (y == 29 || y == 38 ? SEAL_X : RULE_X1) - ID_X0 - 2});
        for (int y : LOW_RULES) rules.add(new int[]{LOW_X0 + 2, y, RULE_X1 - LOW_X0 - 2});
        // the particulars fill the rules in order (beside the photo, then across); the record takes the rest, newest kept
        int shownRows = Math.min(rows.size(), rules.size());
        for (int i = 0; i < shownRows; i++) {
            int[] rule = rules.get(i);
            Component[] row = rows.get(i);
            int label = font.width(row[0]), value = font.width(row[1]);
            if (label > 0 && label + GAP + value > rule[2] && value <= rule[2]) {
                // a value is never cut when leaving its printed label off lets it fit whole (a long name on the name line)
                fit(row[1], rule[0], rule[1] - 8, rule[2], INK, row, true);
                continue;
            }
            int labelRoom = Math.min(label, rule[2] / 2);
            fit(row[0], rule[0], rule[1] - 8, labelRoom, LABEL, row, false);
            int at = label == 0 ? 0 : Math.min(label, labelRoom) + GAP;
            fit(row[1], rule[0] + at, rule[1] - 8, rule[2] - at, INK, row, false);
        }
        int free = rules.size() - shownRows;
        int from = Math.max(0, history.size() - free);
        for (int i = from; i < history.size(); i++) {
            int[] rule = rules.get(shownRows + i - from);
            fit(history.get(i), rule[0], rule[1] - 8, rule[2], INK, null, false);
        }
    }

    /**
     * One line at (x, y) on the card, cut with an ellipsis when wider than {@code room}; {@code row} the whole row it is
     * part of, shown whole under the pointer when anything of it was cut or {@code partial} (its label left off).
     */
    private void fit(Component text, int x, int y, int room, int color, Component[] row, boolean partial) {
        if (room <= 0) return;
        FormattedCharSequence shown;
        List<FormattedCharSequence> whole = null;
        boolean cut = font.width(text) > room;
        if (!cut) {
            shown = text.getVisualOrderText();
        } else {
            FormattedText part = font.substrByWidth(text, Math.max(0, room - font.width("...")));
            shown = Language.getInstance().getVisualOrder(FormattedText.composite(part, FormattedText.of("...")));
        }
        if (cut || partial) {
            Component full = row == null ? text : Component.empty().append(row[0]).append(": ").append(row[1]);
            whole = font.split(full, 200);
        }
        lines.add(new Line(x, y, Math.min(room, font.width(shown)), shown, color, whole));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cardX, cardY, 0);
        pose.scale(scale, scale, 1);
        if (hasSheet()) {
            graphics.blit(SHEET, 0, 0, 0, 0, CARD_W, CARD_H);
        } else {
            plainCard(graphics);
        }
        drawHead(graphics, partialTick);
        pose.pushPose();
        pose.translate(0, 0, 150); // over the head
        if (hasSheet()) {
            graphics.blit(SHEET, CLIP_X, CLIP_Y, CLIP_U, CLIP_V, CLIP_W, CLIP_H);
        } else {
            plainClip(graphics);
        }
        for (Line line : lines) graphics.drawString(font, line.text(), line.x(), line.y(), line.color(), false);
        pose.popPose();
        pose.popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // a line cut short shows whole under the pointer
        double cx = (mouseX - cardX) / scale, cy = (mouseY - cardY) / scale;
        for (Line line : lines) {
            if (line.whole() != null && cx >= line.x() && cx < line.x() + line.width() && cy >= line.y() - 1 && cy < line.y() + 9) {
                graphics.renderTooltip(font, line.whole(), mouseX, mouseY);
                break;
            }
        }
    }

    /** The head's ID photo: the head itself, live in its jar, standing in the print's window, turned a little. */
    private void drawHead(GuiGraphics graphics, float partialTick) {
        if (type == null || minecraft.level == null) return;
        int x0 = cardX + Math.round(WIN_X0 * scale), y0 = cardY + Math.round(WIN_Y0 * scale);
        int x1 = cardX + Math.round(WIN_X1 * scale), y1 = cardY + Math.round(WIN_Y1 * scale);
        graphics.enableScissor(x0, y0, x1, y1);
        var pose = graphics.pose();
        pose.pushPose();
        // the jar's base on the window's floor, the jar filling the window (set up as the inventory draws its mob)
        pose.translate((WIN_X0 + WIN_X1) / 2f, WIN_Y1 - 0.5f, 50);
        float px = 34;
        pose.mulPose(new Matrix4f().scaling(px, px, -px));
        pose.mulPose(Axis.ZP.rotation((float) Math.PI));
        pose.mulPose(Axis.XP.rotationDegrees(-8));
        pose.mulPose(Axis.YP.rotationDegrees(25)); // three-quarters, as a photographer would ask
        Lighting.setupForEntityInInventory();
        float age = minecraft.level.getGameTime() + partialTick;
        try {
            JarredHeadRenderer.drawJar(type, villagerData, size, jar, pose, graphics.bufferSource(), 0xF000F0, age);
        } catch (RuntimeException e) {
            HeadInAJar.LOGGER.debug("file card: could not draw {}", type, e);
        }
        graphics.flush();
        pose.popPose();
        Lighting.setupFor3DItems();
        graphics.disableScissor();
    }

    /** Whether the painted card is in the resources (it is drawn plain otherwise). */
    private boolean hasSheet() {
        if (sheet == null) sheet = minecraft.getResourceManager().getResource(SHEET).isPresent();
        return sheet;
    }

    /** The card without its painting: manila, a darker edge and rules, the photo's print and window; no words. */
    private static void plainCard(GuiGraphics g) {
        int manila = 0xFFE6D3A3, edge = 0xFFB89B62, rule = 0xFFC9B07A, red = 0xFFB0645A;
        g.fill(TAB_X0, 0, TAB_X1, TAB_Y1, edge);
        g.fill(TAB_X0 + 1, 1, TAB_X1 - 1, TAB_Y1, manila);
        g.fill(0, BODY_Y, CARD_W, CARD_H, edge);
        g.fill(1, BODY_Y + 1, CARD_W - 1, CARD_H - 1, manila);
        g.fill(TAB_X0 + 1, BODY_Y, TAB_X1 - 1, BODY_Y + 1, manila);
        for (int y : ID_RULES) g.fill(ID_X0, y, y == 29 || y == 38 ? SEAL_X : RULE_X1, y + 1, y == 29 ? red : rule);
        for (int y : LOW_RULES) g.fill(LOW_X0, y, RULE_X1, y + 1, y == 101 ? red : rule);
        g.fill(PRINT_X0, PRINT_Y0, PRINT_X1, PRINT_Y1, 0xFFF4EEDC);
        g.fill(WIN_X0, WIN_Y0, WIN_X1, WIN_Y1, 0xFF9AA6AE);
        g.fill(WIN_X0, WIN_Y1 - 6, WIN_X1, WIN_Y1, 0xFF7F8A92);
    }

    /** The paperclip, plain: a steel loop over the print's top edge. */
    private static void plainClip(GuiGraphics g) {
        int steel = 0xFF9AA3AB, dark = 0xFF5E666E;
        g.fill(CLIP_X + 1, CLIP_Y, CLIP_X + CLIP_W - 1, CLIP_Y + 1, steel);
        g.fill(CLIP_X, CLIP_Y + 1, CLIP_X + 1, CLIP_Y + CLIP_H - 1, steel);
        g.fill(CLIP_X + CLIP_W - 1, CLIP_Y + 1, CLIP_X + CLIP_W, CLIP_Y + CLIP_H - 1, dark);
        g.fill(CLIP_X + 1, CLIP_Y + CLIP_H - 1, CLIP_X + CLIP_W - 1, CLIP_Y + CLIP_H, dark);
        g.fill(CLIP_X + 2, CLIP_Y + 2, CLIP_X + 3, CLIP_Y + CLIP_H - 2, steel);
        g.fill(CLIP_X + 4, CLIP_Y + 2, CLIP_X + 5, CLIP_Y + CLIP_H - 5, steel);
        g.fill(CLIP_X + 2, CLIP_Y + 2, CLIP_X + 5, CLIP_Y + 3, steel);
    }
}
