package com.advancedcolonies.mixin.workers.farmer;

import com.advancedcolonies.workers.farmer.AgricultureWorkHelper;
import com.advancedcolonies.workers.farmer.IFarmFieldCropRotation;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.colony.buildingextensions.AbstractBuildingExtension;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin on {@link FarmField} implementing {@link IFarmFieldCropRotation} for multi-season
 * crop rotation while strictly enforcing server limits ({@code cropRotationMaxSeasons}
 * and {@code cropRotationMaxSeasonDays}).
 */
@Mixin(value = FarmField.class, remap = false)
public abstract class FarmFieldMixin extends AbstractBuildingExtension implements IFarmFieldCropRotation {

    @Shadow
    private ItemStack seed;

    @Unique
    private int advancedColonies$lastRecordedDay = -1;

    @Unique
    private int advancedColonies$seasonCount = 1;

    @Unique
    private int advancedColonies$currentSeason = 1;

    @Unique
    private int advancedColonies$currentDay = 1;

    @Unique
    private final Map<Integer, Integer> advancedColonies$seasonDurations = new HashMap<>();

    @Unique
    private final Map<Integer, ItemStack> advancedColonies$seasonalSeeds = new HashMap<>();

    protected FarmFieldMixin(
            final BuildingExtensionRegistries.BuildingExtensionEntry fieldType,
            final BlockPos position) {
        super(fieldType, position);
    }

    @Override
    public int advancedColonies$getSeasonCount() {
        return Math.max(1, Math.min(advancedColonies$seasonCount, AgricultureWorkHelper.getClampedMaxSeasons()));
    }

    @Override
    public void advancedColonies$setSeasonCount(final int count) {
        this.advancedColonies$seasonCount = Math.max(1, Math.min(count, AgricultureWorkHelper.getClampedMaxSeasons()));
        if (this.advancedColonies$currentSeason > this.advancedColonies$seasonCount) {
            this.advancedColonies$currentSeason = this.advancedColonies$seasonCount;
        }
    }

    @Override
    public int advancedColonies$getCurrentSeason() {
        return Math.max(1, Math.min(this.advancedColonies$currentSeason, advancedColonies$getSeasonCount()));
    }

    @Override
    public void advancedColonies$setCurrentSeason(final int season) {
        this.advancedColonies$currentSeason = Math.max(1, Math.min(season, advancedColonies$getSeasonCount()));
    }

    @Override
    public int advancedColonies$getCurrentDay() {
        return Math.max(1, Math.min(this.advancedColonies$currentDay, advancedColonies$getSeasonDuration(advancedColonies$getCurrentSeason())));
    }

    @Override
    public void advancedColonies$setCurrentDay(final int day) {
        this.advancedColonies$currentDay = Math.max(1, Math.min(day, advancedColonies$getSeasonDuration(advancedColonies$getCurrentSeason())));
    }

    @Override
    public int advancedColonies$getSeasonDuration(final int season) {
        final int raw = this.advancedColonies$seasonDurations.getOrDefault(season, 7);
        return Math.max(1, Math.min(raw, AgricultureWorkHelper.getClampedMaxSeasonDays()));
    }

    @Override
    public void advancedColonies$setSeasonDuration(final int season, final int days) {
        if (season >= 1 && season <= AgricultureWorkHelper.getClampedMaxSeasons()) {
            this.advancedColonies$seasonDurations.put(
                    season,
                    Math.max(1, Math.min(days, AgricultureWorkHelper.getClampedMaxSeasonDays())));
        }
    }

    @Override
    @NotNull
    public ItemStack advancedColonies$getSeasonSeed(final int season) {
        return this.advancedColonies$seasonalSeeds.getOrDefault(season, ItemStack.EMPTY);
    }

    @Override
    public void advancedColonies$setSeasonSeed(final int season, @NotNull final ItemStack stack) {
        if (season >= 1 && season <= AgricultureWorkHelper.getClampedMaxSeasons()) {
            final ItemStack copy = stack.copy();
            if (!copy.isEmpty()) {
                copy.setCount(1);
            }
            this.advancedColonies$seasonalSeeds.put(season, copy);
        }
    }

    @Override
    public void advancedColonies$advanceDay(final int worldDay) {
        if (!AgricultureWorkHelper.isCropRotationEnabled() || advancedColonies$getSeasonCount() <= 1) {
            return;
        }
        if (advancedColonies$lastRecordedDay < 0) {
            advancedColonies$lastRecordedDay = worldDay;
            return;
        }
        final int elapsed = worldDay - advancedColonies$lastRecordedDay;
        if (elapsed <= 0) {
            return;
        }
        advancedColonies$lastRecordedDay = worldDay;
        advancedColonies$currentDay += elapsed;

        int maxSeasons = advancedColonies$getSeasonCount();
        int duration = advancedColonies$getSeasonDuration(advancedColonies$currentSeason);
        while (advancedColonies$currentDay > duration) {
            advancedColonies$currentDay -= Math.max(1, duration);
            advancedColonies$currentSeason++;
            if (advancedColonies$currentSeason > maxSeasons) {
                advancedColonies$currentSeason = 1;
            }
            duration = advancedColonies$getSeasonDuration(advancedColonies$currentSeason);
        }
    }

    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void advancedColonies$onGetSeed(final CallbackInfoReturnable<ItemStack> cir) {
        if (!AgricultureWorkHelper.isCropRotationEnabled() || advancedColonies$getSeasonCount() <= 1) {
            return;
        }
        if (advancedColonies$seasonalSeeds.isEmpty() && seed != null && !seed.isEmpty()) {
            advancedColonies$setSeasonSeed(1, seed);
        }
        final ItemStack activeSeed = advancedColonies$getSeasonSeed(advancedColonies$getCurrentSeason());
        if (!activeSeed.isEmpty()) {
            final ItemStack copy = activeSeed.copy();
            copy.setCount(1);
            this.seed = copy;
            cir.setReturnValue(copy);
        }
    }

    @Inject(method = "serializeNBT", at = @At("TAIL"))
    private void advancedColonies$onSerializeNBT(
            final HolderLookup.Provider provider,
            final CallbackInfoReturnable<CompoundTag> cir) {

        final CompoundTag tag = cir.getReturnValue();
        if (tag == null) {
            return;
        }

        tag.putInt("AC_SeasonCount", advancedColonies$getSeasonCount());
        tag.putInt("AC_CurrentSeason", advancedColonies$getCurrentSeason());
        tag.putInt("AC_CurrentDay", advancedColonies$getCurrentDay());
        tag.putInt("AC_LastDay", advancedColonies$lastRecordedDay);

        final CompoundTag durationTag = new CompoundTag();
        for (final Map.Entry<Integer, Integer> entry : advancedColonies$seasonDurations.entrySet()) {
            durationTag.putInt(String.valueOf(entry.getKey()),
                    Math.max(1, Math.min(entry.getValue(), AgricultureWorkHelper.getClampedMaxSeasonDays())));
        }
        tag.put("AC_SeasonDurations", durationTag);

        final CompoundTag seedTag = new CompoundTag();
        for (final Map.Entry<Integer, ItemStack> entry : advancedColonies$seasonalSeeds.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                seedTag.put(String.valueOf(entry.getKey()), entry.getValue().saveOptional(provider));
            }
        }
        tag.put("AC_SeasonalSeeds", seedTag);
    }

    @Inject(method = "deserializeNBT", at = @At("TAIL"))
    private void advancedColonies$onDeserializeNBT(
            final HolderLookup.Provider provider,
            final CompoundTag tag,
            final CallbackInfo ci) {

        if (tag == null) {
            return;
        }

        if (tag.contains("AC_SeasonCount")) {
            advancedColonies$setSeasonCount(tag.getInt("AC_SeasonCount"));
        }
        if (tag.contains("AC_CurrentSeason")) {
            advancedColonies$setCurrentSeason(tag.getInt("AC_CurrentSeason"));
        }
        if (tag.contains("AC_LastDay")) {
            advancedColonies$lastRecordedDay = tag.getInt("AC_LastDay");
        }

        advancedColonies$seasonDurations.clear();
        if (tag.contains("AC_SeasonDurations")) {
            final CompoundTag durationTag = tag.getCompound("AC_SeasonDurations");
            for (final String key : durationTag.getAllKeys()) {
                try {
                    final int seasonIdx = Integer.parseInt(key);
                    advancedColonies$setSeasonDuration(seasonIdx, durationTag.getInt(key));
                } catch (final NumberFormatException ignored) {
                }
            }
        }

        advancedColonies$seasonalSeeds.clear();
        if (tag.contains("AC_SeasonalSeeds")) {
            final CompoundTag seedTag = tag.getCompound("AC_SeasonalSeeds");
            for (final String key : seedTag.getAllKeys()) {
                try {
                    final int seasonIdx = Integer.parseInt(key);
                    final ItemStack parsed = ItemStack.parseOptional(provider, seedTag.getCompound(key));
                    advancedColonies$setSeasonSeed(seasonIdx, parsed);
                } catch (final NumberFormatException ignored) {
                }
            }
        }

        if (tag.contains("AC_CurrentDay")) {
            advancedColonies$setCurrentDay(tag.getInt("AC_CurrentDay"));
        }
    }

    @Inject(method = "serialize", at = @At("TAIL"))
    private void advancedColonies$onSerializeBuffer(
            final RegistryFriendlyByteBuf buf,
            final CallbackInfo ci) {

        buf.writeVarInt(advancedColonies$getSeasonCount());
        buf.writeVarInt(advancedColonies$getCurrentSeason());
        buf.writeVarInt(advancedColonies$getCurrentDay());
        buf.writeInt(advancedColonies$lastRecordedDay);

        buf.writeVarInt(advancedColonies$seasonDurations.size());
        for (final Map.Entry<Integer, Integer> entry : advancedColonies$seasonDurations.entrySet()) {
            buf.writeVarInt(entry.getKey());
            buf.writeVarInt(Math.max(1, Math.min(entry.getValue(), AgricultureWorkHelper.getClampedMaxSeasonDays())));
        }

        buf.writeVarInt(advancedColonies$seasonalSeeds.size());
        for (final Map.Entry<Integer, ItemStack> entry : advancedColonies$seasonalSeeds.entrySet()) {
            buf.writeVarInt(entry.getKey());
            Utils.serializeCodecMess(buf, entry.getValue());
        }
    }

    @Inject(method = "deserialize", at = @At("TAIL"))
    private void advancedColonies$onDeserializeBuffer(
            final RegistryFriendlyByteBuf buf,
            final CallbackInfo ci) {

        advancedColonies$setSeasonCount(buf.readVarInt());
        advancedColonies$setCurrentSeason(buf.readVarInt());
        final int day = buf.readVarInt();
        advancedColonies$lastRecordedDay = buf.readInt();

        final int durationCount = buf.readVarInt();
        advancedColonies$seasonDurations.clear();
        for (int i = 0; i < durationCount; i++) {
            final int key = buf.readVarInt();
            final int value = buf.readVarInt();
            advancedColonies$setSeasonDuration(key, value);
        }

        final int seedCount = buf.readVarInt();
        advancedColonies$seasonalSeeds.clear();
        for (int i = 0; i < seedCount; i++) {
            final int key = buf.readVarInt();
            final ItemStack stack = Utils.deserializeCodecMess(buf);
            advancedColonies$setSeasonSeed(key, stack);
        }

        advancedColonies$setCurrentDay(day);
    }
}
