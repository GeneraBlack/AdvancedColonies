package com.advancedcolonies.mixin.workers.builder;

import com.advancedcolonies.common.navigation.NavigationStuckAndRescueHelper;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.workers.builder.BuilderClearingHelper;
import com.advancedcolonies.workers.builder.BuilderClearingStateAccess;
import com.advancedcolonies.workers.builder.BuilderLavaHazardTracker;
import com.advancedcolonies.workers.builder.BuilderStandSafetyEvaluator;
import com.advancedcolonies.workers.builder.StructureWorkModeHelper;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import com.minecolonies.core.entity.ai.workers.util.BuildingStructureHandler;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
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
 * safe mining stand selection, configurable {@code WorkMode} positioning, remote order acceptance,
 * and anti-stall construction site reach recovery to {@link BuilderLavaHazardTracker},
 * {@link BuilderStandSafetyEvaluator}, and {@link StructureWorkModeHelper}.
 */
@Mixin(value = EntityAIStructureBuilder.class, remap = false)
public abstract class EntityAIStructureBuilderMixin
        extends AbstractEntityAIStructureWithWorkOrder<JobBuilder, BuildingBuilder>
        implements BuilderClearingStateAccess {

    @Unique
    private static final int ADVANCED_COLONIES$STAND_RETRY_DELAY = 3;

    @Unique
    private static final int ADVANCED_COLONIES$MAX_UNSAFE_MINE_RETRIES = 2;

    @Unique
    private static final int ADVANCED_COLONIES$SITE_STALL_THRESHOLD = 35;

    @Shadow
    PathResult gotoPath;

    @Shadow
    protected abstract boolean checkForWorkOrder();

    @Unique
    private final BuilderLavaHazardTracker advancedColonies$hazardTracker = new BuilderLavaHazardTracker();

    @Unique
    @Nullable
    private BlockPos advancedColonies$lastUnsafeMineBlock = null;

    @Unique
    private int advancedColonies$unsafeMineRetries = 0;

    @Unique
    @Nullable
    private BlockPos advancedColonies$stalledSiteTarget = null;

    @Unique
    private int advancedColonies$stalledSiteTicks = 0;

    protected EntityAIStructureBuilderMixin(@NotNull final JobBuilder job) {
        super(job);
    }

    @Override
    public void resetCurrentStructure() {
        super.resetCurrentStructure();
        advancedColonies$resetStuckBuildState();
    }

    @Override
    public void advancedColonies$resetStuckBuildState() {
        this.workFrom = null;
        this.prevBlockPosition = null;
        advancedColonies$cancelGotoPath();
        advancedColonies$hazardTracker.reset();
        advancedColonies$lastUnsafeMineBlock = null;
        advancedColonies$unsafeMineRetries = 0;
        advancedColonies$stalledSiteTarget = null;
        advancedColonies$stalledSiteTicks = 0;
    }

    @Inject(method = "setStructurePlacer", at = @At("RETURN"))
    private void advancedColonies$onSetStructurePlacer(
            final BuildingStructureHandler<JobBuilder, BuildingBuilder> structure,
            final CallbackInfo ci) {
        advancedColonies$hazardTracker.reset();
        advancedColonies$lastUnsafeMineBlock = null;
        advancedColonies$unsafeMineRetries = 0;
        advancedColonies$stalledSiteTarget = null;
        advancedColonies$stalledSiteTicks = 0;

        if (structure != null && structure.getStage() != null) {
            BuilderClearingHelper.onStructureLoaded(
                    this,
                    this.building,
                    new BuildingProgressStage[]{structure.getStage()}
            );
        }
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

        if (this.worker == null) {
            return;
        }

        if (!blockToMine.equals(this.advancedColonies$lastUnsafeMineBlock)) {
            this.advancedColonies$lastUnsafeMineBlock = blockToMine.immutable();
            this.advancedColonies$unsafeMineRetries = 0;
        }

        if (StructureWorkModeHelper.shouldBypassMiningStandWalk(this, worker, blockToMine, safeStand)
                && !BuilderStandSafetyEvaluator.isUnsafeMiningStand(world, blockToMine, worker.blockPosition())) {
            return;
        }

        final BlockPos actualFootPos = worker.blockPosition();
        if (!BuilderStandSafetyEvaluator.isUnsafeMiningStand(world, blockToMine, actualFootPos)) {
            return;
        }

        // Prevent infinite retry loops when no alternative stand is reachable in tight spaces
        if (this.advancedColonies$unsafeMineRetries >= ADVANCED_COLONIES$MAX_UNSAFE_MINE_RETRIES) {
            return;
        }
        this.advancedColonies$unsafeMineRetries++;

        final BlockPos alternativeStand = BuilderStandSafetyEvaluator.findAlternativeMiningStand(
                world, blockToMine, actualFootPos, advancedColonies$hazardTracker);

        if (alternativeStand != null) {
            this.workFrom = alternativeStand;
            this.prevBlockPosition = null;
            advancedColonies$cancelGotoPath();
            if (actualFootPos.distSqr(alternativeStand) <= 16.0D) {
                worker.setPos(alternativeStand.getX() + 0.5D, alternativeStand.getY(), alternativeStand.getZ() + 0.5D);
                worker.setDeltaMovement(Vec3.ZERO);
            }
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
                if (this.workFrom == null && this.worker != null) {
                    this.workFrom = this.worker.blockPosition();
                }
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

        // 4. Verify or reroute planned workFrom stand ONLY if it is near lava
        if (workFrom != null) {
            workFrom = advancedColonies$hazardTracker.resolveSafeWorkingStand(world, worker, currentBlock, workFrom);
            if (workFrom == null) {
                setDelay(advancedColonies$hazardTracker.getRetryDelay(world));
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "walkToConstructionSite", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onWalkToConstructionSiteReturn(
            final BlockPos currentBlock,
            final CallbackInfoReturnable<Boolean> cir) {

        if (currentBlock == null || this.worker == null) {
            return;
        }

        if (cir.getReturnValueZ()) {
            // Walk succeeded: reset stall counter
            this.advancedColonies$stalledSiteTarget = null;
            this.advancedColonies$stalledSiteTicks = 0;

            // CRITICAL FIX for vanilla MineColonies doMining() infinite loop:
            // Vanilla walkToConstructionSite sets workFrom = null when dist2D > 5 or returns true while workFrom == null
            // when dist(prevBlockPosition, currentBlock) <= 10. doMining() then aborts at `if (workFrom == null) return getState();`!
            if (this.workFrom == null) {
                this.workFrom = this.worker.blockPosition();
            }
            return;
        }

        // Walk returned false: track whether the builder is genuinely stalling on reaching currentBlock
        if (!currentBlock.equals(this.advancedColonies$stalledSiteTarget)) {
            this.advancedColonies$stalledSiteTarget = currentBlock.immutable();
            this.advancedColonies$stalledSiteTicks = 0;
        }

        if (this.gotoPath != null && this.gotoPath.isDone() && this.gotoPath.getPath() == null) {
            this.advancedColonies$stalledSiteTicks += 10;
            this.gotoPath = null;
        }

        if (NavigationStuckAndRescueHelper.isActivelyMakingProgress(this.worker)) {
            this.advancedColonies$stalledSiteTicks = Math.max(0, this.advancedColonies$stalledSiteTicks - 1);
            return;
        }

        this.advancedColonies$stalledSiteTicks++;
        if (this.advancedColonies$stalledSiteTicks < ADVANCED_COLONIES$SITE_STALL_THRESHOLD) {
            return;
        }

        // Builder has stalled trying to reach currentBlock: apply Smart Site Reach Fallback or Safe Site Teleport
        final BlockPos workerPos = this.worker.blockPosition();
        final double distToBlockSqr = workerPos.distSqr(currentBlock);
        final BlockPos woLoc = (this.building != null && this.building.getWorkOrder() != null)
                ? this.building.getWorkOrder().getLocation()
                : null;
        final double distToSiteSqr = woLoc != null ? workerPos.distSqr(woLoc) : distToBlockSqr;

        if (distToBlockSqr <= 28.0D * 28.0D || distToSiteSqr <= 36.0D * 36.0D) {
            // Builder is already on/near the construction site: allow placing/mining from current stand
            advancedColonies$cancelGotoPath();
            this.worker.getNavigation().stop();
            this.workFrom = workerPos;
            this.prevBlockPosition = currentBlock;
            this.advancedColonies$stalledSiteTicks = 0;
            cir.setReturnValue(true);
            return;
        }

        if (!AdvancedColoniesConfig.PATHFINDING.disableStuckTeleport.get()) {
            BlockPos safeStand = NavigationStuckAndRescueHelper.findSafeStandNear(this.world, currentBlock, 6, 4);
            if (safeStand == null && woLoc != null) {
                safeStand = NavigationStuckAndRescueHelper.findSafeStandNear(this.world, woLoc, 6, 4);
            }
            if (safeStand != null) {
                this.worker.stopRiding();
                this.worker.teleportTo(safeStand.getX() + 0.5D, safeStand.getY(), safeStand.getZ() + 0.5D);
                this.worker.setDeltaMovement(Vec3.ZERO);
                advancedColonies$cancelGotoPath();
                this.worker.getNavigation().stop();
                this.workFrom = this.worker.blockPosition();
                this.prevBlockPosition = currentBlock;
                this.advancedColonies$stalledSiteTicks = 0;
                cir.setReturnValue(true);
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
