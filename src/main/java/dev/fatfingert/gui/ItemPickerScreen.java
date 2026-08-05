package dev.fatfingert.gui;

import dev.fatfingert.Fatfingert;
import dev.fatfingert.FatfingertConfig;
import dev.fatfingert.gui.widget.FatButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Grid picker for choosing which items a slot will accept. */
public class ItemPickerScreen extends Screen {

    private static final int COLUMNS = 10;
    private static final int CELL = 22;
    private static final int PAD = 14;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 32;

    private final Screen parent;
    private final String slotKey;

    private EditBox searchBox;
    private List<Item> filtered = new ArrayList<>();
    private String lastSearch = "";
    private int scrollRow = 0;

    private int cardX, cardY, cardW, cardH;
    private int innerX, innerW;
    private int searchY, gridX, gridY, rows;
    private int footerY;

    public ItemPickerScreen(Screen parent, String slotKey) {
        super(Component.literal("Add item"));
        this.parent = parent;
        this.slotKey = slotKey;
    }

    @Override
    protected void init() {
        super.init();

        cardW = Math.min(COLUMNS * CELL + PAD * 2 + 10, this.width - 16);
        innerW = cardW - PAD * 2;

        int fixedH = HEADER_H + 10 + 20 + 12 + FOOTER_H;
        int maxCardH = this.height - 16;
        rows = Math.max(2, Math.min(7, (maxCardH - fixedH - 4) / CELL));
        cardH = fixedH + rows * CELL + 4;

        cardX = (this.width - cardW) / 2;
        cardY = Math.max(8, (this.height - cardH) / 2);
        innerX = cardX + PAD;

        searchY = cardY + HEADER_H + 10;
        gridY = searchY + 20 + 12;
        gridX = cardX + (cardW - COLUMNS * CELL) / 2;
        footerY = cardY + cardH - FOOTER_H;

        searchBox = new EditBox(this.font, innerX + 18, searchY + 6, innerW - 24, 12,
                Component.literal("Search items"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        // EditBox text colours are ARGB - without the alpha byte the text is
        // drawn fully transparent.
        searchBox.setTextColor(FatTheme.TEXT);
        searchBox.setHint(Component.literal("Search items...")
                .withStyle(s -> s.withColor(FatTheme.TEXT_MUTED & 0xFFFFFF)));
        searchBox.setValue(lastSearch);
        searchBox.setResponder(text -> {
            if (!text.equals(lastSearch)) {
                lastSearch = text;
                scrollRow = 0;
                refilter();
            }
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        addRenderableWidget(new FatButton(
                cardX + PAD, footerY + 6, 66, 20,
                Component.literal("‹ Back"), FatButton.Style.NEUTRAL,
                this::onClose
        ));

        refilter();
    }

    private void refilter() {
        String query = lastSearch.toLowerCase().trim();
        List<Item> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            if (!query.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                String name = item.getName(item.getDefaultInstance()).getString().toLowerCase();
                if (!id.contains(query) && !name.contains(query)) continue;
            }
            out.add(item);
        }
        filtered = out;
        scrollRow = Math.min(scrollRow, maxScrollRow());
    }

    private int totalRows() {
        return (filtered.size() + COLUMNS - 1) / COLUMNS;
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - rows);
    }

    private List<String> currentRule() {
        List<String> rule = Fatfingert.ruleFor(slotKey);
        return rule == null ? List.of() : rule;
    }

    // ---- rendering -----------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);

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

        // Search well.
        boolean focused = searchBox != null && searchBox.isFocused();
        FatTheme.panel(g, innerX, searchY, innerW, 20, FatTheme.CARD_SUNK,
                focused ? FatTheme.alpha(FatTheme.GREEN, 0.6f) : FatTheme.BORDER);

        // Grid well.
        FatTheme.panel(g, gridX - 3, gridY - 3, COLUMNS * CELL + 6, rows * CELL + 6,
                FatTheme.CARD_SUNK, FatTheme.BORDER_SOFT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Header
        FatTheme.logoMark(g, cardX + PAD, cardY + 12, 9, 8, 3);
        int textX = cardX + PAD + FatTheme.logoWidth(9) + 9;
        g.text(this.font, "ADD TO " + Fatfingert.prettySlot(slotKey).toUpperCase(),
                textX, cardY + 12, FatTheme.TEXT, true);
        g.text(this.font, filtered.size() + " item" + (filtered.size() == 1 ? "" : "s") + " match",
                textX, cardY + 24, FatTheme.TEXT_DIM, false);

        // Search glyph + placeholder
        FatTheme.magnifier(g, innerX + 6, searchY + 6, FatTheme.TEXT_MUTED, FatTheme.CARD_SUNK);

        super.extractRenderState(g, mouseX, mouseY, delta);

        drawGrid(g, mouseX, mouseY);

        FatTheme.scrollbar(g, gridX + COLUMNS * CELL + 5, gridY, rows * CELL,
                totalRows(), rows, scrollRow);

        g.text(this.font, "click to add or remove",
                cardX + PAD + 74, footerY + 12, FatTheme.TEXT_MUTED, false);
    }

    private void drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<String> rule = currentRule();
        int start = scrollRow * COLUMNS;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int idx = start + row * COLUMNS + col;
                if (idx >= filtered.size()) return;

                Item item = filtered.get(idx);
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                int x = gridX + col * CELL;
                int y = gridY + row * CELL;

                boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                boolean already = rule.contains(id);

                if (already) {
                    FatTheme.roundRect(g, x + 1, y + 1, CELL - 2, CELL - 2, FatTheme.GREEN_FAINT);
                    FatTheme.outlineGlow(g, x + 1, y + 1, CELL - 2, CELL - 2,
                            FatTheme.alpha(FatTheme.GREEN, 0.8f));
                } else if (hovered) {
                    FatTheme.roundRect(g, x + 1, y + 1, CELL - 2, CELL - 2, 0x22FFFFFF);
                    FatTheme.outlineGlow(g, x + 1, y + 1, CELL - 2, CELL - 2, FatTheme.BORDER_LIT);
                }

                g.item(new ItemStack(item), x + 3, y + 3);

                if (hovered) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(item.getName(item.getDefaultInstance()).copy().withStyle(ChatFormatting.WHITE));
                    lines.add(Component.literal(id).withStyle(ChatFormatting.DARK_GRAY));
                    lines.add(Component.literal(already ? "Already allowed — click to remove" : "Click to allow")
                            .withStyle(already ? ChatFormatting.RED : ChatFormatting.GREEN));
                    g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }

    // ---- input ---------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isHandled) {
        if (!isHandled && event.button() == 0) {
            int start = scrollRow * COLUMNS;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < COLUMNS; col++) {
                    int idx = start + row * COLUMNS + col;
                    if (idx >= filtered.size()) break;

                    int x = gridX + col * CELL;
                    int y = gridY + row * CELL;
                    if (event.x() >= x && event.x() < x + CELL
                            && event.y() >= y && event.y() < y + CELL) {
                        toggleItem(BuiltInRegistries.ITEM.getKey(filtered.get(idx)).toString());
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, isHandled);
    }

    /** Clicking an allowed item again takes it back off the list. */
    private void toggleItem(String itemId) {
        FatfingertConfig cfg = Fatfingert.config();
        FatfingertConfig.Preset preset = cfg.presets.get(cfg.activePreset);
        if (preset == null) return;

        List<String> rule = preset.rules.get(slotKey);
        if (rule != null && rule.contains(itemId)) {
            rule.remove(itemId);
            if (rule.isEmpty()) preset.rules.remove(slotKey);
        } else {
            preset.rules.computeIfAbsent(slotKey, k -> new ArrayList<>()).add(itemId);
        }
        cfg.save();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
