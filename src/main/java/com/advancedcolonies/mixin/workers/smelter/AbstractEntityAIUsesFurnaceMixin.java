package com.advancedcolonies.mixin.workers.smelter;

import com.advancedcolonies.workers.smelter.FurnaceAndCraftingHelper;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAISkill;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIUsesFurnace;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link AbstractEntityAIUsesFurnace} delegating furnace
 * acceleration, multi-furnace batching, and auto-extraction to {@link FurnaceAndCraftingHelper}.
 */
@Mixin(value = AbstractEntityAIUsesFurnace.class, remap = false)
public abstract class AbstractEntityAIUsesFurnaceMixin<J extends AbstractJob<?, J>, B extends AbstractBuilding>
        extends AbstractEntityAISkill<J, B> {

    protected AbstractEntityAIUsesFurnaceMixin(@NotNull final J job) {
        super(job);
    }

    @Inject(method = "accelerateFurnaces", at = @At("HEAD"), cancellable = true, remap = false)
    private void advancedcolonies$accelerateSmelteryFurnaces(final CallbackInfoReturnable<IAIState> cir) {
        final int skillLevel = this.getPrimarySkillLevel();
        FurnaceAndCraftingHelper.accelerateBuildingFurnaces(this.building, skillLevel);
        cir.setReturnValue(null);
    }

    @Inject(method = "getActionsDoneUntilDumping", at = @At("RETURN"), cancellable = true, remap = false)
    private void advancedcolonies$batchMultiFurnaceActionsBeforeDump(final CallbackInfoReturnable<Integer> cir) {
        final int effective = FurnaceAndCraftingHelper.computeFurnaceActionsBeforeDump(
                this.building,
                cir.getReturnValue());
        if (effective != cir.getReturnValue()) {
            cir.setReturnValue(effective);
        }
    }

    @Inject(
            method = "fillUpFurnace",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void advancedcolonies$autoExtractWhenFillingSmelteryFurnace(final CallbackInfoReturnable<IAIState> cir) {
        if (this.walkTo != null && this.world != null) {
            final BlockEntity entity = this.world.getBlockEntity(this.walkTo);
            if (entity instanceof FurnaceBlockEntity furnace) {
                FurnaceAndCraftingHelper.tryAutoExtractOnFill(furnace, this.worker);
            }
        }
    }
}
