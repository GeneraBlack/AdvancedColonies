package com.advancedcolonies.mixin.workers.builder;

import com.advancedcolonies.workers.builder.BuilderClearingHelper;
import com.advancedcolonies.workers.builder.BuilderClearingStateAccess;
import com.ldtteam.structurize.placement.AbstractBlueprintIterator;
import com.ldtteam.structurize.placement.StructurePhasePlacementResult;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlueprintPositionInfo;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.jobs.AbstractJobStructure;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import com.minecolonies.core.entity.ai.workers.util.BuildingStructureHandler;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/**
 * Thin Mixin hook on {@link AbstractEntityAIStructure} that delegates Bottom-Up Clearing
 * execution, Blueprint-Match skipping, and live construction telemetry to {@link BuilderClearingHelper}.
 */
@Mixin(value = AbstractEntityAIStructure.class, remap = false)
public abstract class AbstractEntityAIStructureMixin<J extends AbstractJobStructure<?, J>, B extends AbstractBuildingStructureBuilder>
        extends AbstractEntityAIInteract<J, B>
        implements BuilderClearingStateAccess {

    @Shadow
    protected Tuple<StructurePlacer, BuildingStructureHandler<J, B>> structurePlacer;

    @Shadow
    protected BlockPos blockToMine;

    @Shadow
    protected BlockPos workFrom;

    @Shadow
    protected BlockPos prevBlockPosition;

    @Shadow
    public abstract void storeProgressPos(final BlockPos blockPos, final BuildingProgressStage stage);

    @Shadow
    protected abstract boolean skipClearing(final BlueprintPositionInfo info, final BlockPos pos, final IStructureHandler handler);

    @Shadow
    protected abstract BlockPos getPosToWorkAt();

    @Unique
    private boolean advancedColonies$inBottomUpClearPass = false;

    protected AbstractEntityAIStructureMixin(@NotNull final J job) {
        super(job);
    }

    @Override
    public boolean advancedColonies$isInBottomUpClearPass() {
        return this.advancedColonies$inBottomUpClearPass;
    }

    @Override
    public void advancedColonies$setInBottomUpClearPass(final boolean active) {
        this.advancedColonies$inBottomUpClearPass = active;
    }

    @Override
    @Nullable
    public BlockPos advancedColonies$getCurrentWorkTargetPos() {
        if (this.blockToMine != null) {
            return this.blockToMine;
        }
        if (this.structurePlacer != null && this.structurePlacer.getB() != null && this.building != null && this.building.hasWorkOrder()) {
            try {
                return this.getPosToWorkAt();
            } catch (final Exception ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public BlockPos advancedColonies$getWorkFromPos() {
        return this.workFrom;
    }

    @Override
    @NotNull
    public String advancedColonies$getStageDescription() {
        if (this.structurePlacer == null || this.structurePlacer.getB() == null) {
            return "";
        }
        final BuildingProgressStage stage = this.structurePlacer.getB().getStage();
        if (stage == null) {
            return "";
        }
        if (stage == BuildingProgressStage.CLEAR && this.advancedColonies$inBottomUpClearPass) {
            return "CLEAR (Bottom-Up)";
        }
        return stage.name();
    }

    @Override
    public void advancedColonies$resetStuckBuildState() {
        this.workFrom = null;
        this.prevBlockPosition = null;
    }

    @Inject(method = "setStructurePlacer", at = @At("RETURN"))
    private void advancedColonies$onSetStructurePlacer(
            final BuildingStructureHandler<J, B> structure,
            final CallbackInfo ci) {
        if (structure != null && structure.getStage() != null) {
            BuilderClearingHelper.onStructureLoaded(
                    (AbstractEntityAIStructure<?, ?>) (Object) this,
                    this.building,
                    new BuildingProgressStage[]{structure.getStage()}
            );
        }
    }

    @Inject(method = "skipClearing", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onSkipClearing(
            final BlueprintPositionInfo info,
            final BlockPos pos,
            final IStructureHandler handler,
            final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && BuilderClearingHelper.doesBlockAlreadyMatchBlueprint(info, pos, handler)) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(
            method = "structureStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ldtteam/structurize/placement/StructurePlacer;executeStructureStep(Lnet/minecraft/world/level/Level;Lcom/ldtteam/structurize/util/ChangeStorage;Lnet/minecraft/core/BlockPos;Lcom/ldtteam/structurize/placement/StructurePlacer$Operation;Ljava/util/function/Supplier;Z)Lcom/ldtteam/structurize/placement/StructurePhasePlacementResult;"
            )
    )
    private StructurePhasePlacementResult advancedColonies$redirectClearStep(
            final StructurePlacer placer,
            final net.minecraft.world.level.Level worldArg,
            final com.ldtteam.structurize.util.ChangeStorage changeStorage,
            final BlockPos progress,
            final StructurePlacer.Operation operation,
            final Supplier<AbstractBlueprintIterator.Result> iteratorSupplier,
            final boolean includeEntities) {

        if (operation == StructurePlacer.Operation.BLOCK_REMOVAL
                && this.structurePlacer != null
                && this.structurePlacer.getB().getStage() == BuildingProgressStage.CLEAR) {
            final StructurePhasePlacementResult bottomUpResult = BuilderClearingHelper.tryExecuteBottomUpClearStep(
                    (AbstractEntityAIStructure<?, ?>) (Object) this,
                    worldArg,
                    placer,
                    progress,
                    this::skipClearing
            );
            if (bottomUpResult != null) {
                return bottomUpResult;
            }
        }
        return placer.executeStructureStep(worldArg, changeStorage, progress, operation, iteratorSupplier, includeEntities);
    }

    @Redirect(
            method = "structureStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/colony/buildings/AbstractBuildingStructureBuilder;nextStage()V"
            )
    )
    private void advancedColonies$redirectBuildingNextStage(final AbstractBuildingStructureBuilder targetBuilding) {
        final BuildingProgressStage currentStage = this.structurePlacer != null ? this.structurePlacer.getB().getStage() : null;
        if (currentStage == BuildingProgressStage.CLEAR && this.advancedColonies$inBottomUpClearPass) {
            // Do not advance building.currStage at the end of Pass 1 (Bottom-Up); it will advance after Pass 2 (Top-Down)
            return;
        }
        targetBuilding.nextStage();
    }

    @Inject(method = "goToNextStage", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onGoToNextStage(
            final StructurePhasePlacementResult result,
            final CallbackInfoReturnable<Boolean> cir) {

        final BuildingProgressStage currentStage = this.structurePlacer != null ? this.structurePlacer.getB().getStage() : null;
        if (BuilderClearingHelper.tryTransitionFromBottomUpToTopDownClear(
                (AbstractEntityAIStructure<?, ?>) (Object) this, currentStage, result)) {
            // Remain in BuildingProgressStage.CLEAR for the top-down cleanup pass
            cir.setReturnValue(true);
        }
    }
}
