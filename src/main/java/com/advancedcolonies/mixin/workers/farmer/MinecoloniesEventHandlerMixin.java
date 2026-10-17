package com.advancedcolonies.mixin.workers.farmer;

import com.advancedcolonies.workers.farmer.AgricultureWorkHelper;
import com.minecolonies.core.event.EventHandler;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin Mixin hook on {@link EventHandler#onCropTrample(BlockEvent.FarmlandTrampleEvent)}
 * that extends the {@code SOFT_SHOES} University research to all colony citizens
 * (Guards, Couriers, Builders, Visitors) instead of only the Farmer.
 */
@Mixin(value = EventHandler.class, remap = false)
public abstract class MinecoloniesEventHandlerMixin {

    @Inject(method = "onCropTrample", at = @At("HEAD"), cancellable = true)
    private static void advancedColonies$onCropTrample(
            final BlockEvent.FarmlandTrampleEvent event,
            final CallbackInfo ci) {

        if (event != null && AgricultureWorkHelper.shouldPreventCitizenFarmlandTrample(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
            ci.cancel();
        }
    }
}
