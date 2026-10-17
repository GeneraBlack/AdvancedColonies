package com.advancedcolonies.mixin.workers.common;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.workers.smelter.FurnaceAndCraftingHelper;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link AbstractEntityAICrafting} delegating to {@link FurnaceAndCraftingHelper}
 * for continuous multi-item batch crafting and smooth skill-level speed scaling.
 */
@Mixin(value = AbstractEntityAICrafting.class, remap = false)
public abstract class AbstractEntityAICraftingMixin<J extends AbstractJobCrafter<?, J>, B extends AbstractBuilding>
        extends AbstractEntityAIInteract<J, B> {

    @Shadow
    public static int PROGRESS_MULTIPLIER;

    @Shadow
    protected static int MAX_LEVEL;

    @Shadow
    @Final
    private static int HITTING_TIME;

    @Shadow
    protected IRecipeStorage currentRecipeStorage;

    @Shadow
    protected abstract IAIState checkForItems(@NotNull IRecipeStorage storage);

    protected AbstractEntityAICraftingMixin(@NotNull final J job) {
        super(job);
    }

    /**
     * When a crafter finishes one item of a multi-item batch ({@code craftCounter < maxCraftingCount})
     * and still has all required ingredients in their inventory, stays directly in {@link AIWorkerState#CRAFT}
     * instead of returning {@link AIWorkerState#GET_RECIPE} (which wastes 80+ ticks and performs a full
     * building rack scan after every single item).
     */
    @Inject(
            method = "executeCraftingAction",
            at = @At("RETURN"),
            cancellable = true
    )
    private void advancedcolonies$continueBatchCraftingWhenItemsPresent(
            final int toolSlot,
            final CallbackInfoReturnable<IAIState> cir) {
        if (cir.getReturnValue() == AIWorkerState.GET_RECIPE
                && FurnaceAndCraftingHelper.isContinuousBatchCraftingEnabled()
                && this.currentRecipeStorage != null
                && this.job.getCraftCounter() < this.job.getMaxCraftingCount()) {
            final IAIState itemCheckState = this.checkForItems(this.currentRecipeStorage);
            if (itemCheckState == AIWorkerState.CRAFT) {
                cir.setReturnValue(AIWorkerState.CRAFT);
            }
        }
    }

    /**
     * Replaces coarse integer-division truncation in {@code getRequiredProgressForMakingRawMaterial()}
     * with smooth floating-point skill scaling.
     */
    @Inject(
            method = "getRequiredProgressForMakingRawMaterial",
            at = @At("HEAD"),
            cancellable = true
    )
    private void advancedcolonies$applySmoothCraftingProgress(final CallbackInfoReturnable<Integer> cir) {
        if (!AdvancedColoniesConfig.FURNACE_AND_CRAFT.enableSmoothCrafterSpeedScaling.get()) {
            return;
        }
        if (this.worker.getCitizenData() == null
                || !(this.getModuleForJob() instanceof CraftingWorkerBuildingModule craftingModule)) {
            return;
        }

        final int skillLevel = this.worker.getCitizenData()
                .getCitizenSkillHandler()
                .getLevel(craftingModule.getCraftSpeedSkill());
        cir.setReturnValue(FurnaceAndCraftingHelper.computeSmoothRequiredCraftingProgress(
                skillLevel,
                PROGRESS_MULTIPLIER,
                HITTING_TIME,
                MAX_LEVEL));
    }
}
