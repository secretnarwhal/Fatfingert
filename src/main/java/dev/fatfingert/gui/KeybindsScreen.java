package dev.fatfingert.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fatfingert.FatfingertKeys;
import dev.fatfingert.gui.widget.FatButton;
import dev.fatfingert.gui.widget.KeyBindButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Hotkeys for the Fatfingert controls. Click a key, then press the one you want;
 * Escape unbinds it. These are the same key mappings Options > Controls lists,
 * so a change made on either screen shows up on the other.
 */
public class KeybindsScreen extends Screen {

    private static final int PAD = 14;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 32;
    private static final int ROW_H = 22;
    private static final int KEY_W_MAX = 96;

    private final Screen parent;
    private final List<FatfingertKeys.Binding> bindings = FatfingertKeys.bindings();

    /** The mapping waiting for its new key, if any. */
    private KeyMapping capturing = null;
    private int scrollOffset = 0;

    private int cardX, cardY, cardW, cardH;
    private int innerX, innerW;
    private int listY, listH, itemsPerPage;
    private int footerY;
    private int keyW;

    public KeybindsScreen(Screen parent) {
        super(Component.literal("Fatfingert Keybinds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        layout();

        int rowRight = rowRight();
        int end = Math.min(bindings.size(), scrollOffset + itemsPerPage);
        for (int i = scrollOffset; i < end; i++) {
            KeyMapping mapping = bindings.get(i).mapping();
            int rowY = listY + 2 + (i - scrollOffset) * ROW_H;

            addRenderableWidget(new KeyBindButton(
                    rowRight - 20 - keyW, rowY + 3, keyW, 16, mapping,
                    () -> capturing == mapping,
                    () -> capturing = capturing == mapping ? null : mapping
            ));
            addRenderableWidget(new FatButton(
                    rowRight - 18, rowY + 3, 16, 16,
                    Component.literal("✕"), FatButton.Style.DANGER,
                    () -> {
                        capturing = null;
                        bind(mapping, InputConstants.UNKNOWN);
                    }
            ).hint(Component.literal("Unbind")));
        }

        addRenderableWidget(new FatButton(
                cardX + PAD, footerY + 6, 66, 20,
                Component.literal("‹ Back"), FatButton.Style.NEUTRAL,
                this::onClose
        ));
    }

    private void layout() {
        cardW = Math.min(360, this.width - 16);
        innerW = cardW - PAD * 2;
        keyW = Math.min(KEY_W_MAX, innerW / 3);

        int fixedH = HEADER_H + 10 + 10 + FOOTER_H;
        int maxCardH = this.height - 16;
        itemsPerPage = Math.max(1, Math.min(bindings.size(), (maxCardH - fixedH - 4) / ROW_H));
        listH = itemsPerPage * ROW_H + 4;
        cardH = fixedH + listH;

        cardX = (this.width - cardW) / 2;
        cardY = Math.max(8, (this.height - cardH) / 2);
        innerX = cardX + PAD;
        listY = cardY + HEADER_H + 10;
        footerY = cardY + cardH - FOOTER_H;

        scrollOffset = Math.max(0, Math.min(scrollOffset, bindings.size() - itemsPerPage));
    }

    private int rowRight() {
        return innerX + innerW - (bindings.size() > itemsPerPage ? 8 : 2);
    }

    private void bind(KeyMapping mapping, InputConstants.Key key) {
        mapping.setKey(key);
        KeyMapping.resetMapping();
    }

    // ---- rendering -----------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);

        g.fillGradient(0, 0, this.width, this.height, FatTheme.SCRIM_TOP, FatTheme.SCRIM_BOT);

        FatTheme.roundRect(g, cardX - 1, cardY - 1, cardW + 2, cardH + 2, 0x50000000);
        FatTheme.panelGradient(g, cardX, cardY, cardW, cardH,
                FatTheme.CARD_RAISED, FatTheme.CARD, FatTheme.BORDER);

        FatTheme.roundGradient(g, cardX + 1, cardY + 1, cardW - 2, HEADER_H - 2,
                0xFF262633, FatTheme.CARD_RAISED);
        FatTheme.divider(g, cardX + 1, cardY + HEADER_H, cardW - 2);

        FatTheme.divider(g, cardX + 1, footerY, cardW - 2);
        FatTheme.roundGradient(g, cardX + 1, footerY + 2, cardW - 2, cardH - (footerY - cardY) - 3,
                FatTheme.CARD, FatTheme.CARD_SUNK);

        FatTheme.panel(g, innerX, listY, innerW, listH, FatTheme.CARD_SUNK, FatTheme.BORDER_SOFT);

        // Row stripes run under the key buttons, and render() only gets to draw
        // after the widgets, so they belong to the background here.
        int rowRight = rowRight();
        int end = Math.min(bindings.size(), scrollOffset + itemsPerPage);
        for (int i = scrollOffset; i < end; i++) {
            int rowY = listY + 2 + (i - scrollOffset) * ROW_H;
            if (bindings.get(i).mapping() == capturing) {
                FatTheme.roundRect(g, innerX + 2, rowY, rowRight - innerX - 2, ROW_H - 1,
                        FatTheme.alpha(FatTheme.AMBER, 0.10f));
            } else if ((i & 1) == 1) {
                FatTheme.roundRect(g, innerX + 2, rowY, rowRight - innerX - 2, ROW_H - 1, 0x08FFFFFF);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 1.21.11 renders in one pass: super.render() paints the background (our
        // card) and then the widgets, so the chrome below is drawn on top. None
        // of it overlaps a widget.
        super.render(g, mouseX, mouseY, delta);

        // Header
        FatTheme.logoMark(g, cardX + PAD, cardY + 12, 9, 8, 3);
        int textX = cardX + PAD + FatTheme.logoWidth(9) + 9;
        g.drawString(this.font, "KEYBINDS", textX, cardY + 12, FatTheme.TEXT, true);
        String sub = capturing != null
                ? "press a key · Esc unbinds · click to cancel"
                : "click a key to change it";
        g.drawString(this.font, this.font.plainSubstrByWidth(sub, cardX + cardW - PAD - textX),
                textX, cardY + 24, capturing != null ? FatTheme.AMBER : FatTheme.TEXT_DIM, false);

        // Row labels, left of the key buttons.
        int rowRight = rowRight();
        int end = Math.min(bindings.size(), scrollOffset + itemsPerPage);
        for (int i = scrollOffset; i < end; i++) {
            KeyMapping mapping = bindings.get(i).mapping();
            int rowY = listY + 2 + (i - scrollOffset) * ROW_H;
            String name = Component.translatable(mapping.getName()).getString();
            int room = rowRight - 20 - keyW - 6 - (innerX + 8);
            g.drawString(this.font, this.font.plainSubstrByWidth(name, room), innerX + 8, rowY + 7,
                    mapping == capturing ? FatTheme.AMBER : FatTheme.TEXT, false);
        }

        FatTheme.scrollbar(g, innerX + innerW - 6, listY + 3, listH - 6,
                bindings.size(), itemsPerPage, scrollOffset);

        int hintX = cardX + PAD + 74;
        g.drawString(this.font, this.font.plainSubstrByWidth("also under Options › Controls", cardX + cardW - PAD - hintX),
                hintX, footerY + 12, FatTheme.TEXT_MUTED, false);
    }

    // ---- input ---------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing != null) {
            bind(capturing, event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
            capturing = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (capturing != null) {
            // Middle and side buttons make fine hotkeys. Left or right click backs
            // out instead - bound to a toggle, they would fire on every swing or use.
            if (event.button() > 1) {
                bind(capturing, InputConstants.Type.MOUSE.getOrCreate(event.button()));
            }
            capturing = null;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (bindings.size() > itemsPerPage && mouseY >= listY && mouseY < listY + listH) {
            int maxOffset = bindings.size() - itemsPerPage;
            int next = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(verticalAmount)));
            if (next != scrollOffset) {
                scrollOffset = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        // Like vanilla's controls screen, write key changes out when leaving.
        this.minecraft.options.save();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
