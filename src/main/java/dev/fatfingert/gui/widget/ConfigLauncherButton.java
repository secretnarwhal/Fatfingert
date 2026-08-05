package dev.fatfingert.gui.widget;

import dev.fatfingert.Fatfingert;
import dev.fatfingert.gui.FatTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * The little button tucked into the inventory screen that opens Fatfingert.
 * It draws the logo itself - two keycaps, one pressed and green, one tall and
 * red - so the mark doubles as the icon.
 */
public class ConfigLauncherButton extends AbstractWidget {

    private final Runnable onPress;

    public ConfigLauncherButton(int x, int y, Runnable onPress) {
        super(x, y, 16, 14, Component.literal("Fatfingert"));
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean hot = isHoveredOrFocused();
        int x = getX();
        int y = getY();

        FatTheme.panel(g, x, y, getWidth(), getHeight(),
                hot ? FatTheme.CARD_RAISED : FatTheme.CARD,
                hot ? FatTheme.BORDER_LIT : FatTheme.BORDER);

        // Logo mark, scaled down to fit the button.
        FatTheme.keycap(g, x + 3, y + 3, 4, 4, 2, true, FatTheme.Tone.GREEN, false);
        FatTheme.keycap(g, x + 9, y + 3, 4, 4, 2, false, FatTheme.Tone.RED, false);

        if (hot) {
            boolean on = Fatfingert.isEnabled();
            g.setTooltipForNextFrame(Minecraft.getInstance().font,
                    List.of(
                            Component.literal("Fatfingert").withStyle(ChatFormatting.WHITE),
                            Component.literal(on
                                    ? Fatfingert.guardedSlotCount() + " slot(s) guarded"
                                    : "Currently off")
                                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)
                    ),
                    Optional.empty(), mouseX, mouseY);
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
