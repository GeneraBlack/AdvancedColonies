package com.advancedcolonies.mixin.buildings.crafter;

import com.advancedcolonies.buildings.crafter.CrafterRecipePriorityHelper;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Thin Mixin hook on {@link AbstractCraftingBuildingModule} delegating max-stock
 * recipe selection to {@link CrafterRecipePriorityHelper}.
 */
@Mixin(value = AbstractCraftingBuildingModule.class, remap = false)
public abstract class AbstractCraftingBuildingModuleMixin extends AbstractBuildingModule {

    @Shadow(remap = false)
    @Final
    protected List<IToken<?>> recipes;

    @Shadow(remap = false)
    @Final
    protected Set<IToken<?>> disabledRecipes;

    @Inject(
            method = "getFirstRecipe(Ljava/util/function/Predicate;)Lcom/minecolonies/api/crafting/IRecipeStorage;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void advancedcolonies$prioritizeMaxStockIngredients(
            final Predicate<ItemStack> stackPredicate,
            final CallbackInfoReturnable<IRecipeStorage> cir) {
        final IRecipeStorage selected = CrafterRecipePriorityHelper.selectMaxStockRecipe(
                this.building,
                this.recipes,
                this.disabledRecipes,
                stackPredicate);
        if (selected != null) {
            cir.setReturnValue(selected);
        }
    }
}
