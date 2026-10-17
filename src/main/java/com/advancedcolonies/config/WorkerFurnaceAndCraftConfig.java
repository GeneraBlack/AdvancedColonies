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
    public final ModConfigSpec.BooleanValue enableContinuousBatchCrafting;
    public final ModConfigSpec.BooleanValue enableSmoothCrafterSpeedScaling;

    // Enchanter AI & Server Limits
    public final ModConfigSpec.BooleanValue enableEnhancedEnchanterAI;
    public final ModConfigSpec.IntValue enchanterBookRequestBatchSize;
    public final ModConfigSpec.IntValue enchanterMaxLevelPerBuildingLevel;
    public final ModConfigSpec.BooleanValue allowEnchanterAutonomousDrain;

    // Concrete Mixer Batching & Server Limits
    public final ModConfigSpec.BooleanValue enableConcreteMixerBatching;
    public final ModConfigSpec.IntValue concreteMixerSkillDivisor;
    public final ModConfigSpec.IntValue concreteMixerMaxBatchSize;

    public WorkerFurnaceAndCraftConfig(final ModConfigSpec.Builder builder) {
        builder.comment("Furnace Workers, Crafting, Enchanter, Concrete Mixer & Tool Enchantment Settings").push("furnaceAndCrafting");

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

        this.enableContinuousBatchCrafting = builder
                .comment("Allows crafters to immediately craft the next item in a multi-item batch when ingredients are still in their inventory, skipping redundant 4-second state transitions and rack scans.")
                .define("enableContinuousBatchCrafting", true);

        this.enableSmoothCrafterSpeedScaling = builder
                .comment("Replaces coarse integer-division crafting time truncation with smooth floating-point skill scaling across all citizen levels.")
                .define("enableSmoothCrafterSpeedScaling", true);

        this.enableEnhancedEnchanterAI = builder
                .comment("Enables enhanced Enchanter AI (batch book/tome requests, scaled Mana level cap, and autonomous XP draining when target workers are unavailable).")
                .define("enableEnhancedEnchanterAI", true);

        this.enchanterBookRequestBatchSize = builder
                .comment("Server limit on how many Books or Ancient Tomes the Enchanter requests at once.")
                .defineInRange("enchanterBookRequestBatchSize", 8, 1, 64);

        this.enchanterMaxLevelPerBuildingLevel = builder
                .comment("Server limit for the Enchanter's Mana skill cap per building level (buildingLevel * limit).")
                .defineInRange("enchanterMaxLevelPerBuildingLevel", 15, 5, 50);

        this.allowEnchanterAutonomousDrain = builder
                .comment("Allows the Enchanter to gather Mana at their own tower or station when no target worker is configured or within range.")
                .define("allowEnchanterAutonomousDrain", true);

        this.enableConcreteMixerBatching = builder
                .comment("Allows the Concrete Mixer to place and harvest multiple concrete blocks in a single pass based on Secondary Skill.")
                .define("enableConcreteMixerBatching", true);

        this.concreteMixerSkillDivisor = builder
                .comment("Number of Secondary Skill levels required per +1 extra concrete block placed/harvested per action (batch = 1 + skill / divisor).")
                .defineInRange("concreteMixerSkillDivisor", 6, 1, 50);

        this.concreteMixerMaxBatchSize = builder
                .comment("Strict server limit on the maximum number of concrete blocks the Concrete Mixer can process in a single action.")
                .defineInRange("concreteMixerMaxBatchSize", 16, 1, 64);

        builder.pop();
    }
}

