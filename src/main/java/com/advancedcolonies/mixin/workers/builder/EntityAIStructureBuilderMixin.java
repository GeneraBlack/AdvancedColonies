package com.advancedcolonies.mixin.workers.builder;

import com.advancedcolonies.workers.builder.BuilderLavaHazardTracker;
import com.advancedcolonies.workers.builder.BuilderStandSafetyEvaluator;
import com.advancedcolonies.workers.builder.StructureWorkModeHelper;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder;
import com.minecolonies.core.entity.ai.workers.util.BuildingStructureHandler;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIStructureBuilder} delegating worksite hazard retreat,
 * safe mining stand selection, configurable {@code WorkMode} positioning, and remote order acceptance
 * to {@link BuilderLavaHazardTracker}, {@link BuilderStandSafetyEvaluator}, and {@link StructureWorkModeHelper}.
 */
@Mixin(value = EntityAIStructureBuilder.class, remap = false)
public abstract class EntityAIStructureBuilderMixin extends AbstractEntityAIStructureWithWorkOrder<JobBuilder, BuildingBuilder> {

    @Unique
    private static final int ADVANCED_COLONIES$STAND_RETRY_DELAY = 5;

    @Shadow
    PathResult gotoPath;

    @Shadow
    protected abstract boolean checkForWorkOrder();

    @Unique
    private final BuilderLavaHazardTracker advancedColonies$hazardTracker = new BuilderLavaHazardTracker();

    protected EntityAIStructureBuilderMixin(@NotNull final JobBuilder job) {
        super(job);
    }

    @Override
    public void resetCurrentStructure() {
        super.resetCurrentStructure();
        advancedColonies$cancelGotoPath();
        advancedColonies$hazardTracker.reset();
    }

    @Inject(method = "setStructurePlacer", at = @At("HEAD"))
    private void advancedColonies$onSetStructurePlacer(
            final BuildingStructureHandler<JobBuilder, BuildingBuilder> structure,
            final CallbackInfo ci) {
        advancedColonies$hazardTracker.reset();
    }

    @Inject(method = "startWorkingAtOwnBuilding", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onStartWorkingAtOwnBuilding(final CallbackInfoReturnable<IAIState> cir) {
        if (StructureWorkModeHelper.canBuilderAcceptOrdersAnywhere() && checkForWorkOrder()) {
            final IAIState state = super.startWorkingAtOwnBuilding();
            cir.setReturnValue(state == AIWorkerState.IDLE ? AIWorkerState.LOAD_STRUCTURE : state);
        }
    }

    @Inject(method = "mineBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onBeforeMineBlock(
            @NotNull final BlockPos blockToMine,
            @Nullable final BlockPos safeStand,
            final CallbackInfoReturnable<Boolean> cir) {

        if (StructureWorkModeHelper.shouldBypassMiningStandWalk(this, worker, blockToMine, safeStand)) {
            return;
        }

        final BlockPos standPos = safeStand == null ? worker.blockPosition() : safeStand;
        final BlockPos alternativeStand = BuilderStandSafetyEvaluator.findAlternativeMiningStand(
                world, blockToMine, standPos, advancedColonies$hazardTracker);

        if (alternativeStand != null) {
            workFrom = alternativeStand;
            prevBlockPosition = null;
            advancedColonies$cancelGotoPath();
            worker.getNavigation().stop();
            setDelay(ADVANCED_COLONIES$STAND_RETRY_DELAY);
            cir.setReturnValue(false);
            return;
        }

        if (BuilderStandSafetyEvaluator.isUnsafeMiningStand(world, blockToMine, standPos)) {
            workFrom = null;
            prevBlockPosition = null;
            advancedColonies$cancelGotoPath();
            worker.getNavigation().stop();
            setDelay(ADVANCED_COLONIES$STAND_RETRY_DELAY);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "walkToConstructionSite", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onWalkToConstructionSiteHead(
            final BlockPos currentBlock,
            final CallbackInfoReturnable<Boolean> cir) {

        if (currentBlock == null) {
            return;
        }

        final Boolean workModeResult = StructureWorkModeHelper.evaluateWalkToConstructionSite(
                this, worker, building, currentBlock);
        if (workModeResult != null) {
            if (workModeResult) {
                prevBlockPosition = currentBlock;
            }
            cir.setReturnValue(workModeResult);
            return;
        }

        // 1. Continue active retreat if one is in progress
        if (advancedColonies$hazardTracker.tickActiveRetreat(worker)) {
            setDelay(advancedColonies$hazardTracker.getRetryDelay(world));
            cir.setReturnValue(false);
            return;
        }

        // 2. Respect persistent lava hazard backoff window
        final int backoffTicks = advancedColonies$hazardTracker.getRemainingBackoffTicks(world, currentBlock);
        if (backoffTicks > 0) {
            advancedColonies$cancelGotoPath();
            worker.getNavigation().stop();
            setDelay(backoffTicks);
            cir.setReturnValue(false);
            return;
        }

        // 3. Scan for lava near the builder's current stand
        final BlockPos lavaPos = advancedColonies$hazardTracker.checkCurrentStandForLava(world, worker, currentBlock, workFrom);
        if (lavaPos != null) {
            workFrom = null;
            prevBlockPosition = null;
            advancedColonies$cancelGotoPath();
            advancedColonies$hazardTracker.beginRetreat(world, worker, lavaPos, currentBlock);
            advancedColonies$hazardTracker.tickActiveRetreat(worker);
            setDelay(advancedColonies$hazardTracker.getRetryDelay(world));
            cir.setReturnValue(false);
            return;
        }

        // 4. Verify or reroute planned workFrom stand if it is near lava
        if (workFrom != null) {
            workFrom = advancedColonies$hazardTracker.resolveSafeWorkingStand(world, worker, currentBlock, workFrom);
            if (workFrom == null) {
                setDelay(advancedColonies$hazardTracker.getRetryDelay(world));
                cir.setReturnValue(false);
            }
        }
    }

    @Unique
    private void advancedColonies$cancelGotoPath() {
        if (gotoPath != null) {
            gotoPath.cancel();
            gotoPath = null;
        }
    }
}
