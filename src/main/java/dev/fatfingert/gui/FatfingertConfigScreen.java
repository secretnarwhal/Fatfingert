package dev.fatfingert.gui;

import dev.fatfingert.Fatfingert;
import dev.fatfingert.FatfingertConfig;
import dev.fatfingert.gui.widget.FatButton;
import dev.fatfingert.gui.widget.KeycapButton;
import dev.fatfingert.gui.widget.ToggleChip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The Fatfingert control panel: master switch, preset picker, the keycap row of
 * guardable slots, and the allow-list for whichever slot is selected.
 */
public class FatfingertConfigScreen extends Screen {

    private static final int CAP_W_MAX = 30;
    private static final int CAP_W_MIN = 16;
    private static final int CAP_H = 18;
    private static final int CAP_DEPTH = 5;
    private static final int CAP_GAP = 4;
    private static final int OFFHAND_GAP = 14;

    private static final int ROW_H = 20;
    private static final int PAD = 14;
    private static final int MASTER_W = 96;

    private static final String[] SLOT_ORDER = {
        "hotbar0", "hotbar1", "hotbar2", "hotbar3", "hotbar4",
        "hotbar5", "hotbar6", "hotbar7", "hotbar8", "offhand"
    };

    private final Screen parent;
    private String selectedSlot = null;
    private int scrollOffset = 0;

    /** Inline preset rename state. */
    private boolean renaming = false;
    private EditBox nameBox;
    /**
     * Vanilla focuses whatever widget handled a click *after* the handler runs,
     * which would steal focus from a name box created during that handler. So
     * we claim focus on the following tick instead.
     */
    private boolean focusNameBox = false;
    /** Delete is two-step whenever the preset actually holds rules. */
    private boolean confirmDelete = false;

    // Layout, recomputed on every init/resize.
    private int cardX, cardY, cardW, cardH;
    private int innerX, innerW;
    private int presetRowY, toggleRowY;
    private int slotLabelY, capRowY;
    private int detailHeaderY, listY, listH;
    private int footerY;
    private int itemsPerPage;
    private int capRowStartX, capW;
    private int headerH, footerH;

    public FatfingertConfigScreen(Screen parent) {
        super(Component.literal("Fatfingert"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        layout();

        FatfingertConfig cfg = Fatfingert.config();

        // ---- header: master switch -----------------------------------
        addRenderableWidget(new ToggleChip(
                cardX + cardW - PAD - MASTER_W, cardY + 15, MASTER_W, 20,
                Component.literal("On"),
                Component.literal("Master switch for every guard rule."),
                FatTheme.GREEN,
                Fatfingert::isEnabled,
                () -> { Fatfingert.toggle(); rebuildWidgets(); }
        ).offLabel(Component.literal("Off")));

        // ---- preset selector -----------------------------------------
        // [PRESET] ‹  name  ›            [+] [-]
        int nameX = innerX + 64;
        int nextX = innerX + innerW - 61;
        int addX  = innerX + innerW - 41;
        int delX  = innerX + innerW - 21;

        addRenderableWidget(new FatButton(
                innerX + 48, presetRowY + 1, 14, 18,
                Component.literal("‹"), FatButton.Style.GHOST,
                () -> cyclePreset(-1)
        ));
        addRenderableWidget(new FatButton(
                nextX, presetRowY + 1, 14, 18,
                Component.literal("›"), FatButton.Style.GHOST,
                () -> cyclePreset(1)
        ));

        if (renaming) {
            String seed = nameBox != null ? nameBox.getValue() : cfg.activePreset;
            nameBox = new EditBox(this.font, nameX + 3, presetRowY + 6, nextX - nameX - 8, 12,
                    Component.literal("Preset name"));
            nameBox.setMaxLength(32);
            nameBox.setBordered(false);
            nameBox.setTextColor(FatTheme.TEXT);
            nameBox.setValue(seed);
            nameBox.moveCursorToEnd(false);
            addRenderableWidget(nameBox);
        } else {
            nameBox = null;
            addRenderableWidget(new FatButton(
                    nameX, presetRowY + 1, nextX - nameX, 18,
                    Component.literal(Fatfingert.prettyPreset(cfg.activePreset)),
                    FatButton.Style.GHOST,
                    this::startRename
            ).hint(
                    Component.literal(cfg.activePreset),
                    Component.literal("Click to rename this preset")
            ));
        }

        addRenderableWidget(new FatButton(
                addX, presetRowY + 2, 18, 16,
                Component.literal("+"), FatButton.Style.NEUTRAL,
                this::addPreset
        ).hint(Component.literal("New preset")));

        boolean canDelete = cfg.presets.size() > 1;
        FatButton delete = new FatButton(
                delX, presetRowY + 2, 18, 16,
                Component.literal(confirmDelete ? "✓" : "−"),
                confirmDelete ? FatButton.Style.DANGER : FatButton.Style.NEUTRAL,
                this::deletePreset
        ).hint(confirmDelete
                ? Component.literal("Click again to delete \"" + cfg.activePreset + "\"")
                : Component.literal("Delete this preset"));
        delete.active = canDelete;
        addRenderableWidget(delete);

        // ---- toggles --------------------------------------------------
        int third = (innerW - 8) / 3;
        addRenderableWidget(new ToggleChip(
                innerX, toggleRowY, third, 20,
                Component.literal("Lock Contents"),
                Component.literal("Also stops you taking an allowed item back OUT of its guarded slot. "
                        + "A wrong item can always come out."),
                FatTheme.AMBER,
                () -> Fatfingert.activePreset().blockEmptying,
                () -> { Fatfingert.toggleLockContents(); rebuildWidgets(); }
        ).shortLabel(Component.literal("Lock")));
        addRenderableWidget(new ToggleChip(
                innerX + third + 4, toggleRowY, third, 20,
                Component.literal("Strict Shift"),
                Component.literal("Blocks shift-clicks that would land a wrong item in a guarded slot. "
                        + "Shifting items out is never blocked."),
                FatTheme.AMBER,
                () -> Fatfingert.config().strictShiftClick,
                () -> { Fatfingert.toggleStrictShift(); rebuildWidgets(); }
        ).shortLabel(Component.literal("Strict")));
        addRenderableWidget(new ToggleChip(
                innerX + (third + 4) * 2, toggleRowY, innerW - (third + 4) * 2, 20,
                Component.literal("Inventory Button"),
                Component.literal("Shows the Fatfingert button in your inventory. "
                        + "With it hidden, get back here through a keybind or Mod Menu."),
                FatTheme.AMBER,
                () -> Fatfingert.config().showInventoryButton,
                () -> { Fatfingert.toggleInventoryButton(); rebuildWidgets(); }
        ).shortLabel(Component.literal("Inventory")));

        // ---- keycap row ------------------------------------------------
        int x = capRowStartX;
        for (int i = 0; i < SLOT_ORDER.length; i++) {
            String slotKey = SLOT_ORDER[i];
            boolean isOffhand = slotKey.equals("offhand");
            if (isOffhand) x += OFFHAND_GAP - CAP_GAP;

            addRenderableWidget(new KeycapButton(
                    x, capRowY, capW, CAP_H, CAP_DEPTH,
                    slotKey, isOffhand ? "OH" : String.valueOf(i + 1),
                    () -> selectedSlot,
                    key -> {
                        commitRename();
                        confirmDelete = false;
                        selectedSlot = key.equals(selectedSlot) ? null : key;
                        scrollOffset = 0;
                        rebuildWidgets();
                    }
            ));
            x += capW + CAP_GAP;
        }

        // ---- allow-list ------------------------------------------------
        if (selectedSlot != null) {
            addRenderableWidget(new FatButton(
                    innerX + innerW - 76, detailHeaderY - 2, 76, 16,
                    Component.literal("+ Add Item"), FatButton.Style.PRIMARY,
                    () -> Minecraft.getInstance().setScreen(new ItemPickerScreen(this, selectedSlot))
            ));

            List<String> rule = Fatfingert.ruleFor(selectedSlot);
            if (rule != null) {
                clampScroll(rule.size());
                int visibleEnd = Math.min(rule.size(), scrollOffset + itemsPerPage);
                boolean scrolls = rule.size() > itemsPerPage;
                int rowRight = innerX + innerW - (scrolls ? 8 : 2);

                for (int i = scrollOffset; i < visibleEnd; i++) {
                    int rowY = listY + 2 + (i - scrollOffset) * ROW_H;
                    final int removeIdx = i;
                    addRenderableWidget(new FatButton(
                            rowRight - 18, rowY + 2, 16, 16,
                            Component.literal("✕"), FatButton.Style.DANGER,
                            () -> removeRule(removeIdx)
                    ));
                }
            }
        }

        // ---- footer ----------------------------------------------------
        int doneX = cardX + cardW - PAD - 74;
        addRenderableWidget(new FatButton(
                doneX - 6 - 64, footerY + 6, 64, 20,
                Component.literal("Keybinds"), FatButton.Style.NEUTRAL,
                () -> {
                    commitRename();
                    this.minecraft.setScreen(new KeybindsScreen(this));
                }
        ));
        addRenderableWidget(new FatButton(
                doneX, footerY + 6, 74, 20,
                Component.literal("Done"), FatButton.Style.PRIMARY,
                this::onClose
        ));
    }

    private void layout() {
        // Minecraft allows GUI scales down to a 320x240 logical viewport, so the
        // card has to survive being genuinely small.
        boolean compact = this.height < 300;
        headerH = compact ? 38 : 46;
        footerH = compact ? 26 : 32;
        int gapHeader = compact ? 6 : 10;   // header -> preset row
        int gapSlots  = compact ? 8 : 14;   // toggles -> slot section
        int gapDetail = compact ? 8 : 12;   // slots -> detail header

        cardW = Math.min(452, this.width - 16);
        innerW = cardW - PAD * 2;

        // Shrink the keycaps rather than letting the row spill out of the card.
        int gaps = (SLOT_ORDER.length - 1) * CAP_GAP + (OFFHAND_GAP - CAP_GAP);
        capW = Math.max(CAP_W_MIN, Math.min(CAP_W_MAX, (innerW - gaps) / SLOT_ORDER.length));

        int controlsH = 20 + 6 + 20;                       // preset row + toggle row
        int capBlockH = CAP_H + CAP_DEPTH + 11;
        int slotSectionH = 13 + capBlockH;
        int detailHeaderH = 18;

        int gapList = compact ? 2 : 4;      // detail header -> list well
        int fixedH = headerH + gapHeader + controlsH + gapSlots + slotSectionH
                   + gapDetail + detailHeaderH + gapList + footerH;

        int maxCardH = this.height - 16;
        int wanted = 5 * ROW_H + 4;
        listH = Math.min(wanted, Math.max(ROW_H + 4, maxCardH - fixedH));
        itemsPerPage = Math.max(1, (listH - 4) / ROW_H);
        listH = itemsPerPage * ROW_H + 4;

        cardH = fixedH + listH;
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(4, (this.height - cardH) / 2);
        innerX = cardX + PAD;

        int cursor = cardY + headerH + gapHeader;
        presetRowY = cursor;
        toggleRowY = cursor + 26;
        cursor = toggleRowY + 20 + gapSlots;
        slotLabelY = cursor;
        capRowY = cursor + 13;
        cursor = capRowY + capBlockH + gapDetail;
        detailHeaderY = cursor;
        listY = cursor + detailHeaderH + gapList;
        footerY = cardY + cardH - footerH;

        int rowW = SLOT_ORDER.length * (capW + CAP_GAP) - CAP_GAP + (OFFHAND_GAP - CAP_GAP);
        capRowStartX = cardX + (cardW - rowW) / 2;
    }

    // ---- actions -------------------------------------------------------

    private void cyclePreset(int direction) {
        commitRename();
        confirmDelete = false;
        Fatfingert.config().cyclePreset(direction);
        scrollOffset = 0;
        rebuildWidgets();
    }

    /** Creates a preset and drops straight into naming it. */
    private void addPreset() {
        commitRename();
        confirmDelete = false;
        Fatfingert.config().addPreset();
        selectedSlot = null;
        scrollOffset = 0;
        renaming = true;
        nameBox = null;
        focusNameBox = true;
        rebuildWidgets();
    }

    private void deletePreset() {
        FatfingertConfig cfg = Fatfingert.config();
        if (cfg.presets.size() <= 1) return;

        cancelRename();

        // Only make the user confirm when there is something to lose.
        boolean hasRules = !Fatfingert.activePreset().rules.isEmpty();
        if (hasRules && !confirmDelete) {
            confirmDelete = true;
            rebuildWidgets();
            return;
        }

        cfg.removePreset(cfg.activePreset);
        confirmDelete = false;
        selectedSlot = null;
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void startRename() {
        confirmDelete = false;
        renaming = true;
        nameBox = null;
        focusNameBox = true;
        rebuildWidgets();
    }

    /** Applies whatever is in the name box, if we are renaming. */
    private void commitRename() {
        if (!renaming) return;
        String typed = nameBox != null ? nameBox.getValue() : null;
        renaming = false;
        nameBox = null;
        focusNameBox = false;
        if (typed != null) {
            FatfingertConfig cfg = Fatfingert.config();
            cfg.renamePreset(cfg.activePreset, typed);
        }
        rebuildWidgets();
    }

    private void cancelRename() {
        if (!renaming) return;
        renaming = false;
        nameBox = null;
        focusNameBox = false;
        rebuildWidgets();
    }

    private void removeRule(int index) {
        FatfingertConfig.Preset p = Fatfingert.activePreset();
        List<String> r = p.rules.get(selectedSlot);
        if (r != null && index < r.size()) {
            r.remove(index);
            if (r.isEmpty()) p.rules.remove(selectedSlot);
            Fatfingert.config().save();
            rebuildWidgets();
        }
    }

    private void clampScroll(int total) {
        int maxOffset = Math.max(0, total - itemsPerPage);
        if (scrollOffset > maxOffset) scrollOffset = maxOffset;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    // ---- rendering -----------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);

        // Dim everything behind the card.
        g.fillGradient(0, 0, this.width, this.height, FatTheme.SCRIM_TOP, FatTheme.SCRIM_BOT);

        // Card.
        FatTheme.roundRect(g, cardX - 1, cardY - 1, cardW + 2, cardH + 2, 0x50000000);
        FatTheme.panelGradient(g, cardX, cardY, cardW, cardH,
                FatTheme.CARD_RAISED, FatTheme.CARD, FatTheme.BORDER);

        // Header band.
        FatTheme.roundGradient(g, cardX + 1, cardY + 1, cardW - 2, headerH - 2,
                0xFF262633, FatTheme.CARD_RAISED);
        FatTheme.divider(g, cardX + 1, cardY + headerH, cardW - 2);

        // Footer band.
        FatTheme.divider(g, cardX + 1, footerY, cardW - 2);
        FatTheme.roundGradient(g, cardX + 1, footerY + 2, cardW - 2, cardH - (footerY - cardY) - 3,
                FatTheme.CARD, FatTheme.CARD_SUNK);

        // Preset well.
        FatTheme.panel(g, innerX, presetRowY, innerW, 20, FatTheme.CARD_SUNK,
                renaming ? FatTheme.alpha(FatTheme.GREEN, 0.6f) : FatTheme.BORDER);

        // While renaming, sink the name area so it reads as an input field.
        if (renaming) {
            int nameX = innerX + 64;
            int nextX = innerX + innerW - 61;
            FatTheme.panel(g, nameX, presetRowY + 3, nextX - nameX, 14,
                    0xFF0E0E12, FatTheme.alpha(FatTheme.GREEN, 0.45f));
        }

        // Keycap deck - a recessed strip the caps sit in.
        int deckY = capRowY - 6;
        int deckH = CAP_H + CAP_DEPTH + 11 + 8;
        FatTheme.panel(g, innerX, deckY, innerW, deckH, FatTheme.CARD_SUNK, FatTheme.BORDER_SOFT);

        // Allow-list well.
        FatTheme.panel(g, innerX, listY, innerW, listH, FatTheme.CARD_SUNK, FatTheme.BORDER_SOFT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 1.21.11 renders a screen in one pass, not two: super.render() calls
        // renderBackground() (our card) and *then* walks the widgets. So all of
        // our own chrome has to be drawn afterwards or the card would paint over
        // it. Nothing here overlaps a widget, so drawing it last is visually
        // identical to main's before/after split across the two extract passes.
        super.render(g, mouseX, mouseY, delta);

        drawHeader(g);
        drawPresetRow(g);
        drawSlotSectionLabel(g);
        drawDetailSection(g, mouseX, mouseY);
        drawFooter(g);
    }

    private void drawHeader(GuiGraphics g) {
        int logoX = cardX + PAD;
        int logoY = cardY + 13;
        FatTheme.logoMark(g, logoX, logoY, 11, 9, 4);

        int textX = logoX + FatTheme.logoWidth(11) + 10;
        int fatW = this.font.width("FAT");
        g.drawString(this.font, "FAT", textX, cardY + 13, FatTheme.TEXT, true);
        g.drawString(this.font, "FINGERT", textX + fatW, cardY + 13, FatTheme.GREEN, true);

        boolean on = Fatfingert.isEnabled();
        int guarded = Fatfingert.guardedSlotCount();
        String tail = guarded + " slot" + (guarded == 1 ? "" : "s") + " guarded";
        String full = on ? "misinput insurance · " + tail : "standing down · nothing is blocked";
        String brief = on ? tail : "nothing is blocked";

        // Keep the subtitle clear of the master switch: drop to the short form
        // before resorting to chopping a word in half.
        int subLimit = Math.max(0, (cardX + cardW - PAD - MASTER_W - 6) - textX);
        String sub = this.font.width(full) <= subLimit ? full : brief;
        g.drawString(this.font, this.font.plainSubstrByWidth(sub, subLimit),
                textX, cardY + 25, on ? FatTheme.TEXT_DIM : FatTheme.RED, false);
    }

    private void drawPresetRow(GuiGraphics g) {
        // The name itself is a widget (button, or an edit box while renaming).
        g.drawString(this.font, "PRESET", innerX + 8, presetRowY + 7, FatTheme.TEXT_MUTED, false);
    }

    private void drawSlotSectionLabel(GuiGraphics g) {
        FatTheme.accentBar(g, innerX, slotLabelY, 8, FatTheme.GREEN);
        g.drawString(this.font, "SLOTS", innerX + 6, slotLabelY, FatTheme.TEXT_DIM, false);

        String hint = selectedSlot == null ? "click a key to edit it" : Fatfingert.prettySlot(selectedSlot);
        int w = this.font.width(hint);
        g.drawString(this.font, hint, innerX + innerW - w, slotLabelY, FatTheme.TEXT_MUTED, false);
    }

    private void drawDetailSection(GuiGraphics g, int mouseX, int mouseY) {
        if (selectedSlot == null) {
            g.drawCenteredString(this.font,
                    Component.literal("No key selected"),
                    cardX + cardW / 2, detailHeaderY + 1, FatTheme.TEXT_DIM);
            g.drawCenteredString(this.font,
                    Component.literal("Pick one above to choose what may sit in it"),
                    cardX + cardW / 2, listY + listH / 2 - 4, FatTheme.TEXT_MUTED);
            return;
        }

        FatTheme.accentBar(g, innerX, detailHeaderY, 8, FatTheme.AMBER);
        g.drawString(this.font, Fatfingert.prettySlot(selectedSlot).toUpperCase() + " ACCEPTS",
                innerX + 6, detailHeaderY, FatTheme.TEXT_DIM, false);

        List<String> rule = Fatfingert.ruleFor(selectedSlot);
        if (rule == null) {
            g.drawCenteredString(this.font, Component.literal("Unguarded — anything may go here"),
                    cardX + cardW / 2, listY + listH / 2 - 8, FatTheme.TEXT_MUTED);
            g.drawCenteredString(this.font, Component.literal("Add an item to lock it down"),
                    cardX + cardW / 2, listY + listH / 2 + 2, FatTheme.TEXT_MUTED);
            return;
        }

        clampScroll(rule.size());
        boolean scrolls = rule.size() > itemsPerPage;
        int visibleEnd = Math.min(rule.size(), scrollOffset + itemsPerPage);
        int rowRight = innerX + innerW - (scrolls ? 8 : 2);

        for (int i = scrollOffset; i < visibleEnd; i++) {
            String itemId = rule.get(i);
            int rowY = listY + 2 + (i - scrollOffset) * ROW_H;
            boolean hovered = mouseX >= innerX && mouseX < rowRight
                            && mouseY >= rowY && mouseY < rowY + ROW_H;

            // Stop short of the remove button, which is a widget drawn underneath.
            int stripeW = rowRight - 20 - (innerX + 2);
            if (hovered) {
                FatTheme.roundRect(g, innerX + 2, rowY, stripeW, ROW_H - 1, 0x12FFFFFF);
            } else if ((i & 1) == 1) {
                FatTheme.roundRect(g, innerX + 2, rowY, stripeW, ROW_H - 1, 0x08FFFFFF);
            }

            ItemStack icon = Fatfingert.stackFor(itemId);
            if (!icon.isEmpty()) {
                g.renderItem(icon, innerX + 5, rowY + 2);
            }

            g.drawString(this.font, Fatfingert.itemName(itemId), innerX + 26, rowY + 2, FatTheme.TEXT, false);

            String shortId = itemId.startsWith("minecraft:") ? itemId.substring(10) : itemId;
            int available = rowRight - 22 - (innerX + 26);
            g.drawString(this.font, this.font.plainSubstrByWidth(shortId, available),
                    innerX + 26, rowY + 11, FatTheme.TEXT_MUTED, false);
        }

        if (scrolls) {
            FatTheme.scrollbar(g, innerX + innerW - 6, listY + 3, listH - 6,
                    rule.size(), itemsPerPage, scrollOffset);
        }
    }

    private void drawFooter(GuiGraphics g) {
        int y = footerY + 12;
        // Miniature version of the logo as a maker's mark.
        FatTheme.keycap(g, cardX + PAD, y - 1, 5, 4, 2, true, FatTheme.Tone.GREEN, false);
        FatTheme.keycap(g, cardX + PAD + 7, y - 1, 5, 4, 2, false, FatTheme.Tone.RED, false);

        g.drawString(this.font, "made by " + Fatfingert.AUTHOR, cardX + PAD + 18, y, FatTheme.TEXT_DIM, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (renaming) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        if (renaming && nameBox != null) {
            if (focusNameBox) {
                setFocused(nameBox);
                focusNameBox = false;
            } else if (!nameBox.isFocused()) {
                // Clicking away from the name box commits the edit.
                commitRename();
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedSlot != null && mouseY >= listY && mouseY < listY + listH) {
            List<String> rule = Fatfingert.ruleFor(selectedSlot);
            if (rule != null && rule.size() > itemsPerPage) {
                int maxOffset = rule.size() - itemsPerPage;
                int next = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(verticalAmount)));
                if (next != scrollOffset) {
                    scrollOffset = next;
                    rebuildWidgets();
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        commitRename();
        this.minecraft.setScreen(parent);
    }
}
