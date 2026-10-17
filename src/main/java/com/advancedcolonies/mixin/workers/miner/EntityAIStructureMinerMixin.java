package com.advancedcolonies.mixin.workers.miner;

import com.advancedcolonies.workers.builder.StructureWorkModeHelper;
import com.advancedcolonies.workers.miner.MinerExcavationHelper;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.JobMiner;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.production.EntityAIStructureMiner;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIStructureMiner} delegating configurable {@code WorkMode}
 * positioning to {@link StructureWorkModeHelper} and {@link MinerExcavationHelper}.
 */
@Mixin(value = EntityAIStructureMiner.class, remap = false)
public abstract class EntityAIStructureMinerMixin extends AbstractEntityAIStructureWithWorkOrder<JobMiner, BuildingMiner> {

    protected EntityAIStructureMinerMixin(@NotNull final JobMiner job) {
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

    @Inject(method = "walkToLadder", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onWalkToLadder(final CallbackInfoReturnable<Boolean> cir) {
        if (MinerExcavationHelper.shouldBypassShaftNavigation()) {
            cir.setReturnValue(true);
        }
    }
}
