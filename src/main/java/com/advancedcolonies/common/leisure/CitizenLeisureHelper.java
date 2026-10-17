package com.advancedcolonies.common.leisure;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.MathUtils;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;

/**
 * Manages configurable citizen leisure duration and cooldown intervals.
 *
 * <p>Controlled via {@link AdvancedColoniesConfig#CITIZEN_COMMON}:
 * <ul>
 *   <li>{@code defaultLeisureDurationSeconds}</li>
 *   <li>{@code leisureCooldownSeconds}</li>
 *   <li>{@code maxWorkIdleCreditSeconds}</li>
 * </ul>
 */
public final class CitizenLeisureHelper {

    private CitizenLeisureHelper() {
    }

    /**
     * Computes the updated leisure time in ticks for a citizen during {@code CitizenData.update(int)}.
     *
     * @param citizenData        the citizen data instance
     * @param currentLeisureTime the current leisure time in ticks
     * @param tickRate           the update tick rate
     * @return the updated leisure time in ticks
     */
    public static int computeUpdatedLeisureTime(
            final ICitizenData citizenData,
            final int currentLeisureTime,
            final int tickRate) {
        final int configuredDurationSec = AdvancedColoniesConfig.CITIZEN_COMMON.defaultLeisureDurationSeconds.get();
        if (configuredDurationSec <= 0) {
            return 0;
        }

        if (currentLeisureTime > 0) {
            final int maxTicks = configuredDurationSec * TICKS_SECOND;
            final int clamped = Math.min(currentLeisureTime, maxTicks);
            return Math.max(0, clamped - tickRate);
        }

        final IBuilding home = citizenData.getHomeBuilding();
        final int homeLevel = home == null ? 1 : Math.max(1, home.getBuildingLevel());
        final int baseCooldownSec = Math.max(60, AdvancedColoniesConfig.CITIZEN_COMMON.leisureCooldownSeconds.get());

        // Scale cooldown by home building level (higher home levels reduce wait between leisure periods)
        final double levelFactor = Math.max(0.5D, homeLevel / 2.0D);
        final int scaledCooldownTicks = (int) ((baseCooldownSec * TICKS_SECOND) / levelFactor);
        final int rollBound = Math.max(1, scaledCooldownTicks / Math.max(1, tickRate));

        if (MathUtils.RANDOM.nextInt(rollBound) <= 0) {
            return configuredDurationSec * TICKS_SECOND;
        }

        return 0;
    }
}
