package com.advancedcolonies.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration settings for Builders, Miners, and Quarriers, including:
 * - Bottom-Up clearing strategy
 * - Worksite safety (lava hazard retreat, support-block fall safety, overhead falling-block safety, alternative stand)
 * - Work range/positioning modes (NORMAL, EXTENDED_REACH, SENTRY, FORMALIST, GOD)
 * - Accepting build orders anywhere and optional mining drop multipliers
 */
public final class BuilderAndExcavationConfig {

    /**
     * Available positioning and range modes for Builders and Miners.
     */
    public enum WorkMode {
        /** Vanilla behavior: walks adjacent to every target block. */
        NORMAL,
        /** Extended reach (Gibbon mode): builds/mines within configured range without climbing onto awkward scaffolding. */
        EXTENDED_REACH,
        /** Stands at a central point on the site and works remotely within the structure bounds. */
        SENTRY,
        /** Begins working immediately upon leaving the hut while moving around the site. */
        FORMALIST,
        /** Works from anywhere without walking to the site. */
        GOD
    }

    // Builder Clearing & Safety Enhancements
    public final ModConfigSpec.BooleanValue enableBottomUpClearingOption;
    public final ModConfigSpec.BooleanValue skipClearingMatchingBlueprintBlocks;
    public final ModConfigSpec.BooleanValue enableBuilderLavaHazardRetreat;
    public final ModConfigSpec.IntValue lavaRetreatDistance;
    public final ModConfigSpec.BooleanValue enableSafeMiningStandSelection;
    public final ModConfigSpec.IntValue maxSafeSupportFallDepth;
    public final ModConfigSpec.BooleanValue protectAgainstOverheadFallingBlocks;

    // Builder & Miner Work Modes
    public final ModConfigSpec.EnumValue<WorkMode> builderWorkMode;
    public final ModConfigSpec.EnumValue<WorkMode> minerWorkMode;
    public final ModConfigSpec.IntValue extendedReachRange;
    public final ModConfigSpec.BooleanValue builderAcceptOrdersAnywhere;

    // Excavation Drop Multiplier
    public final ModConfigSpec.BooleanValue enableMinerDropMultiplier;

    public BuilderAndExcavationConfig(final ModConfigSpec.Builder builder) {
        builder.comment("Builder, Miner & Quarrier Safety, Clearing & Work Mode Settings").push("builderAndExcavation");

        this.enableBottomUpClearingOption = builder
                .comment("Adds the 'Clearing Strategy' (Top to Bottom / Bottom to Top) setting to Builder Huts.")
                .define("enableBottomUpClearingOption", true);

        this.skipClearingMatchingBlueprintBlocks = builder
                .comment("During bottom-up clearing, skips breaking blocks that already match the target blueprint state.")
                .define("skipClearingMatchingBlueprintBlocks", true);

        this.enableBuilderLavaHazardRetreat = builder
                .comment("Builders detect nearby lava at the worksite and retreat to a safe distance instead of burning.")
                .define("enableBuilderLavaHazardRetreat", true);

        this.lavaRetreatDistance = builder
                .comment("Minimum distance (in blocks) a builder retreats when lava is detected near the current work target.")
                .defineInRange("lavaRetreatDistance", 4, 2, 12);

        this.enableSafeMiningStandSelection = builder
                .comment("Builders check for unsafe support blocks below or falling blocks above and step onto a safe adjacent stand position.")
                .define("enableSafeMiningStandSelection", true);

        this.maxSafeSupportFallDepth = builder
                .comment("Maximum drop depth (in blocks) allowed below a support block before the builder refuses to mine it from on top.")
                .defineInRange("maxSafeSupportFallDepth", 2, 1, 8);

        this.protectAgainstOverheadFallingBlocks = builder
                .comment("Prevents builders from mining a block directly above their head if sand/gravel/falling block rests on top of it.")
                .define("protectAgainstOverheadFallingBlocks", true);

        this.builderWorkMode = builder
                .comment("Work positioning mode for Builders (NORMAL, EXTENDED_REACH, SENTRY, FORMALIST, GOD).")
                .defineEnum("builderWorkMode", WorkMode.NORMAL);

        this.minerWorkMode = builder
                .comment("Work positioning mode for Miners and Quarriers (NORMAL, EXTENDED_REACH, SENTRY, FORMALIST, GOD).")
                .defineEnum("minerWorkMode", WorkMode.NORMAL);

        this.extendedReachRange = builder
                .comment("Working range in blocks when EXTENDED_REACH mode is selected.")
                .defineInRange("extendedReachRange", 20, 5, 64);

        this.builderAcceptOrdersAnywhere = builder
                .comment("Allows builders to start the next build/repair order immediately without walking back to their hut first.")
                .define("builderAcceptOrdersAnywhere", true);

        this.enableMinerDropMultiplier = builder
                .comment("Multiplies mined ore/stone drops for Miners and Quarriers based on their building level.")
                .define("enableMinerDropMultiplier", false);

        builder.pop();
    }
}
