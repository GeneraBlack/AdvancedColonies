package com.advancedcolonies.mixin.workers.lumberjack;

import com.advancedcolonies.workers.lumberjack.LumberjackForestryHelper;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.entity.ai.workers.util.Tree;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Thin Mixin on {@link Tree} fixing Mangrove Propagule replanting by scanning downward
 * from {@code topLog} to {@code location.getY()} for valid Dirt/Mud ground via
 * {@link LumberjackForestryHelper#tryFillMangroveTreeStumps}.
 */
@Mixin(value = Tree.class, remap = false)
public abstract class TreeMangroveStumpMixin {

    @Shadow
    private LinkedList<BlockPos> woodBlocks;

    @Shadow
    private ArrayList<BlockPos> stumpLocations;

    @Shadow
    private ItemStack sapling;

    @Shadow
    private BlockPos topLog;

    @Shadow
    private BlockPos location;

    @Inject(
            method = "findLogs",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/entity/ai/workers/util/Tree;fillTreeStumps(I)V",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void advancedColonies$onBeforeFillTreeStumps(
            final Level world,
            final IColony colony,
            final CallbackInfo ci) {

        if (location != null && LumberjackForestryHelper.tryFillMangroveTreeStumps(
                world,
                woodBlocks,
                stumpLocations,
                sapling,
                topLog,
                location.getY())) {
            ci.cancel();
        }
    }
}
