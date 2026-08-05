package dev.fatfingert.mixin;

import dev.fatfingert.Fatfingert;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every inventory-screen interaction (clicks, shift-clicks, number-key swaps,
 * F-key swap while hovering a slot, drags, throws) funnels through
 * handleContainerInput before the packet is sent. If it would put a disallowed
 * item into a guarded slot, we cancel it client-side - the click simply
 * doesn't happen.
 */
@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void fatfingert$onClickSlot(int containerId, int slotId, int button,
                                        ContainerInput clickType, Player player,
                                        CallbackInfo ci) {
        if (!Fatfingert.isEnabled() || player == null) return;

        String reason = Fatfingert.clickBlockReason(player, slotId, button, clickType);
        if (reason != null) {
            Fatfingert.notifyBlocked(player, reason);
            ci.cancel();
        }
    }
}
