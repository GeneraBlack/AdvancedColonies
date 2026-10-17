package com.advancedcolonies.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration settings for furnace-using workers (Smelter, Stone Smelter,
 * Chef, Baker, Glassblower, Dyer), recipe stock priority, and enchanted tools.
 */
public final class WorkerFurnaceAndCraftConfig {

    public final ModConfigSpec.BooleanValue enableFurnaceAcceleration;
    public final ModConfigSpec.BooleanValue autoExtractOnFurnaceFill;
    public final ModConfigSpec.BooleanValue enableEnhancedSmelteryAI;
    public final ModConfigSpec.BooleanValue enableEnhancedCookhouseChefAI;
    public final ModConfigSpec.BooleanValue enableEnhancedBakerAI;
    public final ModConfigSpec.BooleanValue prioritizeMaxStockRecipeIngredients;
    public final ModConfigSpec.BooleanValue allowEarlyEnchantedTools;
    public final ModConfigSpec.DoubleValue enchantLevelToHutScale;

    public WorkerFurnaceAndCraftConfig(final ModConfigSpec.Builder builder) {
        builder.comment("Furnace Workers, Crafting & Tool Enchantment Settings").push("furnaceAndCrafting");

        this.enableFurnaceAcceleration = builder
                .comment("Workers actively accelerate lit furnaces based on their skill levels.")
                .define("enableFurnaceAcceleration", true);

        this.autoExtractOnFurnaceFill = builder
                .comment("Automatically extracts finished items from a furnace output slot when refilling inputs.")
                .define("autoExtractOnFurnaceFill", true);

        this.enableEnhancedSmelteryAI = builder
                .comment("Enables streamlined multi-furnace management for Smelters, Stone Smelters, Glassblowers, and Dyers.")
                .define("enableEnhancedSmelteryAI", true);

        this.enableEnhancedCookhouseChefAI = builder
                .comment("Enables streamlined cooking and furnace management for Cookhouse Chefs.")
                .define("enableEnhancedCookhouseChefAI", true);

        this.enableEnhancedBakerAI = builder
                .comment("Enables streamlined baking workflow for Bakers.")
                .define("enableEnhancedBakerAI", true);

        this.prioritizeMaxStockRecipeIngredients = builder
                .comment("Crafters automatically pick the alternative recipe ingredient with the highest stock in the colony.")
                .define("prioritizeMaxStockRecipeIngredients", true);

        this.allowEarlyEnchantedTools = builder
                .comment("Allows workers at lower hut levels to use lightly enchanted tools.")
                .define("allowEarlyEnchantedTools", true);

        this.enchantLevelToHutScale = builder
                .comment("Maximum enchantment level multiplier allowed per building level when allowEarlyEnchantedTools is enabled.")
                .defineInRange("enchantLevelToHutScale", 0.5D, 0.2D, 2.0D);

        builder.pop();
    }
}
