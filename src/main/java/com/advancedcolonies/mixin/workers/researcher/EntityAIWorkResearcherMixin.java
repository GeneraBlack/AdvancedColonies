package com.advancedcolonies.mixin.workers.researcher;

import com.advancedcolonies.workers.researcher.ResearcherWorkHelper;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import com.minecolonies.core.colony.jobs.JobResearch;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.education.EntityAIWorkResearcher;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin Mixin hook on {@link EntityAIWorkResearcher} delegating Knowledge/Mana skill-based
 * study speed scaling and active online research progress to {@link ResearcherWorkHelper}.
 */
@Mixin(value = EntityAIWorkResearcher.class, remap = false)
public abstract class EntityAIWorkResearcherMixin extends AbstractEntityAIInteract<JobResearch, BuildingUniversity> {

    protected EntityAIWorkResearcherMixin(@NotNull final JobResearch job) {
        super(job);
    }

    @Inject(method = "study", at = @At("RETURN"))
    private void advancedColonies$onStudyReturn(final CallbackInfoReturnable<IAIState> cir) {
        if (cir.getReturnValue() == AIWorkerState.START_WORKING) {
            final int scaledDelay = ResearcherWorkHelper.onCompleteBookshelfStudy(
                    this.worker,
                    this.job,
                    this.building,
                    EntityAIWorkResearcher.STUDY_DELAY);
            this.setDelay(scaledDelay);
        }
    }
}
