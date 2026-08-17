package dev.fatfingert.gui.widget;

import dev.fatfingert.gui.FatTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * A labelled row with a sliding ON/OFF pill on the right. Reads its own state
 * every frame, so callers only have to flip the underlying config value.
 */
public class ToggleChip extends AbstractWidget {

    private static final int PILL_W = 22;
    private static final int PILL_H = 12;

    private final BooleanSupplier state;
    private final Runnable onToggle;
    private final int accent;
    private final Component description;

    public ToggleChip(int x, int y, int w, int h, Component label, Component description,
                      int accent, BooleanSupplier state, Runnable onToggle) {
        super(x, y, w, h, label);
        this.description = description;
        this.accent = accent;
        this.state = state;
        this.onToggle = onToggle;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean on = state.getAsBoolean();
        boolean hot = isHoveredOrFocused();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        FatTheme.panelGradient(g, x, y, w, h,
                hot ? FatTheme.lighten(FatTheme.CARD_RAISED, 0.05f) : FatTheme.CARD_RAISED,
                FatTheme.CARD,
                on ? FatTheme.alpha(accent, 0.55f) : (hot ? FatTheme.BORDER_LIT : FatTheme.BORDER));

        if (on) {
            FatTheme.accentBar(g, x + 1, y + 3, h - 6, accent);
        }

        int textY = y + (h - font.lineHeight) / 2 + 1;
        g.drawString(font, getMessage(), x + 7, textY, on ? FatTheme.TEXT : FatTheme.TEXT_DIM, false);

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
            g.setTooltipForNextFrame(font, List.of(getMessage(), description), Optional.empty(), mouseX, mouseY);
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
