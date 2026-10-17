package com.advancedcolonies.mixin.workers.concretemixer;

import com.advancedcolonies.workers.concretemixer.ConcreteMixerBatchHelper;
import com.advancedcolonies.workers.concretemixer.IConcreteMixerBatchAccess;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingConcreteMixer;
import com.minecolonies.core.colony.jobs.JobConcreteMixer;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.ai.workers.crafting.EntityAIConcreteMixer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.GATHERING_REQUIRED_MATERIALS;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import static com.minecolonies.api.util.constant.Constants.UPDATE_FLAG;
import static com.minecolonies.api.util.constant.StatisticsConstants.ITEMS_CRAFTED_DETAIL;

/**
 * Thin Mixin on {@link EntityAIConcreteMixer} delegating server-limited batch placement
 * and harvesting to {@link ConcreteMixerBatchHelper}.
 */
@Mixin(value = EntityAIConcreteMixer.class, remap = false)
public abstract class EntityAIConcreteMixerMixin extends AbstractEntityAICrafting<JobConcreteMixer, BuildingConcreteMixer> {

    @Shadow
    @Final
    private static Predicate<ItemStack> CONCRETE;

    @Unique
    private BlockPos advancedColonies$currentMiningPos = null;

    protected EntityAIConcreteMixerMixin(@NotNull final JobConcreteMixer job) {
        super(job);
    }

    @Unique
    private int advancedColonies$findRequestedPowderSlot() {
        if (currentRequest != null && currentRecipeStorage != null && !currentRecipeStorage.getCleanedInput().isEmpty()) {
            final ItemStack inputStack = currentRecipeStorage.getCleanedInput().getFirst().getItemStack();
            if (CONCRETE.test(inputStack)) {
                return InventoryUtils.findFirstSlotInItemHandlerWith(
                        worker.getInventoryCitizen(),
                        s -> ItemStackUtils.compareItemStacksIgnoreStackSize(s, inputStack));
            }
        }
        return -1;
    }

    @Inject(method = "placePowder", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onPlacePowder(final CallbackInfoReturnable<IAIState> cir) {
        if (!ConcreteMixerBatchHelper.isBatchingEnabled() || building == null) {
            return;
        }

        final BlockPos posToPlace = building.getBlockToPlace();
        if (posToPlace == null) {
            cir.setReturnValue(START_WORKING);
            return;
        }

        final int slot = advancedColonies$findRequestedPowderSlot();
        if (slot == -1) {
            final Predicate<ItemStack> requestedPredicate = stack ->
                    CONCRETE.test(stack)
                            && currentRequest != null
                            && currentRecipeStorage != null
                            && !currentRecipeStorage.getCleanedInput().isEmpty()
                            && ItemStack.isSameItemSameComponents(
                                    stack,
                                    currentRecipeStorage.getCleanedInput().getFirst().getItemStack());

            if (InventoryUtils.getCountFromBuilding(building, requestedPredicate) > 0) {
                needsCurrently = new Tuple<>(requestedPredicate, STACKSIZE);
                cir.setReturnValue(GATHERING_REQUIRED_MATERIALS);
                return;
            }
            cir.setReturnValue(START_WORKING);
            return;
        }

        if (!walkToWorkPos(posToPlace)) {
            cir.setReturnValue(getState());
            return;
        }

        final ItemStack stack = worker.getInventoryCitizen().getStackInSlot(slot);
        if (stack.getItem() instanceof BlockItem blockItem) {
            final Block block = blockItem.getBlock();
            final int quantity = ConcreteMixerBatchHelper.computeBatchPlacementQuantity(
                    stack.getCount(),
                    getSecondarySkillLevel());

            if (InventoryUtils.attemptReduceStackInItemHandler(worker.getInventoryCitizen(), stack, quantity)) {
                if (building instanceof IConcreteMixerBatchAccess access) {
                    access.advancedColonies$setBatchCount(posToPlace, quantity);
                }
                world.setBlock(
                        posToPlace,
                        block.defaultBlockState().updateShape(Direction.DOWN, block.defaultBlockState(), world, posToPlace, posToPlace),
                        UPDATE_FLAG);
            }
        }

        cir.setReturnValue(getState());
    }

    @Inject(method = "harvestConcrete", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onHarvestConcrete(final CallbackInfoReturnable<IAIState> cir) {
        if (!ConcreteMixerBatchHelper.isBatchingEnabled() || building == null) {
            return;
        }

        advancedColonies$currentMiningPos = building.getBlockToMine();
        if (advancedColonies$currentMiningPos == null) {
            this.resetActionsDone();
            cir.setReturnValue(START_WORKING);
            return;
        }

        if (!walkToWorkPos(advancedColonies$currentMiningPos)) {
            cir.setReturnValue(getState());
            return;
        }

        final BlockState blockToMine = world.getBlockState(advancedColonies$currentMiningPos);
        if (mineBlock(advancedColonies$currentMiningPos)) {
            final int multiplier = ConcreteMixerBatchHelper.getClampedHarvestMultiplier(building, advancedColonies$currentMiningPos);
            if (building instanceof IConcreteMixerBatchAccess access) {
                access.advancedColonies$removeBatchCount(advancedColonies$currentMiningPos);
            }

            StatsUtil.trackStatByName(building, ITEMS_CRAFTED_DETAIL, blockToMine.getBlock().getDescriptionId(), multiplier);
            if (currentRequest != null
                    && currentRecipeStorage != null
                    && blockToMine.getBlock().asItem().equals(currentRecipeStorage.getPrimaryOutput().getItem())) {
                currentRequest.addDelivery(new ItemStack(blockToMine.getBlock(), multiplier));
                job.setCraftCounter(job.getCraftCounter() + multiplier);
                if (job.getCraftCounter() >= job.getMaxCraftingCount() && building.getBlockToMine() == null) {
                    cir.setReturnValue(finalizeCraftingTask());
                    return;
                }
            }
        }

        cir.setReturnValue(getState());
    }

    @Override
    protected List<ItemStack> increaseBlockDrops(final List<ItemStack> drops) {
        final List<ItemStack> baseDrops = super.increaseBlockDrops(drops);
        return ConcreteMixerBatchHelper.scaleConcreteDrops(baseDrops, building, advancedColonies$currentMiningPos);
    }
}
