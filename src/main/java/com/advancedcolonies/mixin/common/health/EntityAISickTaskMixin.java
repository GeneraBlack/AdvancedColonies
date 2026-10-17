package com.advancedcolonies.mixin.common.health;

import com.advancedcolonies.common.health.CitizenHealthAndHospitalHelper;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.datalistener.model.Disease;
import com.minecolonies.core.entity.ai.minimal.EntityAISickTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAISickTask} to align recovery checks with configurable
 * health thresholds and allow unhandled Hospital patients to self-heal after a configurable wait.
 */
@Mixin(value = EntityAISickTask.class, remap = false)
public abstract class EntityAISickTaskMixin {

    @Shadow
    @Final
    private EntityCitizen citizen;

    @Shadow
    private void reset() {
    }

    /**
     * Adjusts the spontaneous recovery roll in {@code waitForCure()} to use the configured wait time.
     *
     * @param random the citizen random source
     * @param bound  the vanilla random bound ({@code 60 * 60 * 2})
     * @return the random roll result
     */
    @Redirect(
            method = "waitForCure",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    )
    private int advancedcolonies$adjustSelfHealWaitBound(final RandomSource random, final int bound) {
        final int effectiveBound = CitizenHealthAndHospitalHelper.getPatientSelfHealRandomBound(bound);
        return random.nextInt(effectiveBound);
    }

    /**
     * Ensures disease-free injured citizens remain in or exit the {@code SICK} state according to
     * the configured {@code seekDoctorHealthRatio} and {@code seekDoctorAbsoluteHealth} thresholds.
     *
     * @param cir callback info returnable
     */
    @Inject(method = "checkForCure", at = @At("HEAD"), cancellable = true)
    private void advancedcolonies$checkInjuryRecoveryThreshold(final CallbackInfoReturnable<IState> cir) {
        if (this.citizen == null || this.citizen.getCitizenData() == null) {
            return;
        }
        final Disease disease = this.citizen.getCitizenData().getCitizenDiseaseHandler().getDisease();
        if (disease == null) {
            if (CitizenHealthAndHospitalHelper.isRecoveredFromInjury(this.citizen)) {
                this.reset();
                cir.setReturnValue(CitizenAIState.IDLE);
            } else {
                cir.setReturnValue(EntityAISickTask.DiseaseState.GO_TO_HUT);
            }
        }
    }
}
