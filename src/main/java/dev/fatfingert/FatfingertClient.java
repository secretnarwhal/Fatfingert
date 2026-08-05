package dev.fatfingert;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class FatfingertClient implements ClientModInitializer {

    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        Fatfingert.loadConfig();

        // Unbound by default; assign it in Options > Controls if you want a hotkey.
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fatfingert.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                boolean enabled = Fatfingert.toggle();
                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.literal("Fatfingert " + (enabled ? "armed" : "off"))
                                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                    );
                }
            }
        });
    }
}
