package com.advancedcolonies.buildings.tavern;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import static com.minecolonies.api.util.constant.StatisticsConstants.VISITORS_RECRUITED;
import static com.minecolonies.api.util.constant.TranslationConstants.MESSAGE_RECRUITMENT_SUCCESS;
import static com.minecolonies.api.util.constant.TranslationConstants.MESSAGE_RECRUITMENT_SUCCESS_CUSTOM;
import static com.minecolonies.api.util.constant.TranslationConstants.WARNING_NO_COLONY_SPACE;

/**
 * Provides direct right-click recruitment of Tavern visitors and prevents paid recruits from absconding.
 *
 * <p>Controlled via {@link AdvancedColoniesConfig#CITIZEN_COMMON#enableTavernDirectRecruitment}.
 */
public final class TavernRecruitmentHelper {

    private TavernRecruitmentHelper() {
    }

    /**
     * Checks whether paid visitors in {@code RecruitmentInteraction} should be prevented from absconding
     * with the player's recruitment items.
     *
     * @return {@code true} if direct/guaranteed recruitment is enabled
     */
    public static boolean shouldPreventVisitorAbsconding() {
        return AdvancedColoniesConfig.CITIZEN_COMMON.enableTavernDirectRecruitment.get();
    }

    /**
     * Attempts to directly recruit a {@link VisitorCitizen} when the player right-clicks them while holding
     * the visitor's required recruitment item.
     *
     * @param visitor the tavern visitor entity
     * @param player  the interacting player
     * @param hand    the interaction hand
     * @return {@link InteractionResult#CONSUME} if recruited on server, or {@code null} to fall back to default interaction
     */
    public static InteractionResult tryDirectRecruitWithHeldItem(
            final VisitorCitizen visitor,
            final Player player,
            final InteractionHand hand) {
        if (visitor == null || player == null || player.isShiftKeyDown()) {
            return null;
        }
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableTavernDirectRecruitment.get()) {
            return null;
        }

        final ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.isEmpty()) {
            return null;
        }

        if (visitor.level().isClientSide()) {
            return null;
        }

        final ICitizenData rawData = visitor.getCitizenData();
        if (!(rawData instanceof IVisitorData visitorData)) {
            return null;
        }

        final ItemStack recruitCost = visitorData.getRecruitCost();
        if (recruitCost == null || recruitCost.isEmpty() || !ItemStack.isSameItem(heldStack, recruitCost)) {
            return null;
        }

        final IColony colony = visitorData.getColony();
        if (colony == null) {
            return null;
        }

        if (colony.getCitizenManager().getCurrentCitizenCount() >= colony.getCitizenManager().getPotentialMaxCitizens()) {
            MessageUtils.format(WARNING_NO_COLONY_SPACE).sendTo(player);
            return InteractionResult.CONSUME;
        }

        final boolean paid = player.isCreative() || InventoryUtils.attemptReduceStackInItemHandler(
                new InvWrapper(player.getInventory()),
                recruitCost,
                recruitCost.getCount(),
                true,
                true);
        if (!paid) {
            return null;
        }

        colony.getVisitorManager().removeCivilian(visitorData);
        visitorData.setHomeBuilding(null);
        visitorData.setJob(null);

        final IBuilding tavern = colony.getServerBuildingManager()
                .getFirstBuildingMatching(b -> b.getBuildingType() == ModBuildings.tavern.get());
        if (tavern != null) {
            StatsUtil.trackStat(tavern, VISITORS_RECRUITED, 1);
        }
        colony.getStatisticsManager().increment(VISITORS_RECRUITED, colony.getDay());

        final ICitizenData newCitizen = colony.getCitizenManager().createAndRegisterCivilianData();
        newCitizen.deserializeNBT(player.level().registryAccess(), visitorData.serializeNBT(player.level().registryAccess()));
        newCitizen.setParents("", "");
        newCitizen.setLastPosition(visitorData.getLastPosition());

        newCitizen.updateEntityIfNecessary();
        visitorData.getEntity().ifPresent(e -> e.remove(Entity.RemovalReason.DISCARDED));

        if (visitorData.hasCustomTexture()) {
            MessageUtils.format(MESSAGE_RECRUITMENT_SUCCESS_CUSTOM, visitorData.getName()).sendTo(colony).forAllPlayers();
        } else {
            MessageUtils.format(MESSAGE_RECRUITMENT_SUCCESS, visitorData.getName()).sendTo(colony).forAllPlayers();
        }

        IMinecoloniesAPI.getInstance().getEventBus().post(
                new CitizenAddedModEvent(newCitizen, CitizenAddedModEvent.CitizenAddedSource.HIRED));
        return InteractionResult.CONSUME;
    }
}
