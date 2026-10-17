package com.advancedcolonies.mixin.workers.healer;

import com.advancedcolonies.workers.healer.HealerWorkOptimizer;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenDiseaseHandler;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingHospital;
import com.minecolonies.core.colony.jobs.JobHealer;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkHealer;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIWorkHealer} delegating to {@link HealerWorkOptimizer}
 * to ensure injured ({@code isHurt()}) patients are registered and healed, and to fix the
 * negative-sign healing formula in {@code curePlayer()} and {@code wander()}.
 */
@Mixin(value = EntityAIWorkHealer.class, remap = false)
public abstract class EntityAIWorkHealerMixin extends AbstractEntityAIBasic<JobHealer, BuildingHospital> {

    protected EntityAIWorkHealerMixin(@NotNull final JobHealer job) {
        super(job);
    }

    /**
     * Registers injured citizens inside the Hospital building as patients during {@code decide()}.
     */
    @Inject(
            method = "decide",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/colony/buildings/workerbuildings/BuildingHospital;getPatients()Ljava/util/List;"
            )
    )
    private void advancedcolonies$registerInjuredPatients(final CallbackInfoReturnable<IAIState> cir) {
        HealerWorkOptimizer.registerInjuredPatientsInHospital(this.building, this.world);
    }

    /**
     * Redirects {@link ICitizenDiseaseHandler#isSick()} in {@code decide()}, {@code requestCure()},
     * and {@code cure()} so that injured ({@code isHurt()}) patients with {@code disease == null}
     * are not discarded and instead reach the existing injury-healing branches.
     */
    @Redirect(
            method = {"decide", "requestCure", "cure"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/entity/citizen/citizenhandlers/ICitizenDiseaseHandler;isSick()Z"
            )
    )
    private boolean advancedcolonies$treatInjuredAsNeedingCare(final ICitizenDiseaseHandler handler) {
        return HealerWorkOptimizer.needsHospitalTreatment(handler);
    }

    /**
     * Fixes the inverted building-level subtraction in {@code curePlayer()}.
     */
    @Redirect(
            method = "curePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"
            )
    )
    private void advancedcolonies$healPlayerProperly(final Player player, final float vanillaAmount) {
        player.heal(HealerWorkOptimizer.computeCorrectedHealAmount(player, this.building, vanillaAmount));
    }

    /**
     * Fixes the inverted building-level subtraction in {@code wander()}.
     */
    @Redirect(
            method = "wander",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/entity/citizen/EntityCitizen;heal(F)V"
            )
    )
    private void advancedcolonies$healRemoteCitizenProperly(final EntityCitizen citizen, final float vanillaAmount) {
        citizen.heal(HealerWorkOptimizer.computeCorrectedHealAmount(citizen, this.building, vanillaAmount));
    }
}
