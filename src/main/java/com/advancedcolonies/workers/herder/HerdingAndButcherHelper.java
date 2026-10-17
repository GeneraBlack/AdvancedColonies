package com.advancedcolonies.workers.herder;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Domain helper for Herder and Butcher AI enhancements:
 * <ul>
 *   <li>Exponential animal capacity scaling ({@code 2^level}) when {@code exponentialHerderAnimalCap} is enabled.</li>
 *   <li>Single-strike butchering when {@code butcherInstantKill} is enabled.</li>
 *   <li>Enhanced drop collection within herding pens when {@code enableEnhancedHerderAI} is enabled.</li>
 * </ul>
 */
public final class HerdingAndButcherHelper {

    private HerdingAndButcherHelper() {
    }

    /**
     * Checks whether Butchers/Herders should butcher target animals in a single strike.
     *
     * @return {@code true} if instant butchering is enabled
     */
    public static boolean isInstantButcherKillEnabled() {
        return AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.butcherInstantKill.get();
    }

    /**
     * Computes the attack damage to deal when butchering an animal.
     *
     * @param animal        the animal being butchered
     * @param vanillaDamage the vanilla butchering attack damage
     * @return lethal damage if {@code butcherInstantKill} is enabled, otherwise {@code vanillaDamage}
     */
    public static float computeButcherDamage(@Nullable final Animal animal, final double vanillaDamage) {
        if (isInstantButcherKillEnabled() && animal != null) {
            return Math.max(animal.getMaxHealth() * 2.5F, 100.0F);
        }
        return (float) vanillaDamage;
    }

    /**
     * Computes the maximum number of animals kept in a Herder Hut before butchering excess adults.
     * When {@code exponentialHerderAnimalCap} is enabled, scales as {@code 2^level} instead of {@code 2 * level}.
     *
     * @param building            the Herder Hut building
     * @param maxAnimalMultiplier the vanilla animal multiplier
     * @return the maximum animal count for the hut
     */
    public static int computeMaxAnimals(@Nullable final AbstractBuilding building, final int maxAnimalMultiplier) {
        if (building == null) {
            return maxAnimalMultiplier;
        }
        final int level = Math.max(1, building.getBuildingLevel());
        if (AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.exponentialHerderAnimalCap.get()) {
            return 1 << Math.min(level, 10);
        }
        return level * maxAnimalMultiplier;
    }

    /**
     * Computes the probability of butchering an adult animal when {@code exponentialHerderAnimalCap}
     * is enabled, or returns {@code null} to use the vanilla calculation.
     *
     * @param building            the Herder Hut building
     * @param allAnimals          all compatible animals in the building area
     * @param maxAnimalMultiplier the vanilla multiplier
     * @return the custom butcher probability, or {@code null} if vanilla calculation applies
     */
    @Nullable
    public static Double evaluateChanceToButcher(
            @Nullable final AbstractBuilding building,
            @NotNull final List<? extends Animal> allAnimals,
            final int maxAnimalMultiplier) {

        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.exponentialHerderAnimalCap.get() || building == null) {
            return null;
        }

        final int maxAnimals = computeMaxAnimals(building, maxAnimalMultiplier);
        if (!building.getSetting(AbstractBuilding.BREEDING).getValue() && allAnimals.size() <= maxAnimals) {
            return 0.0D;
        }

        int grownUp = 0;
        for (final Animal animal : allAnimals) {
            if (!animal.isBaby()) {
                grownUp++;
            }
        }

        if (grownUp <= 3) {
            return 0.0D;
        }

        return 0.5D * (Math.pow(grownUp, 4) / Math.pow(maxAnimals, 4));
    }

    /**
     * Sweeps stray item entities inside the Herder Hut pen directly to the herder during {@code pickupItems}
     * when {@code enableEnhancedHerderAI} is enabled.
     *
     * @param worker the Herder citizen entity
     * @param items  the items found inside the building bounds
     */
    public static void collectPenItems(
            @Nullable final AbstractEntityCitizen worker,
            @Nullable final List<? extends ItemEntity> items) {

        if (!AdvancedColoniesConfig.AGRICULTURE_AND_HERDING.enableEnhancedHerderAI.get()
                || worker == null
                || items == null
                || items.isEmpty()) {
            return;
        }

        for (final ItemEntity item : items) {
            if (item != null && item.isAlive()) {
                item.setPos(worker.getX(), worker.getY() + 0.25D, worker.getZ());
                item.setDeltaMovement(0.0D, 0.0D, 0.0D);
                item.setNoPickUpDelay();
            }
        }
        CitizenItemUtils.pickupItems(worker);
    }
}
