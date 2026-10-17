package com.advancedcolonies.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration settings for shared citizen behaviors:
 * dining hall & eating AI, health/hospital thresholds, leisure time, and QoL interactions.
 */
public final class CitizenCommonConfig {

    // Eating & Restaurant AI
    public final ModConfigSpec.BooleanValue enableEnhancedEatingAI;
    public final ModConfigSpec.BooleanValue enableEnhancedRestaurantCookAI;
    public final ModConfigSpec.BooleanValue preorderRestaurantTable;
    public final ModConfigSpec.BooleanValue serveHungryPlayersInRestaurant;
    public final ModConfigSpec.IntValue restaurantWaitTimeoutSeconds;
    public final ModConfigSpec.DoubleValue minecoloniesFoodMultiplier;
    public final ModConfigSpec.DoubleValue vanillaFoodMultiplier;
    public final ModConfigSpec.BooleanValue enableHutFoodBlacklistModule;
    public final ModConfigSpec.ConfigValue<List<? extends String>> globalFoodBlacklist;

    // Health, Hospital & Regeneration
    public final ModConfigSpec.BooleanValue enableEnhancedHospitalAI;
    public final ModConfigSpec.DoubleValue seekDoctorHealthRatio;
    public final ModConfigSpec.DoubleValue seekDoctorAbsoluteHealth;
    public final ModConfigSpec.BooleanValue enableUnhandledPatientSelfHeal;
    public final ModConfigSpec.IntValue selfHealWaitSeconds;
    public final ModConfigSpec.BooleanValue enableCitizenBeaconEffects;
    public final ModConfigSpec.BooleanValue alertOnCitizenDamage;

    // Leisure & Rest
    public final ModConfigSpec.IntValue defaultLeisureDurationSeconds;
    public final ModConfigSpec.IntValue leisureCooldownSeconds;
    public final ModConfigSpec.IntValue maxWorkIdleCreditSeconds;

    // Recruitment, Resurrection & QoL
    public final ModConfigSpec.BooleanValue enableTavernDirectRecruitment;
    public final ModConfigSpec.BooleanValue enableGraveyardResurrection;
    public final ModConfigSpec.ConfigValue<String> resurrectionItemId;
    public final ModConfigSpec.DoubleValue resurrectionSuccessChance;
    public final ModConfigSpec.BooleanValue enableCompassMindReader;
    public final ModConfigSpec.BooleanValue disablePermissionLevitation;
    public final ModConfigSpec.BooleanValue fixDoorAndGateToggleSlam;
    public final ModConfigSpec.BooleanValue returnGearOnUndertakerResurrect;
    public final ModConfigSpec.BooleanValue enableVisualDebugger;
    public final ModConfigSpec.IntValue visualDebugMaxRange;
    public final ModConfigSpec.IntValue visualDebugSyncIntervalTicks;

    // Guards, University Researcher & Mourning Control
    public final ModConfigSpec.BooleanValue preventGuardSleepingDuringRaids;
    public final ModConfigSpec.BooleanValue disableGuardSleepingOnDuty;
    public final ModConfigSpec.BooleanValue harmlessGuardWakeUp;
    public final ModConfigSpec.BooleanValue honorGuardRetreatWithoutResearch;
    public final ModConfigSpec.BooleanValue enableResearcherSkillScaling;
    public final ModConfigSpec.BooleanValue exemptEssentialWorkersFromMourning;
    public final ModConfigSpec.IntValue maxMourningDurationSeconds;

    public CitizenCommonConfig(final ModConfigSpec.Builder builder) {
        builder.comment("Shared Citizen AI, Eating, Health, Leisure & QoL Settings").push("citizenCommon");

        this.enableEnhancedEatingAI = builder
                .comment("Enables the improved citizen eating AI (work-hut self-feeding, smart food scoring, clean chair dismount).")
                .define("enableEnhancedEatingAI", true);

        this.enableEnhancedRestaurantCookAI = builder
                .comment("Enables the optimized restaurant waiter/cook AI (64-stack gathering, nearest-table serving, mid-round restocking).")
                .define("enableEnhancedRestaurantCookAI", true);

        this.preorderRestaurantTable = builder
                .comment("Citizens reserve a seat at the least crowded nearby restaurant before walking there.")
                .define("preorderRestaurantTable", true);

        this.serveHungryPlayersInRestaurant = builder
                .comment("Waiters also serve hungry players with colony permissions sitting inside the restaurant.")
                .define("serveHungryPlayersInRestaurant", true);

        this.restaurantWaitTimeoutSeconds = builder
                .comment("How many seconds a citizen patiently waits at a restaurant table before re-requesting service.")
                .defineInRange("restaurantWaitTimeoutSeconds", 60, 15, 300);

        this.minecoloniesFoodMultiplier = builder
                .comment("Nutrition/saturation multiplier for MineColonies food items when eaten by citizens.")
                .defineInRange("minecoloniesFoodMultiplier", 1.0D, 0.1D, 10.0D);

        this.vanillaFoodMultiplier = builder
                .comment("Nutrition/saturation multiplier for non-MineColonies food items when eaten by citizens.")
                .defineInRange("vanillaFoodMultiplier", 1.0D, 0.1D, 10.0D);

        this.enableHutFoodBlacklistModule = builder
                .comment("Adds a per-hut food blacklist module to food-producing huts so workers do not eat raw ingredients.")
                .define("enableHutFoodBlacklistModule", true);

        this.globalFoodBlacklist = builder
                .comment("Global list of item registry IDs that citizens are never allowed to eat as regular food.")
                .defineListAllowEmpty("globalFoodBlacklist", List.of(), () -> "minecraft:rotten_flesh", obj -> obj instanceof String);

        this.enableEnhancedHospitalAI = builder
                .comment("Improves healer treatment reliability and citizen hospital-seeking thresholds.")
                .define("enableEnhancedHospitalAI", true);

        this.seekDoctorHealthRatio = builder
                .comment("Citizens seek a hospital when their health drops below this fraction of max health (e.g. 0.25 = 25%).")
                .defineInRange("seekDoctorHealthRatio", 0.20D, 0.05D, 0.90D);

        this.seekDoctorAbsoluteHealth = builder
                .comment("Citizens seek a hospital when their health drops below this absolute HP value.")
                .defineInRange("seekDoctorAbsoluteHealth", 10.0D, 2.0D, 40.0D);

        this.enableUnhandledPatientSelfHeal = builder
                .comment("Allows injured/sick citizens to slowly recover on their own if no healer treats them within selfHealWaitSeconds.")
                .define("enableUnhandledPatientSelfHeal", true);

        this.selfHealWaitSeconds = builder
                .comment("Seconds an untreated patient waits before passive self-healing begins.")
                .defineInRange("selfHealWaitSeconds", 160, 30, 1200);

        this.enableCitizenBeaconEffects = builder
                .comment("Allows nearby vanilla Beacons to grant beneficial status effects to colony citizens.")
                .define("enableCitizenBeaconEffects", true);

        this.alertOnCitizenDamage = builder
                .comment("Sends a notification message when a citizen takes non-player damage.")
                .define("alertOnCitizenDamage", false);

        this.defaultLeisureDurationSeconds = builder
                .comment("Default duration (in seconds) of a citizen's leisure break.")
                .defineInRange("defaultLeisureDurationSeconds", 180, 0, 1200);

        this.leisureCooldownSeconds = builder
                .comment("Average time (in seconds) between citizen leisure breaks.")
                .defineInRange("leisureCooldownSeconds", 1000, 60, 7200);

        this.maxWorkIdleCreditSeconds = builder
                .comment("Maximum idle time at work (in seconds) that can be credited against the next leisure break.")
                .defineInRange("maxWorkIdleCreditSeconds", 180, 0, 1200);

        this.enableTavernDirectRecruitment = builder
                .comment("Adds a recruitment & housing/job assignment tab directly inside the Tavern building window.")
                .define("enableTavernDirectRecruitment", true);

        this.enableGraveyardResurrection = builder
                .comment("Allows players to resurrect fallen citizens at the Graveyard using the configured item.")
                .define("enableGraveyardResurrection", true);

        this.resurrectionItemId = builder
                .comment("Registry ID of the item used to resurrect fallen citizens at the Graveyard.")
                .define("resurrectionItemId", "minecraft:totem_of_undying");

        this.resurrectionSuccessChance = builder
                .comment("Base probability (0.0 to 1.0) of a manual Graveyard resurrection succeeding.")
                .defineInRange("resurrectionSuccessChance", 0.05D, 0.0D, 1.0D);

        this.enableCompassMindReader = builder
                .comment("Right-clicking a citizen with a Compass displays their current AI state and navigation target.")
                .define("enableCompassMindReader", true);

        this.disablePermissionLevitation = builder
                .comment("Prevents MineColonies from applying the Levitation effect to players during permission checks.")
                .define("disablePermissionLevitation", true);

        this.fixDoorAndGateToggleSlam = builder
                .comment("Prevents citizens from slamming already-open doors and fence gates shut when walking through them.")
                .define("fixDoorAndGateToggleSlam", true);

        this.returnGearOnUndertakerResurrect = builder
                .comment("When the Undertaker resurrects a fallen citizen, returns the citizen's grave items directly to their inventory instead of dumping them into the Graveyard rack.")
                .define("returnGearOnUndertakerResurrect", true);

        this.enableVisualDebugger = builder
                .comment("Enables the in-game visual debugger (/acdebug command and Shift+Compass click) for rendering citizen AI actions, stats, and 3D pathfinding.")
                .define("enableVisualDebugger", true);

        this.visualDebugMaxRange = builder
                .comment("Maximum range in blocks around the player for streaming colony-wide visual debug data.")
                .defineInRange("visualDebugMaxRange", 96, 16, 256);

        this.visualDebugSyncIntervalTicks = builder
                .comment("Interval in server ticks between visual debugger data sync packets.")
                .defineInRange("visualDebugSyncIntervalTicks", 5, 1, 40);

        this.preventGuardSleepingDuringRaids = builder
                .comment("Prevents Guards from randomly falling asleep on duty while a colony raid/attack is active.")
                .define("preventGuardSleepingDuringRaids", true);

        this.disableGuardSleepingOnDuty = builder
                .comment("If true, Guards never fall asleep on duty at all.")
                .define("disableGuardSleepingOnDuty", false);

        this.harmlessGuardWakeUp = builder
                .comment("Wakes up sleeping Guards cleanly without dealing real damage or durability loss to their armor.")
                .define("harmlessGuardWakeUp", true);

        this.honorGuardRetreatWithoutResearch = builder
                .comment("Allows Guards to retreat on low health when 'Retreat on low health' is enabled in the Guard Tower GUI, even before the University Retreat research is unlocked.")
                .define("honorGuardRetreatWithoutResearch", true);

        this.enableResearcherSkillScaling = builder
                .comment("Scales the University Researcher's bookshelf study speed dynamically with their Knowledge skill instead of waiting a flat 60 seconds per shelf.")
                .define("enableResearcherSkillScaling", true);

        this.exemptEssentialWorkersFromMourning = builder
                .comment("Exempts essential emergency professions (Healers, Cooks/Bakers, Builders, Couriers) from stopping work to mourn after a citizen death.")
                .define("exemptEssentialWorkersFromMourning", true);

        this.maxMourningDurationSeconds = builder
                .comment("Maximum duration in seconds that mourning citizens spend grieving before returning to work (0 = disable mourning completely, 1200 = full vanilla day).")
                .defineInRange("maxMourningDurationSeconds", 120, 0, 1200);

        builder.pop();
    }
}
