package com.advancedcolonies.common.leisure;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.CitizenCommonConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenMournHandler;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.JobBaker;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.colony.jobs.JobChef;
import com.minecolonies.core.colony.jobs.JobCook;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.jobs.JobHealer;
import com.minecolonies.core.colony.jobs.JobUndertaker;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Domain helper for intelligent citizen mourning control:
 * <ul>
 *   <li>Exempts essential emergency professions (Healer, Cook, Chef, Baker, Builder, Courier) from
 *       colony-paralyzing mourning after raids/deaths when {@code exemptEssentialWorkersFromMourning} is enabled.</li>
 *   <li>Caps mourning duration to {@code maxMourningDurationSeconds} (e.g. 120 seconds of paying respects at the
 *       Graveyard/Town Hall instead of an entire 20-minute workday).</li>
 *   <li>Prevents {@code NoSuchElementException} in {@code CitizenAI} if {@code isMourning()} is true while
 *       {@code getDeceasedCitizens()} is empty.</li>
 * </ul>
 */
public final class CitizenMournHelper {

    /** Tracks the server game-tick timestamp when a citizen began mourning (keyed by colonyId << 32 | citizenId). */
    private static final Map<Long, Long> MOURN_START_TICK_BY_CITIZEN = new ConcurrentHashMap<>();

    private CitizenMournHelper() {
    }

    /**
     * Checks whether a citizen's job is considered an essential emergency profession that should
     * remain on duty after a colony death.
     *
     * @param citizen the citizen data to evaluate
     * @return {@code true} if the citizen should be exempt from mourning
     */
    public static boolean isExemptFromMourning(@Nullable final ICitizenData citizen) {
        if (citizen == null) {
            return false;
        }

        final CitizenCommonConfig cfg = AdvancedColoniesConfig.CITIZEN_COMMON;
        if (cfg.maxMourningDurationSeconds.get() <= 0) {
            return true;
        }

        final IJob<?> job = citizen.getJob();
        if (job instanceof AbstractJobGuard<?> || job instanceof JobUndertaker) {
            return true;
        }

        if (!cfg.exemptEssentialWorkersFromMourning.get()) {
            return false;
        }

        return job instanceof JobHealer
                || job instanceof JobCook
                || job instanceof JobChef
                || job instanceof JobBaker
                || job instanceof JobBuilder
                || job instanceof JobDeliveryman;
    }

    /**
     * Sanitizes a citizen's mourn handler after {@code updateCitizenMourn} or {@code onWakeUp},
     * clearing mourning state for exempt essential workers or when mourning is disabled, and
     * guarding against empty {@code deceasedCitizens} sets.
     *
     * @param citizen the citizen data to sanitize
     */
    public static void sanitizeCitizenMournState(@Nullable final ICitizenData citizen) {
        if (citizen == null || citizen.getCitizenMournHandler() == null) {
            return;
        }

        final ICitizenMournHandler handler = citizen.getCitizenMournHandler();
        if (isExemptFromMourning(citizen) || handler.getDeceasedCitizens().isEmpty()) {
            handler.setMourning(false);
            handler.clearDeceasedCitizen();
            MOURN_START_TICK_BY_CITIZEN.remove(composeKey(citizen));
        }
    }

    /**
     * Evaluates whether a mourning citizen has completed their configured mourning duration
     * ({@code maxMourningDurationSeconds}) and should return to normal work.
     *
     * @param citizenEntity the mourning citizen entity
     * @return {@code true} if mourning has finished and the citizen should transition back to IDLE/WORK
     */
    public static boolean checkAndCompleteMourningIfExpired(@Nullable final EntityCitizen citizenEntity) {
        if (citizenEntity == null || citizenEntity.getCitizenData() == null) {
            return false;
        }

        final ICitizenData data = citizenEntity.getCitizenData();
        final ICitizenMournHandler handler = data.getCitizenMournHandler();
        if (handler == null) {
            return false;
        }

        final int maxSeconds = AdvancedColoniesConfig.CITIZEN_COMMON.maxMourningDurationSeconds.get();
        if (maxSeconds <= 0 || isExemptFromMourning(data) || handler.getDeceasedCitizens().isEmpty()) {
            completeMourning(citizenEntity, data, handler);
            return true;
        }

        // If configured to the full 1200s (20 minutes) vanilla day, let vanilla onWakeUp handle it
        if (maxSeconds >= 1200) {
            return false;
        }

        final long gameTime = citizenEntity.level().getGameTime();
        final long key = composeKey(data);
        final long startTick = MOURN_START_TICK_BY_CITIZEN.computeIfAbsent(key, k -> gameTime);

        if (gameTime - startTick < startTick && gameTime < startTick) {
            MOURN_START_TICK_BY_CITIZEN.put(key, gameTime);
            return false;
        }

        final long maxTicks = maxSeconds * 20L;
        if (gameTime - startTick >= maxTicks) {
            completeMourning(citizenEntity, data, handler);
            return true;
        }

        return false;
    }

    private static void completeMourning(
            final EntityCitizen citizenEntity,
            final ICitizenData data,
            final ICitizenMournHandler handler) {
        handler.setMourning(false);
        handler.clearDeceasedCitizen();
        data.setVisibleStatus(null);
        MOURN_START_TICK_BY_CITIZEN.remove(composeKey(data));
    }

    private static long composeKey(final ICitizenData data) {
        final int colonyId = data.getColony() != null ? data.getColony().getID() : 0;
        return ((long) colonyId << 32) | (data.getId() & 0xFFFFFFFFL);
    }
}
