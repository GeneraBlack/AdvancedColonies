package com.advancedcolonies.mixin.workers.netherworker;

import com.advancedcolonies.workers.netherworker.NetherWorkerExpeditionHelper;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingNetherWorker;
import com.minecolonies.core.colony.jobs.JobNetherWorker;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.ai.workers.production.EntityAIWorkNether;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIWorkNether} delegating expedition survival protection
 * to {@link NetherWorkerExpeditionHelper}.
 */
@Mixin(value = EntityAIWorkNether.class, remap = false)
public abstract class EntityAIWorkNetherMixin extends AbstractEntityAICrafting<JobNetherWorker, BuildingNetherWorker> {

    protected EntityAIWorkNetherMixin(@NotNull final JobNetherWorker job) {
        super(job);
    }

    @Inject(
            method = "stayInNether",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/entity/citizen/AbstractEntityCitizen;isDeadOrDying()Z"))
    private void advancedColonies$onBeforeCheckDeadOrDying(final CallbackInfoReturnable<IAIState> cir) {
        NetherWorkerExpeditionHelper.ensureExpeditionSurvival(worker);
    }
}
