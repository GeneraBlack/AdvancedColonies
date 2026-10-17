package com.advancedcolonies.mixin.common.leisure;

import com.advancedcolonies.common.leisure.CitizenMournHelper;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.minimal.EntityAIMournCitizen;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIMournCitizen} that ends mourning once the configured
 * {@code maxMourningDurationSeconds} has elapsed so citizens return to work instead of
 * losing an entire day.
 */
@Mixin(value = EntityAIMournCitizen.class, remap = false)
public abstract class EntityAIMournCitizenMixin {

    @Shadow
    @Final
    private EntityCitizen citizen;

    @Shadow
    public abstract void reset();

    @Inject(method = "decide", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onDecideHead(final CallbackInfoReturnable<IState> cir) {
        if (CitizenMournHelper.checkAndCompleteMourningIfExpired(this.citizen)) {
            this.reset();
            cir.setReturnValue(CitizenAIState.IDLE);
        }
    }
}
