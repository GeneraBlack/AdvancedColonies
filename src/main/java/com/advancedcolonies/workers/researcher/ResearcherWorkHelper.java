package com.advancedcolonies.workers.researcher;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import com.minecolonies.core.colony.jobs.JobResearch;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Domain helper for University Researcher AI improvements:
 * <ul>
 *   <li>Scales the bookshelf study delay ({@code STUDY_DELAY = 1200}) dynamically with the Researcher's
 *       {@link Skill#Knowledge} level down to a minimum of 200 ticks (10 seconds).</li>
 *   <li>Allows active bookshelf studying during online/singleplayer play (when offline {@code currentMana == 0})
 *       to grant Knowledge/Mana-scaled bonus progress ticks to active University researches.</li>
 * </ul>
 */
public final class ResearcherWorkHelper {

    /** Minimum ticks between bookshelf study actions at high Knowledge skill levels. */
    private static final int MIN_STUDY_DELAY_TICKS = 200;

    /** Ticks of study delay reduced per Knowledge skill level. */
    private static final int DELAY_REDUCTION_PER_LEVEL = 18;

    private ResearcherWorkHelper() {
    }

    /**
     * Computes the scaled bookshelf study delay in ticks based on the Researcher's Knowledge level,
     * and applies online active research progress when {@code enableResearcherSkillScaling} is enabled.
     *
     * @param worker       the Researcher citizen entity
     * @param job          the Researcher's job instance
     * @param building     the University building
     * @param vanillaDelay the vanilla 1200-tick study delay
     * @return the scaled study delay in ticks
     */
    public static int onCompleteBookshelfStudy(
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final JobResearch job,
            @Nullable final BuildingUniversity building,
            final int vanillaDelay) {

        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableResearcherSkillScaling.get()
                || worker == null
                || worker.getCitizenData() == null) {
            return vanillaDelay;
        }

        final int knowledgeLevel = Math.max(1, worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Knowledge));
        final int manaLevel = Math.max(1, worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Mana));

        // In vanilla MineColonies, study() only advances research if offline currentMana > 0.
        // During active online/singleplayer play, grant a skill-scaled chance to advance in-progress research.
        if (building != null && (job == null || job.getCurrentMana() <= 0)) {
            final IColony colony = building.getColony();
            if (colony != null && colony.getResearchManager() != null && colony.getResearchManager().getResearchTree() != null) {
                final List<ILocalResearch> inProgress = colony.getResearchManager().getResearchTree().getResearchInProgress();
                if (!inProgress.isEmpty()) {
                    final double bonusChance = Math.min(1.0D, 0.20D + (knowledgeLevel + manaLevel) * 0.008D);
                    if (worker.getRandom().nextDouble() < bonusChance) {
                        final ILocalResearch research = inProgress.get(worker.getRandom().nextInt(inProgress.size()));
                        final ILocalResearch treeEntry = colony.getResearchManager()
                                .getResearchTree()
                                .getResearch(research.getBranch(), research.getId());
                        if (treeEntry != null && treeEntry.research(
                                colony.getResearchManager().getResearchEffects(),
                                colony.getResearchManager().getResearchTree())) {
                            building.onSuccess(research);
                        }
                        colony.getResearchManager().markDirty();
                    }
                }
            }
        }

        return Math.max(MIN_STUDY_DELAY_TICKS, vanillaDelay - knowledgeLevel * DELAY_REDUCTION_PER_LEVEL);
    }
}
