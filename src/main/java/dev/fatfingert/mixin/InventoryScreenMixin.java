package dev.fatfingert.mixin;

import dev.fatfingert.Fatfingert;
import dev.fatfingert.gui.FatfingertConfigScreen;
import dev.fatfingert.gui.widget.ConfigLauncherButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Fatfingert button to the player's inventory screen.
 * Purely cosmetic/client-side - nothing is sent to the server.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {

    private InventoryScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fatfingert$addConfigButton(CallbackInfo ci) {
        // init() runs again whenever the screen comes back from the Fatfingert
        // panel, so flipping the setting there takes effect straight away.
        if (!Fatfingert.config().showInventoryButton) return;

        int x = this.leftPos + 125;
        int y = this.topPos + 62;

        this.addRenderableWidget(new ConfigLauncherButton(x, y, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new FatfingertConfigScreen(mc.screen));
        }));
    }
}
