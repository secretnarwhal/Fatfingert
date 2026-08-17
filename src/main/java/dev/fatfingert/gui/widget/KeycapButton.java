package dev.fatfingert.gui.widget;

import dev.fatfingert.Fatfingert;
import dev.fatfingert.gui.FatTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One inventory slot, drawn as a keycap. A guarded slot is pressed and green,
 * an unguarded one stands tall in slate - the same language as the logo.
 */
public class KeycapButton extends AbstractWidget {

    private final String slotKey;
    private final String label;
    private final int capH;
    private final int depth;
    private final Supplier<String> selectedSlot;
    private final Consumer<String> onSelect;

    public KeycapButton(int x, int y, int w, int capH, int depth,
                        String slotKey, String label,
                        Supplier<String> selectedSlot, Consumer<String> onSelect) {
        super(x, y, w, capH + depth + 11, Component.literal(label));
        this.slotKey = slotKey;
        this.label = label;
        this.capH = capH;
        this.depth = depth;
        this.selectedSlot = selectedSlot;
        this.onSelect = onSelect;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();

        List<String> rule = Fatfingert.ruleFor(slotKey);
        boolean guarded = rule != null;
        boolean selected = slotKey.equals(selectedSlot.get());
        boolean hot = isHoveredOrFocused();

        FatTheme.Tone tone = guarded ? FatTheme.Tone.GREEN : FatTheme.Tone.SLATE;

        // A hovered-but-unguarded cap lifts slightly toward the "live" colour.
        FatTheme.keycap(g, x, y, w, capH, depth, guarded, tone, selected);

        // Item icon rides on the cap face.
        int capTop = guarded ? y + depth - 1 : y;
        if (guarded) {
            ItemStack icon = Fatfingert.stackFor(rule.get(0));
            if (!icon.isEmpty()) {
                g.renderItem(icon, x + (w - 16) / 2, capTop + (capH - 16) / 2);
            }
            if (rule.size() > 1) {
                g.drawString(font, "+" + (rule.size() - 1),
                        x + w - font.width("+" + (rule.size() - 1)) - 2,
                        capTop + capH - font.lineHeight - 1,
                        FatTheme.TEXT, true);
            }
        } else if (hot) {
            FatTheme.outlineGlow(g, x - 1, capTop - 1, w + 2, capH + depth + 1,
                    FatTheme.alpha(FatTheme.BORDER_LIT, 0.9f));
        }

        // Slot number / OH under the cap.
        int labelColor = selected ? FatTheme.AMBER : guarded ? FatTheme.GREEN : FatTheme.TEXT_MUTED;
        g.drawCenteredString(font, Component.literal(label), x + w / 2, y + capH + depth + 3, labelColor);

        if (hot) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(Fatfingert.prettySlot(slotKey)).withStyle(ChatFormatting.WHITE));
            if (rule == null) {
                lines.add(Component.literal("Unguarded - anything goes here")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.literal("Only accepts:").withStyle(ChatFormatting.DARK_GRAY));
                for (String id : rule) {
                    lines.add(Component.literal(" " + Fatfingert.itemName(id)).withStyle(ChatFormatting.GREEN));
                }
            }
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onSelect.accept(slotKey);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
