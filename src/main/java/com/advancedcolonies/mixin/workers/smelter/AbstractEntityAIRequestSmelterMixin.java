package com.advancedcolonies.mixin.workers.smelter;

import com.advancedcolonies.workers.smelter.FurnaceAndCraftingHelper;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAIRequestSmelter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link AbstractEntityAIRequestSmelter} delegating furnace
 * acceleration and auto-extraction on refill to {@link FurnaceAndCraftingHelper}.
 */
@Mixin(value = AbstractEntityAIRequestSmelter.class, remap = false)
public abstract class AbstractEntityAIRequestSmelterMixin<J extends AbstractJobCrafter<?, J>, B extends AbstractBuilding>
        extends AbstractEntityAICrafting<J, B> {

    @Shadow(remap = false)
    private BlockPos furnacePos;

    protected AbstractEntityAIRequestSmelterMixin(@NotNull final J job) {
        super(job);
    }

    @Inject(method = "accelerateFurnaces", at = @At("HEAD"), cancellable = true, remap = false)
    private void advancedcolonies$accelerateFurnaces(final CallbackInfoReturnable<Boolean> cir) {
        final int skillLevel = this.getSecondarySkillLevel();
        FurnaceAndCraftingHelper.accelerateBuildingFurnaces(this.building, skillLevel);
        cir.setReturnValue(false);
    }

    @Inject(method = "getEmptyFurnaceWithFuel", at = @At("RETURN"), cancellable = true, remap = false)
    private void advancedcolonies$allowFurnaceWithPendingOutput(final CallbackInfoReturnable<BlockPos> cir) {
        if (cir.getReturnValue() == null) {
            final BlockPos fallback = FurnaceAndCraftingHelper.findRefillableFurnaceWithFuel(this.building, this.world);
            if (fallback != null) {
                cir.setReturnValue(fallback);
            }
        }
    }

    @Redirect(
            method = "fillUpFurnace",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/util/ItemStackUtils;hasFuelInFurnaceAndNoSmeltable(Lnet/minecraft/world/level/block/entity/FurnaceBlockEntity;)Z"
            ),
            remap = false
    )
    private boolean advancedcolonies$autoExtractWhenFillingRequestFurnace(final FurnaceBlockEntity furnace) {
        FurnaceAndCraftingHelper.tryAutoExtractOnFill(furnace, this.worker);
        return com.minecolonies.api.util.ItemStackUtils.hasFuelInFurnaceAndNoSmeltable(furnace);
    }
}
