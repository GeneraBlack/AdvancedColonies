package com.advancedcolonies.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration settings for citizen pathfinding, road/structure preference,
 * vertical dead-end avoidance, ladder/water rescue, and unstuck behavior.
 */
public final class PathfindingConfig {

    public final ModConfigSpec.BooleanValue preferRoadsAndStructures;
    public final ModConfigSpec.BooleanValue preferClimbablesAndRails;
    public final ModConfigSpec.BooleanValue enableNearTarget3DHeuristic;
    public final ModConfigSpec.IntValue nearTargetHorizontalRadius;
    public final ModConfigSpec.IntValue nearTargetVerticalDiffThreshold;

    public final ModConfigSpec.DoubleValue roadCostMultiplier;
    public final ModConfigSpec.DoubleValue structurePathCostBonus;
    public final ModConfigSpec.DoubleValue waterEnterCost;
    public final ModConfigSpec.DoubleValue diveCost;
    public final ModConfigSpec.DoubleValue roofAndLeafPenalty;
    public final ModConfigSpec.DoubleValue farmlandJumpPenalty;
    public final ModConfigSpec.DoubleValue berryBushPenalty;

    public final ModConfigSpec.BooleanValue enableLadderSlipProtection;
    public final ModConfigSpec.BooleanValue enableShallowWaterRescue;
    public final ModConfigSpec.BooleanValue allowFenceGatePassage;
    public final ModConfigSpec.BooleanValue disableStuckTeleport;
    public final ModConfigSpec.DoubleValue validPathStuckPatienceMultiplier;
    public final ModConfigSpec.IntValue maxPathfindingRange;
    public final ModConfigSpec.IntValue nodeExpansionCount;

    public PathfindingConfig(final ModConfigSpec.Builder builder) {
        builder.comment("Pathfinding, Navigation & Unstuck Settings").push("pathfinding");

        this.preferRoadsAndStructures = builder
                .comment("Whether citizens strongly prefer built roads, stairs, slabs, and walkable building interiors.")
                .define("preferRoadsAndStructures", true);

        this.preferClimbablesAndRails = builder
                .comment("Whether citizens prefer ladders, vines, and minecart rails when navigating.")
                .define("preferClimbablesAndRails", true);

        this.enableNearTarget3DHeuristic = builder
                .comment("Prevents citizens from walking into vertical dead-ends (e.g. under bridges or in basements) when horizontally close to their target.")
                .define("enableNearTarget3DHeuristic", true);

        this.nearTargetHorizontalRadius = builder
                .comment("Horizontal Manhattan distance within which vertical differences to the target are penalized more strongly.")
                .defineInRange("nearTargetHorizontalRadius", 20, 4, 64);

        this.nearTargetVerticalDiffThreshold = builder
                .comment("Minimum vertical distance (in blocks) near the target before the 3D vertical heuristic penalty activates.")
                .defineInRange("nearTargetVerticalDiffThreshold", 3, 1, 32);

        this.roadCostMultiplier = builder
                .comment("Cost multiplier applied when walking on recognized path/road blocks (lower = stronger preference).")
                .defineInRange("roadCostMultiplier", 0.35D, 0.05D, 1.0D);

        this.structurePathCostBonus = builder
                .comment("Multiplicative cost factor when walking inside valid building footprints or player-built stairs/slabs.")
                .defineInRange("structurePathCostBonus", 0.55D, 0.1D, 1.0D);

        this.waterEnterCost = builder
                .comment("Additional pathing cost for stepping into water.")
                .defineInRange("waterEnterCost", 8.0D, 1.0D, 50.0D);

        this.diveCost = builder
                .comment("Additional pathing cost for diving in deep water (prevents drowning shortcuts).")
                .defineInRange("diveCost", 24.0D, 2.0D, 100.0D);

        this.roofAndLeafPenalty = builder
                .comment("Pathing penalty for walking across roof shingles or tree leaves.")
                .defineInRange("roofAndLeafPenalty", 4.0D, 0.0D, 50.0D);

        this.farmlandJumpPenalty = builder
                .comment("Pathing penalty to prevent citizens from jumping on and trampling farmland.")
                .defineInRange("farmlandJumpPenalty", 6.0D, 0.0D, 50.0D);

        this.berryBushPenalty = builder
                .comment("Pathing penalty to avoid walking through damaging sweet berry bushes.")
                .defineInRange("berryBushPenalty", 15.0D, 0.0D, 100.0D);

        this.enableLadderSlipProtection = builder
                .comment("Prevents citizens from slipping off ladders during diagonal transitions.")
                .define("enableLadderSlipProtection", true);

        this.enableShallowWaterRescue = builder
                .comment("Helps citizens escape 1-block water pockets under solid ceilings before drowning.")
                .define("enableShallowWaterRescue", true);

        this.allowFenceGatePassage = builder
                .comment("Allows citizens to smoothly open and pass through fence gates like regular doors.")
                .define("allowFenceGatePassage", true);

        this.disableStuckTeleport = builder
                .comment("If true, completely disables emergency stuck-teleportation for citizens.")
                .define("disableStuckTeleport", false);

        this.validPathStuckPatienceMultiplier = builder
                .comment("Multiplies the time a citizen waits on an active, valid path before triggering unstuck teleportation.")
                .defineInRange("validPathStuckPatienceMultiplier", 2.5D, 1.0D, 10.0D);

        this.maxPathfindingRange = builder
                .comment("Maximum pathfinding search range in blocks for large colonies.")
                .defineInRange("maxPathfindingRange", 1000, 256, 4096);

        this.nodeExpansionCount = builder
                .comment("Number of candidate nodes evaluated per A* expansion step.")
                .defineInRange("nodeExpansionCount", 4, 1, 16);

        builder.pop();
    }
}
