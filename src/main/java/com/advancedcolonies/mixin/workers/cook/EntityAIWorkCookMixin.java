package com.advancedcolonies.mixin.workers.cook;

import com.advancedcolonies.workers.cook.CookServiceOptimizer;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.jobs.JobCook;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIUsesFurnace;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkCook;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;

/**
 * Thin Mixin hook on {@link EntityAIWorkCook} delegating customer queue deduplication,
 * nearest-table distance sorting, and smart inventory dump thresholds to {@link CookServiceOptimizer}.
 */
@Mixin(value = EntityAIWorkCook.class, remap = false)
public abstract class EntityAIWorkCookMixin extends AbstractEntityAIUsesFurnace<JobCook, BuildingCook> {

    @Shadow
    @Final
    private Queue<AbstractEntityCitizen> citizenToServe;

    @Shadow
    @Final
    private Queue<Player> playerToServe;

    protected EntityAIWorkCookMixin(@NotNull final JobCook job) {
        super(job);
    }

    @Inject(method = "checkForImportantJobs", at = @At("RETURN"))
    private void advancedColonies$onCheckForImportantJobsReturn(final CallbackInfoReturnable<IAIState> cir) {
        CookServiceOptimizer.deduplicateAndSortCitizenQueue(this.worker, this.building, this.citizenToServe);
        CookServiceOptimizer.sanitizePlayerQueue(this.playerToServe);
    }

    @Inject(method = "serveFoodToCitizen", at = @At("HEAD"))
    private void advancedColonies$onServeFoodToCitizenHead(final CallbackInfoReturnable<IAIState> cir) {
        CookServiceOptimizer.deduplicateAndSortCitizenQueue(this.worker, this.building, this.citizenToServe);
    }

    @Inject(method = "getActionsDoneUntilDumping", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onGetActionsDoneUntilDumping(final CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CookServiceOptimizer.getSmartActionsUntilDumping(
                this.world, this.worker, this.building, this.citizenToServe));
    }
}
