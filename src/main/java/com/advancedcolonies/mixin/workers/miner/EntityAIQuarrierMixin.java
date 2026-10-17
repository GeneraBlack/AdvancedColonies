package com.advancedcolonies.mixin.workers.miner;

import com.advancedcolonies.workers.builder.StructureWorkModeHelper;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.JobQuarrier;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIQuarrier} delegating configurable {@code WorkMode}
 * positioning to {@link StructureWorkModeHelper}.
 */
@Mixin(value = EntityAIQuarrier.class, remap = false)
public abstract class EntityAIQuarrierMixin extends AbstractEntityAIStructureWithWorkOrder<JobQuarrier, BuildingMiner> {

    protected EntityAIQuarrierMixin(@NotNull final JobQuarrier job) {
        super(job);
    }

    @Inject(method = "walkToConstructionSite", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onWalkToConstructionSite(
            final BlockPos currentBlock,
            final CallbackInfoReturnable<Boolean> cir) {
        final Boolean result = StructureWorkModeHelper.evaluateWalkToConstructionSite(
                this, worker, building, currentBlock);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
