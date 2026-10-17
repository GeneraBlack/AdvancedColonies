package com.advancedcolonies.workers.enchanter;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.WorkerFurnaceAndCraftConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.EnchanterStationsModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingEnchanter;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.jobs.JobEnchanter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.StatisticsConstants.CITIZENS_VISITED;
import static com.minecolonies.api.util.constant.TranslationConstants.NO_WORKERS_TO_DRAIN_SET;

/**
 * Domain helper for Enchanter AI optimizations and server-limited batching:
 * <ul>
 *   <li>Requests Books and Ancient Tomes in configurable batches ({@code enchanterBookRequestBatchSize}).</li>
 *   <li>Scales the Enchanter's Mana level threshold with {@code enchanterMaxLevelPerBuildingLevel}.</li>
 *   <li>Supports autonomous Mana gathering at the Enchanter's own tower when {@code allowEnchanterAutonomousDrain} is enabled.</li>
 * </ul>
 */
public final class EnchanterWorkHelper {

    private EnchanterWorkHelper() {
    }

    /**
     * Checks whether the enhanced Enchanter AI is enabled in the server configuration.
     *
     * @return {@code true} if enabled
     */
    public static boolean isEnhancedEnchanterEnabled() {
        return AdvancedColoniesConfig.FURNACE_AND_CRAFT.enableEnhancedEnchanterAI.get();
    }

    /**
     * Returns the server-clamped batch size for Book and Ancient Tome requests.
     *
     * @return clamped batch size between 1 and 64
     */
    public static int getClampedBookBatchSize() {
        final int configured = AdvancedColoniesConfig.FURNACE_AND_CRAFT.enchanterBookRequestBatchSize.get();
        return Math.max(1, Math.min(64, configured));
    }

    /**
     * Checks whether the Enchanter should continue gathering Mana based on primary skill,
     * vanilla base threshold, and the server-limited per-building-level cap.
     *
     * @param primarySkillLevel the Enchanter's current Mana skill level
     * @param manaReqPerLevel   the vanilla minimum Mana threshold
     * @param building          the Enchanter Tower building
     * @param shouldGainMore    whether the Enchanter is actively seeking additional Mana before tome crafting
     * @return {@code true} if the Enchanter should enter {@code ENCHANTER_DRAIN}
     */
    public static boolean shouldGatherMana(
            final int primarySkillLevel,
            final int manaReqPerLevel,
            @NotNull final BuildingEnchanter building,
            final boolean shouldGainMore) {

        if (primarySkillLevel < manaReqPerLevel) {
            return true;
        }
        final int maxPerLevel = Math.max(5, Math.min(50,
                AdvancedColoniesConfig.FURNACE_AND_CRAFT.enchanterMaxLevelPerBuildingLevel.get()));
        final int serverMaxLevel = building.getBuildingLevel() * maxPerLevel;
        return shouldGainMore && primarySkillLevel < serverMaxLevel;
    }

    /**
     * Selects the target building position to drain XP/Mana from, falling back to the Enchanter's
     * own building position when {@code allowEnchanterAutonomousDrain} is enabled and no external
     * worker building is available.
     *
     * @param worker   the Enchanter citizen entity
     * @param building the Enchanter Tower building
     * @return target building position, or {@code null} if none is available and autonomous drain is disabled
     */
    @Nullable
    public static BlockPos selectBuildingToDrainFrom(
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final BuildingEnchanter building) {

        final EnchanterStationsModule module = building.getModule(BuildingModules.ENCHANTER_STATIONS);
        final boolean allowAutonomous = AdvancedColoniesConfig.FURNACE_AND_CRAFT.allowEnchanterAutonomousDrain.get();

        if (module == null || module.getBuildingsToGatherFrom().isEmpty()) {
            if (worker.getCitizenData() != null) {
                worker.getCitizenData().triggerInteraction(
                        new StandardInteraction(Component.translatable(NO_WORKERS_TO_DRAIN_SET), ChatPriority.BLOCKING));
            }
            return allowAutonomous ? building.getPosition() : null;
        }

        final BlockPos randomPos = module.getRandomBuildingToDrainFrom();
        if (randomPos != null) {
            return randomPos;
        }
        return allowAutonomous ? building.getPosition() : null;
    }

    /**
     * Checks whether Ancient Tome crafting is disabled in the Enchanter's crafting module.
     *
     * @param building the Enchanter Tower building
     * @return {@code true} if the Ancient Tome recipe is disabled
     */
    public static boolean isAncientTomeCraftingDisabled(@NotNull final BuildingEnchanter building) {
        final BuildingEnchanter.CraftingModule craftingModule =
                building.getFirstModuleOccurance(BuildingEnchanter.CraftingModule.class);
        if (craftingModule == null) {
            return false;
        }

        for (final IToken<?> token : craftingModule.getRecipes()) {
            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
            if (storage != null
                    && !storage.getInput().isEmpty()
                    && storage.getInput().getFirst().getItem() == ModItems.ancientTome
                    && craftingModule.isDisabled(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Performs the final Mana/XP award and optional target-worker gear blessing at the end of a drain cycle.
     *
     * @param world               the world
     * @param worker              the Enchanter citizen entity
     * @param building            the Enchanter Tower building
     * @param job                 the Enchanter job
     * @param citizenToGatherFrom the target citizen (may be {@code null} during autonomous drain)
     * @param secondarySkillLevel the Enchanter's Knowledge skill level
     * @param gatherWithoutTarget whether the drain completed autonomously without a target citizen nearby
     * @param xpPerDrain          the vanilla XP awarded per drain
     */
    public static void completeDrainCycle(
            @NotNull final Level world,
            @NotNull final AbstractEntityCitizen worker,
            @NotNull final BuildingEnchanter building,
            @NotNull final JobEnchanter job,
            @Nullable final ICitizenData citizenToGatherFrom,
            final int secondarySkillLevel,
            final boolean gatherWithoutTarget,
            final double xpPerDrain) {

        final ICitizenData enchanterData = worker.getCitizenData();
        if (enchanterData == null) {
            return;
        }

        final int bookSlot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), Items.BOOK);
        if (bookSlot != -1 && !gatherWithoutTarget && citizenToGatherFrom != null) {
            final int size = citizenToGatherFrom.getInventory().getSlots();
            final int attempts = Math.max(1, secondarySkillLevel / 5);

            for (int i = 0; i < attempts && size > 0; i++) {
                final int randomSlot = worker.getRandom().nextInt(size);
                final ItemStack stack = citizenToGatherFrom.getInventory().getStackInSlot(randomSlot);
                if (!stack.isEmpty() && stack.isEnchantable()) {
                    EnchantmentHelper.enchantItem(
                            worker.getRandom(),
                            stack,
                            secondarySkillLevel > 50 ? 2 : 1,
                            world.registryAccess(),
                            Optional.empty());
                    worker.getInventoryCitizen().extractItem(bookSlot, 1, false);
                    break;
                }
            }

            final int manaLevels = building.getPosition().equals(job.getPosToDrainFrom())
                    ? building.getBuildingLevel()
                    : building.getBuildingLevel() * 2;
            enchanterData.getCitizenSkillHandler().incrementLevel(Skill.Mana, manaLevels);
            worker.getCitizenExperienceHandler().addExperience(xpPerDrain);
            enchanterData.markDirty(80);
            StatsUtil.trackStat(building, CITIZENS_VISITED, 1);
        } else {
            enchanterData.getCitizenSkillHandler().incrementLevel(Skill.Mana, 1);
            enchanterData.markDirty(80);
        }
    }
}
