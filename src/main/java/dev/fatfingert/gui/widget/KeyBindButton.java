package dev.fatfingert.gui.widget;

import dev.fatfingert.gui.FatTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Shows the key a mapping is bound to. Click it and the owning screen waits for
 * the next key press. A key that some other control also uses is drawn in red,
 * with the clash spelled out in its tooltip - the same warning vanilla gives.
 */
public class KeyBindButton extends AbstractWidget {

    private final KeyMapping mapping;
    private final BooleanSupplier capturing;
    private final Runnable onPress;

    public KeyBindButton(int x, int y, int w, int h, KeyMapping mapping,
                         BooleanSupplier capturing, Runnable onPress) {
        super(x, y, w, h, Component.translatable(mapping.getName()));
        this.mapping = mapping;
        this.capturing = capturing;
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean waiting = capturing.getAsBoolean();
        boolean hot = isHoveredOrFocused();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        List<Component> clashes = new ArrayList<>();
        if (!mapping.isUnbound()) {
            for (KeyMapping other : mc.options.keyMappings) {
                if (other != mapping && mapping.same(other)) {
                    clashes.add(Component.translatable(other.getName()));
                }
            }
        }

        int border = waiting ? FatTheme.AMBER
                : !clashes.isEmpty() ? FatTheme.alpha(FatTheme.RED, 0.7f)
                : hot ? FatTheme.BORDER_LIT : FatTheme.BORDER;
        FatTheme.panelGradient(g, x, y, w, h,
                hot ? FatTheme.lighten(FatTheme.CARD_RAISED, 0.06f) : FatTheme.CARD_RAISED,
                hot ? FatTheme.CARD_RAISED : FatTheme.CARD,
                border);

        String text;
        int color;
        if (waiting) {
            text = "> press a key <";
            color = FatTheme.AMBER;
        } else {
            text = mapping.getTranslatedKeyMessage().getString();
            color = mapping.isUnbound() ? FatTheme.TEXT_MUTED
                    : !clashes.isEmpty() ? FatTheme.RED
                    : FatTheme.TEXT;
        }
        text = font.plainSubstrByWidth(text, w - 6);
        g.text(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2 + 1, color, false);

        if (hot && !waiting && !clashes.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Also used by:").withStyle(ChatFormatting.RED));
            for (Component clash : clashes) {
                lines.add(Component.literal(" ").append(clash).withStyle(ChatFormatting.GRAY));
            }
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
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
