package com.advancedcolonies.workers.healer;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenDiseaseHandler;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingHospital;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Domain helper for Hospital Healer AI ({@code EntityAIWorkHealer}).
 *
 * <p>Fixes two critical bugs in Original MineColonies 1.21.1:
 * <ol>
 *   <li><b>Injured Patient Blindness</b>: {@code EntityAIWorkHealer} checks {@code isSick()} in
 *       {@code decide()}, {@code requestCure()}, and {@code cure()}, which ignores citizens who
 *       walked to the hospital because they are injured ({@code isHurt() == true}, {@code disease == null})
 *       and immediately deletes their patient file. Even though {@code EntityAIWorkHealer} contains
 *       {@code if (disease == null)} healing branches, they were completely unreachable dead code.</li>
 *   <li><b>Broken Remote/Player Healing Formula</b>: {@code curePlayer()} and {@code wander()} calculate
 *       the heal amount as {@code maxHealth - health - 5 - buildingLevel} (missing parentheses around
 *       {@code (5 - buildingLevel)}), which reduces healing as the Hospital upgrades and often results
 *       in {@code <= 0} healing.</li>
 * </ol>
 */
public final class HealerWorkOptimizer {

    private HealerWorkOptimizer() {
        // Utility class
    }

    /**
     * Scans the hospital building for injured citizens ({@code isHurt()}) and registers their
     * patient files so the Healer treats them alongside diseased citizens.
     *
     * @param hospital the hospital building
     * @param world    the world level
     */
    public static void registerInjuredPatientsInHospital(
            @Nullable final BuildingHospital hospital,
            @Nullable final Level world) {
        if (hospital == null || world == null) {
            return;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedHospitalAI.get()) {
            return;
        }

        for (final AbstractEntityCitizen citizen : WorldUtil.getEntitiesWithinBuilding(
                world,
                AbstractEntityCitizen.class,
                hospital,
                cit -> cit.getCitizenData() != null
                        && cit.getCitizenData().getCitizenDiseaseHandler().isHurt())) {
            hospital.checkOrCreatePatientFile(citizen.getCivilianID());
        }
    }

    /**
     * Evaluates whether a citizen's disease handler requires treatment by the Healer
     * (either diseased via {@code isSick()} or injured via {@code isHurt()} when enhanced
     * Hospital AI is enabled).
     *
     * @param handler the citizen's disease handler
     * @return {@code true} if the citizen is sick or injured and needs Healer treatment
     */
    public static boolean needsHospitalTreatment(@NotNull final ICitizenDiseaseHandler handler) {
        if (handler.isSick()) {
            return true;
        }
        return AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedHospitalAI.get() && handler.isHurt();
    }

    /**
     * Computes a properly scaled heal amount for {@code curePlayer()} and {@code wander()}
     * instead of the buggy vanilla {@code maxHealth - health - 5 - buildingLevel} formula.
     *
     * @param target        the entity being healed (player or remote citizen)
     * @param hospital      the healer's hospital building
     * @param vanillaAmount the original vanilla heal amount
     * @return the corrected heal amount
     */
    public static float computeCorrectedHealAmount(
            @NotNull final LivingEntity target,
            @Nullable final BuildingHospital hospital,
            final float vanillaAmount) {
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableEnhancedHospitalAI.get() || hospital == null) {
            return vanillaAmount;
        }

        final int buildingLevel = Math.max(1, hospital.getBuildingLevel());
        final float missingHealth = Math.max(0.0f, target.getMaxHealth() - target.getHealth());
        final float intendedHeal = missingHealth - Math.max(0, 5 - buildingLevel);
        final float minimumHeal = 4.0f + (buildingLevel * 2.0f);
        return Math.max(intendedHeal, minimumHeal);
    }
}
