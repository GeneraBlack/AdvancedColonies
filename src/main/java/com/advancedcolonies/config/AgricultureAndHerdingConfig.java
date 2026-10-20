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
    public final ModConfigSpec.BooleanValue fixMangroveTreeReplanting;

    // Farmer & Planter
    public final ModConfigSpec.BooleanValue enableEnhancedFarmerAI;
    public final ModConfigSpec.BooleanValue fixFarmerSpiralFieldScan;
    public final ModConfigSpec.BooleanValue fixMinecoloniesCropBlockSurvival;
    public final ModConfigSpec.BooleanValue enableFarmerRightClickHarvest;
    public final ModConfigSpec.IntValue farmerSkillHarvestBonusStep;
    public final ModConfigSpec.IntValue farmerMaxHarvestMultiplier;
    public final ModConfigSpec.BooleanValue enableFarmFieldCropRotation;
    public final ModConfigSpec.IntValue cropRotationMaxSeasons;
    public final ModConfigSpec.IntValue cropRotationMaxSeasonDays;
    public final ModConfigSpec.BooleanValue extendSoftShoesToAllCitizens;
    public final ModConfigSpec.BooleanValue alwaysProtectColonyFarmland;
    public final ModConfigSpec.BooleanValue enableEnhancedPlanterAI;

    // Herders, Cowboy, Beekeeper & Butcher
    public final ModConfigSpec.BooleanValue enableEnhancedHerderAI;
    public final ModConfigSpec.BooleanValue exponentialHerderAnimalCap;
    public final ModConfigSpec.BooleanValue butcherInstantKill;
    public final ModConfigSpec.BooleanValue enableCowboyMultiMilking;
    public final ModConfigSpec.IntValue cowboyMilkingSkillDivisor;
    public final ModConfigSpec.IntValue cowboyMaxMilkPerVisit;
    public final ModConfigSpec.BooleanValue disableLargeBottleCooldown;
    public final ModConfigSpec.BooleanValue enableBeekeeperHarvestPriority;
    public final ModConfigSpec.BooleanValue enableEnhancedBeeAI;
    public final ModConfigSpec.IntValue beeMaxHiveWanderRadius;
    public final ModConfigSpec.IntValue beeFlowerSearchRadius;
    public final ModConfigSpec.IntValue beePollinationDurationTicks;
    public final ModConfigSpec.BooleanValue beeProtectFromHazardsAndStingDeath;

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

        this.fixMangroveTreeReplanting = builder
                .comment("Fixes Mangrove tree replanting by scanning downward from the trunk for valid Dirt/Mud beneath air or water so Mangrove Propagules are planted on solid ground.")
                .define("fixMangroveTreeReplanting", true);

        this.enableEnhancedFarmerAI = builder
                .comment("Enables streamlined harvesting, tilling, and planting loops for Farmers.")
                .define("enableEnhancedFarmerAI", true);

        this.fixFarmerSpiralFieldScan = builder
                .comment("Uses a safe, division-by-zero-free spiral coordinate scan (nextValidCell) across symmetric and asymmetric FarmFields.")
                .define("fixFarmerSpiralFieldScan", true);

        this.fixMinecoloniesCropBlockSurvival = builder
                .comment("Prevents MinecoloniesCropBlock#updateShape from prematurely breaking crops planted on valid MinecoloniesFarmland.")
                .define("fixMinecoloniesCropBlockSurvival", true);

        this.enableFarmerRightClickHarvest = builder
                .comment("Allows Farmers to harvest compatible crops/bushes via right-click interaction before falling back to block breaking.")
                .define("enableFarmerRightClickHarvest", true);

        this.farmerSkillHarvestBonusStep = builder
                .comment("Number of Farmer Primary Skill levels required per +1 bonus crop drop multiplier (0 = disable skill-based drop multiplier).")
                .defineInRange("farmerSkillHarvestBonusStep", 25, 0, 100);

        this.farmerMaxHarvestMultiplier = builder
                .comment("Strict server limit on the maximum total crop drop multiplier a Farmer can reach from skill and research.")
                .defineInRange("farmerMaxHarvestMultiplier", 4, 1, 16);

        this.enableFarmFieldCropRotation = builder
                .comment("Enables multi-season crop rotation support on FarmFields (persisted in NBT and synced to clients).")
                .define("enableFarmFieldCropRotation", true);

        this.cropRotationMaxSeasons = builder
                .comment("Strict server limit on the maximum number of crop rotation seasons allowed per FarmField.")
                .defineInRange("cropRotationMaxSeasons", 4, 1, 8);

        this.cropRotationMaxSeasonDays = builder
                .comment("Strict server limit on the maximum duration (in Minecraft days) allowed for a single crop rotation season.")
                .defineInRange("cropRotationMaxSeasonDays", 30, 1, 365);

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

        this.enableCowboyMultiMilking = builder
                .comment("Allows Cowboys to milk Cows/Goats and stew Mooshrooms multiple times per visit based on Primary Skill, plus FakePlayer compatibility for modded stews.")
                .define("enableCowboyMultiMilking", true);

        this.cowboyMilkingSkillDivisor = builder
                .comment("Number of Cowboy Primary Skill levels required per +1 extra milking/stewing action per visit (actions = 1 + skill / divisor).")
                .defineInRange("cowboyMilkingSkillDivisor", 10, 1, 50);

        this.cowboyMaxMilkPerVisit = builder
                .comment("Strict server limit on the maximum number of milking or stewing actions a Cowboy can perform in a single visit.")
                .defineInRange("cowboyMaxMilkPerVisit", 6, 1, 32);

        this.disableLargeBottleCooldown = builder
                .comment("Removes the item use cooldown when milking animals or filling water with MineColonies' Large Empty Bottle.")
                .define("disableLargeBottleCooldown", true);

        this.enableBeekeeperHarvestPriority = builder
                .comment("Prioritizes harvesting honey/honeycombs from full beehives before breeding bees, and tags nearby bees as colony-penned.")
                .define("enableBeekeeperHarvestPriority", true);

        this.enableEnhancedBeeAI = builder
                .comment("Overhauls Minecraft's Bee AI: prevents bees from drifting away or forgetting their hive, fixes hive-entry deadzones and roof/corner getting-stuck bugs, speeds up flower discovery and pollination, and protects bees from water/campfire hazards.")
                .define("enableEnhancedBeeAI", true);

        this.beeMaxHiveWanderRadius = builder
                .comment("Maximum radius in blocks a bee with a home hive is allowed to wander from its hive before being guided back (kept within MineColonies' 22-block Beekeeper detection range).")
                .defineInRange("beeMaxHiveWanderRadius", 16, 6, 32);

        this.beeFlowerSearchRadius = builder
                .comment("Radius in blocks around a bee and its hive to scan for flowers when looking for nectar (vanilla default is only 5 blocks).")
                .defineInRange("beeFlowerSearchRadius", 16, 5, 32);

        this.beePollinationDurationTicks = builder
                .comment("Number of ticks a bee needs to pollinate a flower before receiving nectar and returning to its hive (vanilla default is 400+ ticks).")
                .defineInRange("beePollinationDurationTicks", 160, 40, 400);

        this.beeProtectFromHazardsAndStingDeath = builder
                .comment("Protects bees from drowning in water, burning on campfires under beehives, berry bush/cactus damage, and stinging MineColonies citizens/Beekeepers to death.")
                .define("beeProtectFromHazardsAndStingDeath", true);

        this.enableEnhancedNetherWorkerAI = builder
                .comment("Enables the improved Nether Miner expedition logic and expedition log tracking.")
                .define("enableEnhancedNetherWorkerAI", true);

        builder.pop();
    }
}

