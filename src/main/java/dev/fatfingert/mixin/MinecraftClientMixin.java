package dev.fatfingert.mixin;

import dev.fatfingert.Fatfingert;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs right before vanilla processes input events each tick. If the pending
 * F-key (swap hands) or Q-key (drop, only when blockEmptying is on) press
 * would violate a slot reservation, we consume the key press so vanilla never
 * sends the packet. This only ever CANCELS the player's own input.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Shadow public LocalPlayer player;

    @Shadow @Final public Options options;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void fatfingert$filterInputs(CallbackInfo ci) {
        if (player == null || !Fatfingert.isEnabled()) return;

        String swapReason = Fatfingert.handSwapBlockReason(player);
        if (swapReason != null) {
            boolean pressed = false;
            while (options.keySwapOffhand.consumeClick()) pressed = true;
            if (pressed) Fatfingert.notifyBlocked(player, swapReason);
        }

        String dropReason = Fatfingert.dropBlockReason(player);
        if (dropReason != null) {
            boolean pressed = false;
            while (options.keyDrop.consumeClick()) pressed = true;
            if (pressed) Fatfingert.notifyBlocked(player, dropReason);
        }
    }
}
