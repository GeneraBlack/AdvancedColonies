package com.advancedcolonies.common.interaction;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.ai.ITickingStateAI;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Provides citizen interaction enhancements such as the Compass "Mind Reader" AI inspector
 * and suppression of colony permission denial levitation effects.
 *
 * <p>Controlled via {@link AdvancedColoniesConfig#CITIZEN_COMMON}:
 * <ul>
 *   <li>{@code enableCompassMindReader}</li>
 *   <li>{@code disablePermissionLevitation}</li>
 * </ul>
 */
public final class CitizenInteractionHelper {

    private CitizenInteractionHelper() {
    }

    /**
     * Inspects a citizen's current AI state, job state, navigation target, and vitals when a player
     * right-clicks the citizen with a vanilla Compass.
     *
     * @param citizen the citizen entity
     * @param player  the interacting player
     * @param hand    the interaction hand
     * @return {@link InteractionResult#SUCCESS} if handled, or {@code null} to continue normal interaction
     */
    public static InteractionResult tryInspectCitizenWithCompass(
            final EntityCitizen citizen,
            final Player player,
            final InteractionHand hand) {
        if (citizen == null || player == null) {
            return null;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableCompassMindReader.get()) {
            return null;
        }

        final ItemStack heldStack = player.getItemInHand(hand);
        if (!heldStack.is(Items.COMPASS)) {
            return null;
        }

        if (!citizen.level().isClientSide()) {
            final ICitizenData data = citizen.getCitizenData();
            final String name = data != null ? data.getName() : citizen.getName().getString();
            final IState citizenState = citizen.getCitizenAI() != null ? citizen.getCitizenAI().getState() : null;

            String jobName = "Unemployed";
            String workStateName = "None";
            if (data != null && data.getJob() != null) {
                final IJob<?> job = data.getJob();
                jobName = Component.translatable(job.getJobRegistryEntry().getTranslationKey()).getString();
                final ITickingStateAI workAi = job.getWorkerAI();
                if (workAi != null && workAi.getStateAI() != null && workAi.getStateAI().getState() != null) {
                    workStateName = workAi.getStateAI().getState().toString();
                }
            }

            final BlockPos targetPos = citizen.getNavigation() != null
                    ? citizen.getNavigation().getTargetPos()
                    : null;
            final String targetStr = targetPos != null
                    ? String.format("(%d, %d, %d)", targetPos.getX(), targetPos.getY(), targetPos.getZ())
                    : "None";

            final double saturation = data != null ? data.getSaturation() : 0.0;
            final double happiness = data != null && data.getCitizenHappinessHandler() != null
                    ? data.getCitizenHappinessHandler().getHappiness(data.getColony(), data)
                    : 0.0;

            player.sendSystemMessage(Component.translatable(
                    "advancedcolonies.message.mind_reader.header",
                    name,
                    jobName).withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable(
                    "advancedcolonies.message.mind_reader.ai_state",
                    String.valueOf(citizenState),
                    workStateName,
                    targetStr).withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.translatable(
                    "advancedcolonies.message.mind_reader.vitals",
                    String.format("%.1f/%.1f", citizen.getHealth(), citizen.getMaxHealth()),
                    String.format("%.1f", saturation),
                    String.format("%.1f", happiness)).withStyle(ChatFormatting.GREEN));
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Checks whether the levitation penalty for repeated unauthorized colony interactions should be suppressed.
     *
     * @return {@code true} if levitation should not be applied
     */
    public static boolean shouldSuppressPermissionLevitation() {
        return AdvancedColoniesConfig.CITIZEN_COMMON.disablePermissionLevitation.get();
    }
}
