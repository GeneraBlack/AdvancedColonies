package com.advancedcolonies.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Central configuration holder for Advanced Colonies.
 * Aggregates all domain-specific configuration sections into a single {@link ModConfigSpec}.
 */
public final class AdvancedColoniesConfig {

    public static final ModConfigSpec SPEC;

    public static final PathfindingConfig PATHFINDING;
    public static final CitizenCommonConfig CITIZEN_COMMON;
    public static final BuildingCapacityConfig BUILDING_CAPACITY;
    public static final LogisticsConfig LOGISTICS;
    public static final WorkerFurnaceAndCraftConfig FURNACE_AND_CRAFT;
    public static final BuilderAndExcavationConfig BUILDER_AND_EXCAVATION;
    public static final AgricultureAndHerdingConfig AGRICULTURE_AND_HERDING;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        PATHFINDING = new PathfindingConfig(builder);
        CITIZEN_COMMON = new CitizenCommonConfig(builder);
        BUILDING_CAPACITY = new BuildingCapacityConfig(builder);
        LOGISTICS = new LogisticsConfig(builder);
        FURNACE_AND_CRAFT = new WorkerFurnaceAndCraftConfig(builder);
        BUILDER_AND_EXCAVATION = new BuilderAndExcavationConfig(builder);
        AGRICULTURE_AND_HERDING = new AgricultureAndHerdingConfig(builder);

        SPEC = builder.build();
    }

    private AdvancedColoniesConfig() {
        // Utility holder class
    }
}
