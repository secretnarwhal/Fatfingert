package dev.fatfingert.gui.widget;

import dev.fatfingert.gui.FatTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/** A flat, bordered button in the Fatfingert palette. */
public class FatButton extends AbstractWidget {

    public enum Style {
        /** Filled green - the affirmative action on a screen. */
        PRIMARY,
        /** Bordered slate - everything else. */
        NEUTRAL,
        /** Red, for removals. */
        DANGER,
        /** Borderless, for quiet actions like "back". */
        GHOST
    }

    private final Runnable onPress;
    private final Style style;
    private List<Component> hint;

    public FatButton(int x, int y, int w, int h, Component label, Style style, Runnable onPress) {
        super(x, y, w, h, label);
        this.style = style;
        this.onPress = onPress;
    }

    /** Adds a hover tooltip. Returns {@code this} so it can be chained inline. */
    public FatButton hint(Component... lines) {
        this.hint = List.of(lines);
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hot = isHoveredOrFocused() && this.active;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        int textColor;

        switch (style) {
            case PRIMARY -> {
                int top = this.active ? (hot ? FatTheme.lighten(FatTheme.GREEN, 0.12f) : FatTheme.GREEN)
                                      : FatTheme.SLATE;
                int bot = this.active ? (hot ? FatTheme.GREEN : FatTheme.GREEN_MID)
                                      : FatTheme.SLATE_MID;
                FatTheme.panelGradient(g, x, y, w, h, top, bot,
                        this.active ? FatTheme.GREEN_DEEP : FatTheme.BORDER);
                textColor = this.active ? 0xFF08160E : FatTheme.TEXT_MUTED;
            }
            case DANGER -> {
                FatTheme.panelGradient(g, x, y, w, h,
                        hot ? FatTheme.alpha(FatTheme.RED, 0.30f) : FatTheme.CARD_RAISED,
                        hot ? FatTheme.alpha(FatTheme.RED_MID, 0.30f) : FatTheme.CARD,
                        hot ? FatTheme.RED : FatTheme.BORDER);
                textColor = hot ? FatTheme.RED : FatTheme.TEXT_DIM;
            }
            case GHOST -> {
                if (hot) {
                    FatTheme.roundRect(g, x, y, w, h, 0x18FFFFFF);
                }
                textColor = hot ? FatTheme.TEXT : FatTheme.TEXT_DIM;
            }
            default -> {
                FatTheme.panelGradient(g, x, y, w, h,
                        hot ? FatTheme.lighten(FatTheme.CARD_RAISED, 0.06f) : FatTheme.CARD_RAISED,
                        hot ? FatTheme.CARD_RAISED : FatTheme.CARD,
                        hot ? FatTheme.BORDER_LIT : FatTheme.BORDER);
                textColor = this.active ? (hot ? FatTheme.TEXT : FatTheme.TEXT_DIM) : FatTheme.TEXT_MUTED;
            }
        }

        // The drop shadow is a darkened copy of the text colour, so under the dark
        // text on a filled button it just reads as doubled text. Draw that one flat.
        int textY = y + (h - font.lineHeight) / 2 + 1;
        int textX = x + (w - font.width(getMessage())) / 2;
        g.text(font, getMessage(), textX, textY, textColor, style != Style.PRIMARY);

        if (hot && hint != null) {
            g.setTooltipForNextFrame(font, hint, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
