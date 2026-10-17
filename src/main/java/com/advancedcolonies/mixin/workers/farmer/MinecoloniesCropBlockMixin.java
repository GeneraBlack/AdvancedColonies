package com.advancedcolonies.mixin.workers.farmer;

import com.advancedcolonies.workers.farmer.AgricultureWorkHelper;
import com.minecolonies.api.blocks.AbstractBlockMinecolonies;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin on {@link MinecoloniesCropBlock} preventing crops planted on {@code MinecoloniesFarmland}
 * from prematurely breaking during neighbor shape updates when {@code fixMinecoloniesCropBlockSurvival} is enabled.
 */
@Mixin(value = MinecoloniesCropBlock.class, remap = false)
public abstract class MinecoloniesCropBlockMixin extends AbstractBlockMinecolonies<MinecoloniesCropBlock> {

    @Shadow
    @Final
    private Block preferredFarmland;

    protected MinecoloniesCropBlockMixin(final Properties properties) {
        super(properties);
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true, remap = true)
    private void advancedColonies$onUpdateShape(
            @NotNull final BlockState state,
            @NotNull final Direction dir,
            @NotNull final BlockState newState,
            @NotNull final LevelAccessor level,
            @NotNull final BlockPos pos,
            @NotNull final BlockPos neighborPos,
            final CallbackInfoReturnable<BlockState> cir) {

        if (!AgricultureWorkHelper.isMinecoloniesCropSurvivalFixed()) {
            return;
        }

        if (!AgricultureWorkHelper.canMinecoloniesCropSurviveOnFarmland(level, pos, preferredFarmland)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        } else {
            cir.setReturnValue(super.updateShape(state, dir, newState, level, pos, neighborPos));
        }
    }
}
