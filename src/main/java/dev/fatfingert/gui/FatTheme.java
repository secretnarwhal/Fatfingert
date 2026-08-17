package dev.fatfingert.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The Fatfingert look: palette, rounded panels, and the keycap primitive that
 * the logo, the slot row, and the mod icon are all built from.
 *
 * Everything is drawn with axis-aligned fills, so it renders identically at any
 * GUI scale and needs no textures.
 */
public final class FatTheme {

    private FatTheme() {}

    // ---- palette -----------------------------------------------------

    public static final int SCRIM_TOP   = 0xE6101014;
    public static final int SCRIM_BOT   = 0xF207070A;

    public static final int CARD        = 0xFF1B1B23;
    public static final int CARD_RAISED = 0xFF23232E;
    public static final int CARD_SUNK   = 0xFF141419;

    public static final int BORDER      = 0xFF33333F;
    public static final int BORDER_SOFT = 0xFF2A2A34;
    public static final int BORDER_LIT  = 0xFF4E4E60;

    public static final int TEXT        = 0xFFECECF2;
    public static final int TEXT_DIM    = 0xFF9A9AAA;
    public static final int TEXT_MUTED  = 0xFF666676;

    public static final int GREEN       = 0xFF46D07E;
    public static final int GREEN_MID   = 0xFF2FA862;
    public static final int GREEN_DEEP  = 0xFF1C6B3F;
    public static final int GREEN_FAINT = 0x3346D07E;

    public static final int RED         = 0xFFEE5A4C;
    public static final int RED_MID     = 0xFFBF3C31;
    public static final int RED_DEEP    = 0xFF7E241C;
    public static final int RED_FAINT   = 0x33EE5A4C;

    public static final int AMBER       = 0xFFF2B531;
    public static final int AMBER_DEEP  = 0xFF9A6E14;

    public static final int SLATE       = 0xFF3A3A47;
    public static final int SLATE_MID   = 0xFF2E2E39;
    public static final int SLATE_DEEP  = 0xFF1F1F27;

    // ---- color math --------------------------------------------------

    public static int alpha(int argb, float a) {
        int base = argb & 0x00FFFFFF;
        int aa = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (aa << 24) | base;
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int rr = (int) (((a >>> 16) & 0xFF) + (((b >>> 16) & 0xFF) - ((a >>> 16) & 0xFF)) * t);
        int gg = (int) (((a >>> 8) & 0xFF) + (((b >>> 8) & 0xFF) - ((a >>> 8) & 0xFF)) * t);
        int bb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    public static int lighten(int argb, float amount) {
        return mix(argb, 0xFFFFFFFF, amount);
    }

    public static int darken(int argb, float amount) {
        return mix(argb, 0xFF000000, amount);
    }

    // ---- tooltips ----------------------------------------------------

    /**
     * Queues a multi-line tooltip on the current screen.
     *
     * 1.21.1 widgets have no "draw this later" hook of their own, so anything a
     * widget paints during its own render pass gets covered by whatever renders
     * after it. Handing the lines to the screen instead makes it draw them once
     * every widget is done, which is where a tooltip belongs.
     */
    public static void tooltip(List<Component> lines) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null || lines.isEmpty()) return;

        List<FormattedCharSequence> text = new ArrayList<>(lines.size());
        for (Component line : lines) {
            text.add(line.getVisualOrderText());
        }
        screen.setTooltipForNextRenderPass(text);
    }

    // ---- primitives --------------------------------------------------

    /** Solid rect with 1px clipped corners, which reads as a subtle radius. */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        if (w <= 2 || h <= 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    /** Vertical gradient with the same 1px-clipped corners as {@link #roundRect}. */
    public static void roundGradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        if (w <= 2 || h <= 2) {
            g.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        g.fillGradient(x + 1, y, x + w - 1, y + h, top, bottom);
        g.fillGradient(x, y + 1, x + w, y + h - 1, top, bottom);
    }

    /** 1px border in {@code border}, interior filled with {@code fill}. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        roundRect(g, x, y, w, h, border);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    /** Bordered panel whose interior is a vertical gradient. */
    public static void panelGradient(GuiGraphics g, int x, int y, int w, int h,
                                     int top, int bottom, int border) {
        roundRect(g, x, y, w, h, border);
        roundGradient(g, x + 1, y + 1, w - 2, h - 2, top, bottom);
    }

    /** Hairline used to separate header/footer bands. */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, BORDER_SOFT);
        g.fill(x, y + 1, x + w, y + 2, 0x14FFFFFF);
    }

    /** A short accent bar, used to badge section headings. */
    public static void accentBar(GuiGraphics g, int x, int y, int h, int color) {
        roundRect(g, x, y, 2, h, color);
    }

    /**
     * Vertical scrollbar track + thumb. Draws nothing when everything fits.
     */
    public static void scrollbar(GuiGraphics g, int x, int y, int h,
                                 int total, int visible, int offset) {
        if (total <= visible || h <= 0) return;
        roundRect(g, x, y, 3, h, CARD_SUNK);
        int thumbH = Math.max(12, h * visible / total);
        int span = h - thumbH;
        int maxOffset = total - visible;
        int thumbY = y + (maxOffset <= 0 ? 0 : span * offset / maxOffset);
        roundRect(g, x, thumbY, 3, thumbH, BORDER_LIT);
    }

    // ---- keycaps -----------------------------------------------------

    /** The three colorways a keycap can wear. */
    public enum Tone {
        /** Guarded / committed - the key you meant to hit. */
        GREEN(FatTheme.GREEN, GREEN_MID, GREEN_DEEP),
        /** Blocked / misinput - the key you didn't. */
        RED(FatTheme.RED, RED_MID, RED_DEEP),
        /** Unassigned. */
        SLATE(FatTheme.SLATE, SLATE_MID, SLATE_DEEP);

        public final int light;
        public final int mid;
        public final int deep;

        Tone(int light, int mid, int deep) {
            this.light = light;
            this.mid = mid;
            this.deep = deep;
        }
    }

    /**
     * Draws a keycap as a rectangular prism: a lit top face sitting on a darker
     * front wall. A pressed cap sinks toward its baseline while keeping the same
     * footprint, so a row of caps never shifts as states change.
     *
     * @param x,y     top-left of the cap at rest
     * @param w,capH  size of the cap's top face
     * @param depth   how tall the front wall is when unpressed
     */
    public static void keycap(GuiGraphics g, int x, int y, int w, int capH, int depth,
                              boolean pressed, Tone tone, boolean glow) {
        int baseline = y + capH + depth;
        int capTop = pressed ? y + depth - 1 : y;
        int capBottom = capTop + capH;

        // Contact shadow so the cap sits on the surface rather than floating.
        roundRect(g, x - 1, baseline - 1, w + 2, 2, 0x40000000);

        // Front wall.
        int wallTop = tone.deep;
        int wallBot = darken(tone.deep, 0.35f);
        roundRect(g, x, capBottom - 2, w, baseline - capBottom + 2, wallBot);
        roundGradient(g, x, capBottom - 2, w, Math.max(2, baseline - capBottom), wallTop, wallBot);

        // Cap edge + top face.
        roundRect(g, x, capTop, w, capH, darken(tone.deep, 0.15f));
        roundGradient(g, x + 1, capTop + 1, w - 2, capH - 2,
                pressed ? tone.mid : tone.light,
                pressed ? darken(tone.mid, 0.25f) : tone.mid);

        // Specular line along the top edge.
        if (capH > 3 && w > 4) {
            g.fill(x + 2, capTop + 1, x + w - 2, capTop + 2, lighten(tone.light, pressed ? 0.10f : 0.30f));
        }

        if (glow) {
            outlineGlow(g, x - 1, capTop - 1, w + 2, (baseline - capTop) + 1, alpha(tone.light, 0.55f));
        }
    }

    /**
     * A 8x8 magnifier glyph drawn from fills, so it can't turn into a
     * missing-glyph box the way an exotic unicode character might.
     */
    public static void magnifier(GuiGraphics g, int x, int y, int color, int hollow) {
        roundRect(g, x, y, 6, 6, color);
        roundRect(g, x + 1, y + 1, 4, 4, hollow);
        g.fill(x + 5, y + 5, x + 7, y + 7, color);
    }

    /** 1px outline drawn just outside a rect, used for focus/selection rings. */
    public static void outlineGlow(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * The Fatfingert mark: two keycaps side by side. The left one is pressed and
     * green (the input you wanted), the right stands tall and red (the one that
     * got blocked).
     */
    public static void logoMark(GuiGraphics g, int x, int y, int capW, int capH, int depth) {
        int gap = Math.max(2, capW / 6);
        keycap(g, x, y, capW, capH, depth, true, Tone.GREEN, false);
        keycap(g, x + capW + gap, y, capW, capH, depth, false, Tone.RED, false);
    }

    /** Width the logo mark occupies for a given cap width. */
    public static int logoWidth(int capW) {
        return capW * 2 + Math.max(2, capW / 6);
    }

    /** Height the logo mark occupies. */
    public static int logoHeight(int capH, int depth) {
        return capH + depth;
    }
}
