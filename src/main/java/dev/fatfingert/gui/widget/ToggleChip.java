package dev.fatfingert.gui.widget;

import dev.fatfingert.gui.FatTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A labelled row with a sliding ON/OFF pill on the right. Reads its own state
 * every frame, so callers only have to flip the underlying config value.
 */
public class ToggleChip extends AbstractWidget {

    private static final int PILL_W = 22;
    private static final int PILL_H = 12;
    private static final int TOOLTIP_W = 200;

    private final BooleanSupplier state;
    private final Runnable onToggle;
    private final int accent;
    private final Component label;
    private final Component description;
    private Component offLabel;
    private Component shortLabel;

    public ToggleChip(int x, int y, int w, int h, Component label, Component description,
                      int accent, BooleanSupplier state, Runnable onToggle) {
        super(x, y, w, h, label);
        this.label = label;
        this.description = description;
        this.accent = accent;
        this.state = state;
        this.onToggle = onToggle;
    }

    /** Label to show instead while the chip is off. Returns {@code this} for chaining. */
    public ToggleChip offLabel(Component offLabel) {
        this.offLabel = offLabel;
        return this;
    }

    /** Label to fall back to when the chip is too narrow for the full one. */
    public ToggleChip shortLabel(Component shortLabel) {
        this.shortLabel = shortLabel;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean on = state.getAsBoolean();
        boolean hot = isHoveredOrFocused();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        Component current = on || offLabel == null ? label : offLabel;
        if (getMessage() != current) setMessage(current);

        FatTheme.panelGradient(g, x, y, w, h,
                hot ? FatTheme.lighten(FatTheme.CARD_RAISED, 0.05f) : FatTheme.CARD_RAISED,
                FatTheme.CARD,
                on ? FatTheme.alpha(accent, 0.55f) : (hot ? FatTheme.BORDER_LIT : FatTheme.BORDER));

        if (on) {
            FatTheme.accentBar(g, x + 1, y + 3, h - 6, accent);
        }

        // Text runs from the accent bar to just short of the pill.
        int textRoom = w - PILL_W - 15;
        Component shown = font.width(current) > textRoom && shortLabel != null ? shortLabel : current;
        int textY = y + (h - font.lineHeight) / 2 + 1;
        g.text(font, font.plainSubstrByWidth(shown.getString(), textRoom), x + 7, textY,
                on ? FatTheme.TEXT : FatTheme.TEXT_DIM, false);

        // Pill
        int pillX = x + w - PILL_W - 5;
        int pillY = y + (h - PILL_H) / 2;
        FatTheme.roundRect(g, pillX, pillY, PILL_W, PILL_H,
                on ? FatTheme.alpha(accent, 0.35f) : FatTheme.CARD_SUNK);
        FatTheme.outlineGlow(g, pillX, pillY, PILL_W, PILL_H,
                on ? FatTheme.alpha(accent, 0.75f) : FatTheme.BORDER);

        int knobX = on ? pillX + PILL_W - 9 : pillX + 2;
        FatTheme.roundRect(g, knobX, pillY + 2, 7, PILL_H - 4,
                on ? accent : FatTheme.SLATE);

        if (hot && description != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(current.getVisualOrderText());
            lines.addAll(font.split(description, TOOLTIP_W));
            g.setTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onToggle.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
