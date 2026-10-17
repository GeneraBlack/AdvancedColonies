package com.advancedcolonies.workers.farmer;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Duck interface implemented on {@code FarmField} via Mixin to support multi-season
 * crop rotation with strict server limits on maximum seasons and season durations.
 */
public interface IFarmFieldCropRotation {

    /**
     * Returns the configured number of crop rotation seasons for this field (clamped to server limit).
     *
     * @return season count (1 .. {@code cropRotationMaxSeasons})
     */
    int advancedColonies$getSeasonCount();

    /**
     * Sets the number of crop rotation seasons for this field, clamped to {@code cropRotationMaxSeasons}.
     *
     * @param count desired season count
     */
    void advancedColonies$setSeasonCount(int count);

    /**
     * Returns the active 1-indexed season for this field.
     *
     * @return active season (1 .. {@code getSeasonCount()})
     */
    int advancedColonies$getCurrentSeason();

    /**
     * Sets the active 1-indexed season for this field, clamped to {@code getSeasonCount()}.
     *
     * @param season desired season index
     */
    void advancedColonies$setCurrentSeason(int season);

    /**
     * Returns the current day within the active season.
     *
     * @return current day (at least 1)
     */
    int advancedColonies$getCurrentDay();

    /**
     * Sets the current day within the active season, clamped to the season's duration.
     *
     * @param day desired day
     */
    void advancedColonies$setCurrentDay(int day);

    /**
     * Returns the duration in Minecraft days for the given 1-indexed season (clamped to server limit).
     *
     * @param season the 1-indexed season
     * @return duration in days
     */
    int advancedColonies$getSeasonDuration(int season);

    /**
     * Sets the duration in Minecraft days for the given 1-indexed season, clamped to {@code cropRotationMaxSeasonDays}.
     *
     * @param season the 1-indexed season
     * @param days   duration in days
     */
    void advancedColonies$setSeasonDuration(int season, int days);

    /**
     * Returns the seed configured for the given 1-indexed season.
     *
     * @param season the 1-indexed season
     * @return the configured seed stack, or {@link ItemStack#EMPTY}
     */
    @NotNull
    ItemStack advancedColonies$getSeasonSeed(int season);

    /**
     * Sets the seed stack for the given 1-indexed season.
     *
     * @param season the 1-indexed season
     * @param stack  the seed stack
     */
    void advancedColonies$setSeasonSeed(int season, @NotNull ItemStack stack);

    /**
     * Advances the field's crop rotation calendar to the given colony/world day.
     *
     * @param worldDay the current colony or world day
     */
    void advancedColonies$advanceDay(int worldDay);
}
