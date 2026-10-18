package com.advancedcolonies.buildings.crafter;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.MultiOutputRecipe;
import com.minecolonies.api.util.InventoryUtils;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Selects the optimal crafting recipe among multiple matching recipes by comparing
 * available ingredient stocks across the crafter building and colony Warehouses.
 */
public final class CrafterRecipePriorityHelper {

    private CrafterRecipePriorityHelper() {
    }

    /**
     * When multiple active recipes can produce an item matching {@code stackPredicate}
     * and {@code prioritizeMaxStockRecipeIngredients} is enabled, selects the recipe
     * whose required ingredients have the highest available stock ratio in the colony.
     *
     * @param building        the crafter building
     * @param recipes         the ordered list of recipe tokens on the module
     * @param disabledRecipes the collection of disabled recipe tokens
     * @param stackPredicate  the desired output predicate
     * @return the best matching recipe storage, or {@code null} to fall back to vanilla
     */
    @Nullable
    public static IRecipeStorage selectMaxStockRecipe(
            @Nullable final IBuilding building,
            final List<IToken<?>> recipes,
            final Collection<IToken<?>> disabledRecipes,
            final Predicate<ItemStack> stackPredicate) {
        if (building == null
                || building.getColony() == null
                || !AdvancedColoniesConfig.FURNACE_AND_CRAFT.prioritizeMaxStockRecipeIngredients.get()) {
            return null;
        }

        final List<IRecipeStorage> candidates = new ArrayList<>();
        for (final IToken<?> token : recipes) {
            if (disabledRecipes.contains(token)) {
                continue;
            }
            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
            if (storage != null
                    && (stackPredicate.test(storage.getPrimaryOutput())
                    || InventoryUtils.getFirstMatch(storage.getAlternateOutputs(), stackPredicate) != null)) {
                if (storage.getRecipeType() instanceof MultiOutputRecipe
                        && storage.getClassicForMultiOutput(stackPredicate) == null) {
                    continue;
                }
                candidates.add(storage);
            }
        }

        if (candidates.size() <= 1) {
            return null;
        }

        IRecipeStorage bestCandidate = candidates.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (final IRecipeStorage candidate : candidates) {
            final int score = evaluateIngredientStockScore(building, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }

        if (bestCandidate.getRecipeType() instanceof MultiOutputRecipe) {
            final IToken<?> token = IColonyManager.getInstance()
                    .getRecipeManager()
                    .checkOrAddRecipe(bestCandidate.getClassicForMultiOutput(stackPredicate));
            return IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
        }

        return bestCandidate;
    }

    /**
     * Computes a stock score for a recipe based on the bottleneck ingredient availability
     * across both the crafter building and all colony Warehouses.
     */
    private static int evaluateIngredientStockScore(
            final IBuilding building,
            final IRecipeStorage recipe) {
        final List<ItemStorage> inputs = recipe.getCleanedInput();
        if (inputs.isEmpty()) {
            return 0;
        }

        int minBatchesAvailable = Integer.MAX_VALUE;
        for (final ItemStorage input : inputs) {
            final int required = Math.max(1, input.getAmount());
            int available = InventoryUtils.getCountFromBuilding(building, input);
            for (final IWareHouse warehouse : building.getColony().getServerBuildingManager().getWareHouses()) {
                available += InventoryUtils.getCountFromBuilding(warehouse, input);
            }
            final int batches = available / required;
            if (batches < minBatchesAvailable) {
                minBatchesAvailable = batches;
            }
        }
        return minBatchesAvailable == Integer.MAX_VALUE ? 0 : minBatchesAvailable;
    }
}
