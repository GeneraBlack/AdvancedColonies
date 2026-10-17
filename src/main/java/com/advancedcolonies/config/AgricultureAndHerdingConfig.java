package com.advancedcolonies.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration settings for Forestry (Lumberjack), Agriculture (Farmer, Planter),
 * Herding (Animal Herders, Butcher), and Nether Workers.
 */
public final class AgricultureAndHerdingConfig {

    // Lumberjack / Forestry
    public final ModConfigSpec.BooleanValue teleportUnreachableTreeDrops;
    public final ModConfigSpec.BooleanValue lumberjackWorkBeforeHutBuilt;
    public final ModConfigSpec.BooleanValue lumberjackReplantWithoutSaplings;

    // Farmer & Planter
    public final ModConfigSpec.BooleanValue enableEnhancedFarmerAI;
    public final ModConfigSpec.BooleanValue extendSoftShoesToAllCitizens;
    public final ModConfigSpec.BooleanValue alwaysProtectColonyFarmland;
    public final ModConfigSpec.BooleanValue enableEnhancedPlanterAI;

    // Herders & Butcher
    public final ModConfigSpec.BooleanValue enableEnhancedHerderAI;
    public final ModConfigSpec.BooleanValue exponentialHerderAnimalCap;
    public final ModConfigSpec.BooleanValue butcherInstantKill;

    // Nether Worker
    public final ModConfigSpec.BooleanValue enableEnhancedNetherWorkerAI;

    public AgricultureAndHerdingConfig(final ModConfigSpec.Builder builder) {
        builder.comment("Forestry, Farming, Plantation, Herding & Nether Worker Settings").push("agricultureAndHerding");

        this.teleportUnreachableTreeDrops = builder
                .comment("Teleports unreachable treetop drops directly to the Lumberjack instead of breaking every leaf block.")
                .define("teleportUnreachableTreeDrops", true);

        this.lumberjackWorkBeforeHutBuilt = builder
                .comment("Allows Lumberjacks to chop trees even before their hut building is constructed.")
                .define("lumberjackWorkBeforeHutBuilt", false);

        this.lumberjackReplantWithoutSaplings = builder
                .comment("Allows Lumberjacks to replant trees even when they have no saplings in their inventory.")
                .define("lumberjackReplantWithoutSaplings", false);

        this.enableEnhancedFarmerAI = builder
                .comment("Enables streamlined harvesting, tilling, and planting loops for Farmers.")
                .define("enableEnhancedFarmerAI", true);

        this.extendSoftShoesToAllCitizens = builder
                .comment("When the 'Soft Shoes' University research is unlocked, extends farmland trample protection to ALL colony citizens (Guards, Couriers, Builders, Visitors) instead of only the Farmer.")
                .define("extendSoftShoesToAllCitizens", true);

        this.alwaysProtectColonyFarmland = builder
                .comment("If true, prevents colony citizens from trampling farmland even before the 'Soft Shoes' research is unlocked.")
                .define("alwaysProtectColonyFarmland", false);

        this.enableEnhancedPlanterAI = builder
                .comment("Enables streamlined plantation field collection and accelerated growth handling for Planters.")
                .define("enableEnhancedPlanterAI", true);

        this.enableEnhancedHerderAI = builder
                .comment("Improves breeding, feeding, and animal management for Herders.")
                .define("enableEnhancedHerderAI", true);

        this.exponentialHerderAnimalCap = builder
                .comment("Scales herder hut max animals exponentially (2^level) instead of linearly (2*level).")
                .define("exponentialHerderAnimalCap", false);

        this.butcherInstantKill = builder
                .comment("Allows Butchers to butcher target animals in a single strike instead of chasing them.")
                .define("butcherInstantKill", true);

        this.enableEnhancedNetherWorkerAI = builder
                .comment("Enables the improved Nether Miner expedition logic and expedition log tracking.")
                .define("enableEnhancedNetherWorkerAI", true);

        builder.pop();
    }
}
