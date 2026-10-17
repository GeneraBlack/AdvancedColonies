package com.advancedcolonies.mixin.buildings.concretemixer;

import com.advancedcolonies.workers.concretemixer.ConcreteMixerBatchHelper;
import com.advancedcolonies.workers.concretemixer.IConcreteMixerBatchAccess;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingConcreteMixer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mixin on {@link BuildingConcreteMixer} implementing {@link IConcreteMixerBatchAccess}
 * to persist per-position batch counts across server restarts while enforcing server limits.
 */
@Mixin(value = BuildingConcreteMixer.class, remap = false)
public abstract class BuildingConcreteMixerMixin implements IConcreteMixerBatchAccess {

    @Unique
    private static final String TAG_BATCHED_BLOCKS = "ac_concrete_batches";

    @Unique
    private static final String TAG_BATCH_POS = "pos";

    @Unique
    private static final String TAG_BATCH_COUNT = "count";

    @Shadow
    @Final
    private Map<Integer, List<BlockPos>> waterPos;

    @Unique
    private final Map<BlockPos, Integer> advancedColonies$batchCounts = new HashMap<>();

    @Override
    public void advancedColonies$setBatchCount(@NotNull final BlockPos pos, final int count) {
        final int clamped = Math.max(1, Math.min(count, ConcreteMixerBatchHelper.getServerMaxBatchSize()));
        advancedColonies$batchCounts.put(pos.immutable(), clamped);
    }

    @Override
    public int advancedColonies$getBatchCount(@NotNull final BlockPos pos) {
        final int raw = advancedColonies$batchCounts.getOrDefault(pos, 1);
        return Math.max(1, Math.min(raw, ConcreteMixerBatchHelper.getServerMaxBatchSize()));
    }

    @Override
    public void advancedColonies$removeBatchCount(@NotNull final BlockPos pos) {
        advancedColonies$batchCounts.remove(pos);
    }

    @Unique
    private boolean advancedColonies$isValidWaterPos(final BlockPos pos) {
        if (pos == null || waterPos == null) {
            return false;
        }
        for (final List<BlockPos> list : waterPos.values()) {
            if (list != null && list.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void advancedColonies$onDeserializeNBT(
            final HolderLookup.Provider provider,
            final CompoundTag compound,
            final CallbackInfo ci) {

        advancedColonies$batchCounts.clear();
        if (compound == null || !compound.contains(TAG_BATCHED_BLOCKS, Tag.TAG_LIST)) {
            return;
        }

        final int maxLimit = ConcreteMixerBatchHelper.getServerMaxBatchSize();
        final ListTag list = compound.getList(TAG_BATCHED_BLOCKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            NbtUtils.readBlockPos(entry, TAG_BATCH_POS).ifPresent(pos -> {
                final int count = Math.max(1, Math.min(entry.getInt(TAG_BATCH_COUNT), maxLimit));
                advancedColonies$batchCounts.put(pos, count);
            });
        }
    }

    @Inject(method = "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void advancedColonies$onSerializeNBT(final CallbackInfoReturnable<CompoundTag> cir) {
        final CompoundTag compound = cir.getReturnValue();
        if (compound == null || advancedColonies$batchCounts.isEmpty()) {
            return;
        }

        final int maxLimit = ConcreteMixerBatchHelper.getServerMaxBatchSize();
        final ListTag list = new ListTag();
        for (final Map.Entry<BlockPos, Integer> entry : advancedColonies$batchCounts.entrySet()) {
            final BlockPos pos = entry.getKey();
            if (advancedColonies$isValidWaterPos(pos)) {
                final CompoundTag tag = new CompoundTag();
                tag.put(TAG_BATCH_POS, NbtUtils.writeBlockPos(pos));
                tag.putInt(TAG_BATCH_COUNT, Math.max(1, Math.min(entry.getValue(), maxLimit)));
                list.add(tag);
            }
        }
        compound.put(TAG_BATCHED_BLOCKS, list);
    }
}
