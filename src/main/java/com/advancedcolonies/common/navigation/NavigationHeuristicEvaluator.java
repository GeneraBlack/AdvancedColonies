package com.advancedcolonies.common.navigation;

import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.advancedcolonies.config.PathfindingConfig;
import com.minecolonies.core.entity.pathfinding.MNode;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * Computes 3D target heuristics and end-node scores to prevent citizens from walking into
 * vertical dead-ends (such as underneath bridges, on rooftops, or in basements directly
 * below/above their target) when a slightly longer horizontal path with stairs/ladders exists.
 */
public final class NavigationHeuristicEvaluator {

    private NavigationHeuristicEvaluator() {
        // Utility class
    }

    /**
     * Computes an enhanced Manhattan heuristic that progressively penalizes large vertical
     * offsets once the citizen approaches the target horizontally.
     *
     * @param baseHeuristic the standard 3D Manhattan distance {@code |dx| + |dy| + |dz|}.
     * @param destination   the target destination coordinates.
     * @param x             candidate node X.
     * @param y             candidate node Y.
     * @param z             candidate node Z.
     * @return the adjusted heuristic value.
     */
    public static double adjustHeuristic(
            final double baseHeuristic,
            @NotNull final BlockPos destination,
            final int x,
            final int y,
            final int z) {

        final PathfindingConfig cfg = AdvancedColoniesConfig.PATHFINDING;
        if (!cfg.enableNearTarget3DHeuristic.get()) {
            return baseHeuristic;
        }

        final int horizDist = Math.abs(destination.getX() - x) + Math.abs(destination.getZ() - z);
        final int vertDist = Math.abs(destination.getY() - y);
        final int radius = cfg.nearTargetHorizontalRadius.get();
        final int vertThreshold = cfg.nearTargetVerticalDiffThreshold.get();

        if (horizDist <= radius && vertDist >= vertThreshold) {
            final int excessVert = vertDist - vertThreshold + 1;
            final double proximityRatio = 1.0D - ((double) horizDist / (double) (radius + 1));
            final double verticalPenalty = excessVert * 1.45D * (1.0D + proximityRatio);
            return baseHeuristic + verticalPenalty;
        }

        return baseHeuristic;
    }

    /**
     * Adjusts the final score of a candidate best-effort end node so that nodes on the wrong
     * vertical floor right above/below the destination are strongly disfavored compared to
     * nodes that reach the correct elevation.
     *
     * @param baseEndScore the vanilla end-node score (lower is better).
     * @param destination  the target destination coordinates.
     * @param node         the candidate end node.
     * @return the adjusted end-node score (lower is better).
     */
    public static double adjustEndNodeScore(
            final double baseEndScore,
            @NotNull final BlockPos destination,
            @NotNull final MNode node) {

        final PathfindingConfig cfg = AdvancedColoniesConfig.PATHFINDING;
        if (!cfg.enableNearTarget3DHeuristic.get()) {
            return baseEndScore;
        }

        final int vertDist = Math.abs(destination.getY() - node.y);
        final int vertThreshold = cfg.nearTargetVerticalDiffThreshold.get();
        if (vertDist >= vertThreshold) {
            final int excessVert = vertDist - vertThreshold + 1;
            return baseEndScore + (excessVert * 2.5D);
        }

        return baseEndScore;
    }
}
