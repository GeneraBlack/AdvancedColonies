package com.advancedcolonies.mixin.workers.common;

import com.advancedcolonies.workers.builder.StructureWorkModeHelper;
import com.advancedcolonies.workers.miner.MinerExcavationHelper;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAISkill;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import com.minecolonies.core.entity.ai.workers.production.EntityAIStructureMiner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Thin Mixin hook on {@link AbstractEntityAIInteract} supporting:
 * <ul>
 *   <li>Remote/extended-reach mining location checks via {@link StructureWorkModeHelper}.</li>
 *   <li>Miner and Quarrier block drop multipliers via {@link MinerExcavationHelper}.</li>
 * </ul>
 */
@Mixin(value = AbstractEntityAIInteract.class, remap = false)
public abstract class AbstractEntityAIInteractMixin<J extends AbstractJob<?, J>, B extends AbstractBuilding>
        extends AbstractEntityAISkill<J, B> {

    protected AbstractEntityAIInteractMixin(@NotNull final J job) {
        super(job);
    }

    @Shadow
    public abstract int getBlockMiningTime(@NotNull BlockState state, @NotNull BlockPos pos);

    @Inject(method = "checkMiningLocation", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onCheckMiningLocation(
            @NotNull final BlockPos blockToMine,
            @NotNull final BlockPos safeStand,
            final CallbackInfoReturnable<Boolean> cir) {

        final AbstractEntityAIInteract<?, ?> self = (AbstractEntityAIInteract<?, ?>) (Object) this;
        if (!StructureWorkModeHelper.shouldBypassMiningStandWalk(self, worker, blockToMine, safeStand)) {
            return;
        }

        final BlockState curBlock = world.getBlockState(blockToMine);
        if (!holdEfficientTool(curBlock, blockToMine)) {
            cir.setReturnValue(true);
            return;
        }

        currentWorkingLocation = blockToMine;
        cir.setReturnValue(hasNotDelayed(getBlockMiningTime(curBlock, blockToMine)));
    }

    @Inject(method = "increaseBlockDrops", at = @At("RETURN"), cancellable = true)
    private void advancedColonies$onIncreaseBlockDrops(
            final List<ItemStack> drops,
            final CallbackInfoReturnable<List<ItemStack>> cir) {
        final Object self = this;
        if (self instanceof EntityAIStructureMiner || self instanceof EntityAIQuarrier) {
            cir.setReturnValue(MinerExcavationHelper.scaleMinerBlockDrops(building, cir.getReturnValue()));
        }
    }
}
