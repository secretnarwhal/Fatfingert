package dev.fatfingert;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fatfingert.gui.FatfingertConfigScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hotkeys for every Fatfingert control that means something outside its menu.
 * They are plain vanilla key mappings: unbound by default, saved in options.txt,
 * and listed both on the Keybinds screen and under Options > Controls. Like any
 * vanilla hotkey they fire in-game, not while a screen is open.
 */
public final class FatfingertKeys {

    public record Binding(KeyMapping mapping, Runnable action) {}

    private static final List<Binding> BINDINGS = new ArrayList<>();

    private FatfingertKeys() {}

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(Fatfingert.MOD_ID, "main"));

        // "toggle" keeps its original id so a key bound before this list existed still works.
        add("toggle", category, () -> announce("Fatfingert", Fatfingert.toggle()));
        add("open_menu", category, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(new FatfingertConfigScreen(mc.gui.screen()));
        });
        add("toggle_inventory_button", category, () -> {
            boolean shown = Fatfingert.toggleInventoryButton();
            overlay("Inventory button " + (shown ? "shown" : "hidden"), shown);
        });
        add("toggle_lock_contents", category, () -> announce("Lock Contents", Fatfingert.toggleLockContents()));
        add("toggle_strict_shift", category, () -> announce("Strict Shift", Fatfingert.toggleStrictShift()));
        add("next_preset", category, () -> presetChanged(Fatfingert.config().cyclePreset(1)));
        add("previous_preset", category, () -> presetChanged(Fatfingert.config().cyclePreset(-1)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (Binding binding : BINDINGS) {
                while (binding.mapping().consumeClick()) {
                    binding.action().run();
                }
            }
        });
    }

    /** Every hotkey, in the order the Keybinds screen lists them. */
    public static List<Binding> bindings() {
        return Collections.unmodifiableList(BINDINGS);
    }

    private static void add(String id, KeyMapping.Category category, Runnable action) {
        KeyMapping mapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + Fatfingert.MOD_ID + "." + id,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category
        ));
        BINDINGS.add(new Binding(mapping, action));
    }

    private static void announce(String setting, boolean on) {
        overlay(setting + (on ? " on" : " off"), on);
    }

    private static void presetChanged(String presetKey) {
        overlay("Preset: " + Fatfingert.prettyPreset(presetKey), true);
    }

    private static void overlay(String text, boolean positive) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal(text)
                    .withStyle(positive ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
    }
}
