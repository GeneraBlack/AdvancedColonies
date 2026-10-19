package com.advancedcolonies.common.debug;

import com.advancedcolonies.AdvancedColonies;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registers the {@code /acdebug} (and {@code /advancedcolonies debug}) Brigadier chat command tree
 * for enabling and disabling the in-game visual debugger, filtering by job or stuck status,
 * inspecting detailed citizen diagnostics, teleporting to citizens, and executing immediate
 * unstuck/AI recovery actions.
 */
@EventBusSubscriber(modid = AdvancedColonies.MOD_ID)
public final class VisualDebugCommand {

    private VisualDebugCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(@NotNull final RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(buildCommandTree("acdebug"));
        dispatcher.register(Commands.literal("advancedcolonies").then(buildCommandTree("debug")));
    }

    @NotNull
    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandTree(@NotNull final String rootLiteral) {
        return Commands.literal(rootLiteral)
                // /acdebug colony [on|off|toggle] OR /acdebug colony <colonyId> [on|off|toggle]
                .then(Commands.literal("colony")
                        .executes(ctx -> executeColonyHere(ctx, null))
                        .then(Commands.literal("on").executes(ctx -> executeColonyHere(ctx, Boolean.TRUE)))
                        .then(Commands.literal("off").executes(ctx -> executeColonyHere(ctx, Boolean.FALSE)))
                        .then(Commands.literal("toggle").executes(ctx -> executeColonyHere(ctx, null)))
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeColonyById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "colonyId"),
                                        null))
                                .then(Commands.literal("on").executes(ctx -> executeColonyById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "colonyId"),
                                        Boolean.TRUE)))
                                .then(Commands.literal("off").executes(ctx -> executeColonyById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "colonyId"),
                                        Boolean.FALSE)))
                                .then(Commands.literal("toggle").executes(ctx -> executeColonyById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "colonyId"),
                                        null)))))
                // /acdebug citizen <citizenId> [on|off|toggle]
                .then(Commands.literal("citizen")
                        .then(Commands.argument("citizenId", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeCitizenById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "citizenId"),
                                        null))
                                .then(Commands.literal("on").executes(ctx -> executeCitizenById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "citizenId"),
                                        Boolean.TRUE)))
                                .then(Commands.literal("off").executes(ctx -> executeCitizenById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "citizenId"),
                                        Boolean.FALSE)))
                                .then(Commands.literal("toggle").executes(ctx -> executeCitizenById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "citizenId"),
                                        null)))))
                // /acdebug name <citizenName>
                .then(Commands.literal("name")
                        .then(Commands.argument("citizenName", StringArgumentType.greedyString())
                                .executes(ctx -> executeCitizenByName(
                                        ctx,
                                        StringArgumentType.getString(ctx, "citizenName")))))
                // /acdebug look [on|off|toggle]
                .then(Commands.literal("look")
                        .executes(ctx -> executeCitizenLook(ctx, null))
                        .then(Commands.literal("on").executes(ctx -> executeCitizenLook(ctx, Boolean.TRUE)))
                        .then(Commands.literal("off").executes(ctx -> executeCitizenLook(ctx, Boolean.FALSE)))
                        .then(Commands.literal("toggle").executes(ctx -> executeCitizenLook(ctx, null))))
                // /acdebug job <jobFilter> (e.g. /acdebug job builder, /acdebug job all)
                .then(Commands.literal("job")
                        .then(Commands.argument("jobFilter", StringArgumentType.greedyString())
                                .executes(ctx -> executeJobFilter(
                                        ctx,
                                        StringArgumentType.getString(ctx, "jobFilter")))))
                // /acdebug stuck (filters HUD to stuck citizens & lists them with clickable [Unstuck] / [TP] / [Inspect])
                .then(Commands.literal("stuck")
                        .executes(VisualDebugCommand::executeStuckScan))
                // /acdebug unstuck [<citizenId>|look|all]
                .then(Commands.literal("unstuck")
                        .executes(VisualDebugCommand::executeUnstuckLookOrAllStuck)
                        .then(Commands.literal("look").executes(VisualDebugCommand::executeUnstuckLook))
                        .then(Commands.literal("all").executes(VisualDebugCommand::executeUnstuckAll))
                        .then(Commands.argument("citizenId", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeUnstuckById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "citizenId")))))
                // /acdebug inspect [<citizenId>|look]
                .then(Commands.literal("inspect")
                        .executes(VisualDebugCommand::executeInspectLook)
                        .then(Commands.literal("look").executes(VisualDebugCommand::executeInspectLook))
                        .then(Commands.argument("citizenId", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeInspectById(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "citizenId")))))
                // /acdebug tp <citizenId>
                .then(Commands.literal("tp")
                        .then(Commands.argument("citizenId", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeTeleportToCitizen(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "citizenId")))))
                // /acdebug list [<jobFilter>]
                .then(Commands.literal("list")
                        .executes(ctx -> executeListCitizens(ctx, null))
                        .then(Commands.argument("jobFilter", StringArgumentType.greedyString())
                                .executes(ctx -> executeListCitizens(
                                        ctx,
                                        StringArgumentType.getString(ctx, "jobFilter")))))
                // /acdebug layer <actions|stats|path|all> [on|off|toggle]
                .then(Commands.literal("layer")
                        .then(Commands.argument("layerName", StringArgumentType.word())
                                .executes(ctx -> executeLayerToggle(
                                        ctx,
                                        StringArgumentType.getString(ctx, "layerName"),
                                        null))
                                .then(Commands.literal("on").executes(ctx -> executeLayerToggle(
                                        ctx,
                                        StringArgumentType.getString(ctx, "layerName"),
                                        Boolean.TRUE)))
                                .then(Commands.literal("off").executes(ctx -> executeLayerToggle(
                                        ctx,
                                        StringArgumentType.getString(ctx, "layerName"),
                                        Boolean.FALSE)))
                                .then(Commands.literal("toggle").executes(ctx -> executeLayerToggle(
                                        ctx,
                                        StringArgumentType.getString(ctx, "layerName"),
                                        null)))))
                // /acdebug status
                .then(Commands.literal("status")
                        .executes(VisualDebugCommand::executeStatus))
                // /acdebug off | /acdebug clear
                .then(Commands.literal("off")
                        .executes(VisualDebugCommand::executeClearAll))
                .then(Commands.literal("clear")
                        .executes(VisualDebugCommand::executeClearAll));
    }

    private static int executeColonyHere(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            @Nullable final Boolean enable) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || !checkDebuggerEnabled(player)) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return applyColonyDebug(player, colony, enable);
    }

    private static int executeColonyById(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            final int colonyId,
            @Nullable final Boolean enable) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || !checkDebuggerEnabled(player)) {
            return 0;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.level().dimension());
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return applyColonyDebug(player, colony, enable);
    }

    private static int applyColonyDebug(
            @NotNull final ServerPlayer player,
            @NotNull final IColony colony,
            @Nullable final Boolean enable) {
        final boolean nowEnabled = ServerDebugSessionManager.setColonyDebug(player, colony.getID(), enable);
        player.sendSystemMessage(Component.translatable(
                nowEnabled
                        ? "advancedcolonies.debug.colony_enabled"
                        : "advancedcolonies.debug.colony_disabled",
                colony.getName(),
                colony.getID()).withStyle(nowEnabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeCitizenById(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            final int citizenId,
            @Nullable final Boolean enable) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || !checkDebuggerEnabled(player)) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
        if (citizenData == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.citizen_not_found", citizenId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final boolean nowEnabled = ServerDebugSessionManager.setCitizenDebug(
                player,
                colony.getID(),
                citizenData.getId(),
                enable);
        player.sendSystemMessage(Component.translatable(
                nowEnabled
                        ? "advancedcolonies.debug.citizen_enabled"
                        : "advancedcolonies.debug.citizen_disabled",
                citizenData.getName(),
                citizenData.getId()).withStyle(nowEnabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeCitizenByName(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            @NotNull final String query) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || !checkDebuggerEnabled(player)) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final String lowerQuery = query.trim().toLowerCase(Locale.ROOT);
        ICitizenData matched = null;
        for (final ICitizenData candidate : colony.getCitizenManager().getCitizens()) {
            if (candidate.getName().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                matched = candidate;
                break;
            }
        }
        if (matched == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.citizen_name_not_found", query)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final boolean nowEnabled = ServerDebugSessionManager.setCitizenDebug(
                player,
                colony.getID(),
                matched.getId(),
                null);
        player.sendSystemMessage(Component.translatable(
                nowEnabled
                        ? "advancedcolonies.debug.citizen_enabled"
                        : "advancedcolonies.debug.citizen_disabled",
                matched.getName(),
                matched.getId()).withStyle(nowEnabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeCitizenLook(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            @Nullable final Boolean enable) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || !checkDebuggerEnabled(player)) {
            return 0;
        }
        final EntityCitizen targetCitizen = raycastCitizen(player, 32.0D);
        if (targetCitizen == null || targetCitizen.getCitizenData() == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_citizen_looked_at")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final ICitizenData data = targetCitizen.getCitizenData();
        final boolean nowEnabled = ServerDebugSessionManager.setCitizenDebug(
                player,
                data.getColony().getID(),
                data.getId(),
                enable);
        player.sendSystemMessage(Component.translatable(
                nowEnabled
                        ? "advancedcolonies.debug.citizen_enabled"
                        : "advancedcolonies.debug.citizen_disabled",
                data.getName(),
                data.getId()).withStyle(nowEnabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeJobFilter(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            @NotNull final String jobFilter) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || !checkDebuggerEnabled(player)) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerDebugSessionManager.setColonyJobFilter(player, colony.getID(), jobFilter);
        player.sendSystemMessage(Component.literal(String.format(
                "[AC Debug] Colony '%s' (#%d) visual debugger filtered to job: '%s'",
                colony.getName(),
                colony.getID(),
                jobFilter)).withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeStuckScan(@NotNull final CommandContext<CommandSourceStack> ctx) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || !checkDebuggerEnabled(player)) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerDebugSessionManager.setColonyStuckOnlyFilter(player, colony.getID(), true);

        final List<CitizenDebugSnapshot> stuckCitizens = new ArrayList<>();
        for (final ICitizenData data : colony.getCitizenManager().getCitizens()) {
            final Optional<AbstractEntityCitizen> entityOpt = data.getEntity();
            if (entityOpt.isPresent() && entityOpt.get() instanceof EntityCitizen citizen) {
                final CitizenDebugSnapshot snap = ServerDebugSessionManager.buildSnapshot(colony.getID(), data, citizen);
                if (snap.isGenuinelyStuck()) {
                    stuckCitizens.add(snap);
                }
            }
        }

        if (stuckCitizens.isEmpty()) {
            player.sendSystemMessage(Component.literal(String.format(
                    "[AC Debug] No stuck citizens detected in '%s' (#%d)! (Stuck-only filter active)",
                    colony.getName(),
                    colony.getID())).withStyle(ChatFormatting.GREEN));
            return Command.SINGLE_SUCCESS;
        }

        player.sendSystemMessage(Component.literal(String.format(
                "[AC Debug] Found %d genuinely stuck/stalled citizen(s) in '%s':",
                stuckCitizens.size(),
                colony.getName())).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        for (final CitizenDebugSnapshot snap : stuckCitizens) {
            final MutableComponent line = Component.literal(String.format(
                    " • [#%d] %s (%s) — Stalled %.1fs (Lv.%d) | AI: %s ",
                    snap.citizenId(),
                    snap.citizenName(),
                    snap.jobName(),
                    snap.realStalledTicks() / 20.0D,
                    snap.stuckLevel(),
                    snap.workerAiState())).withStyle(ChatFormatting.YELLOW);
            line.append(buildActionLinks(snap.citizenId()));
            player.sendSystemMessage(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeUnstuckLookOrAllStuck(@NotNull final CommandContext<CommandSourceStack> ctx) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final EntityCitizen lookedAt = raycastCitizen(player, 32.0D);
        if (lookedAt != null && lookedAt.getCitizenData() != null) {
            final boolean rescued = ServerDebugSessionManager.forceUnstuckCitizenAndAI(lookedAt);
            player.sendSystemMessage(Component.literal(String.format(
                    "[AC Debug] Executed unstuck & AI reset on [#%d] %s (moved: %s).",
                    lookedAt.getCitizenData().getId(),
                    lookedAt.getCitizenData().getName(),
                    rescued)).withStyle(ChatFormatting.GREEN));
            return Command.SINGLE_SUCCESS;
        }
        return executeUnstuckAll(ctx);
    }

    private static int executeUnstuckLook(@NotNull final CommandContext<CommandSourceStack> ctx) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final EntityCitizen lookedAt = raycastCitizen(player, 32.0D);
        if (lookedAt == null || lookedAt.getCitizenData() == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_citizen_looked_at")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final boolean rescued = ServerDebugSessionManager.forceUnstuckCitizenAndAI(lookedAt);
        player.sendSystemMessage(Component.literal(String.format(
                "[AC Debug] Executed unstuck & AI reset on [#%d] %s (moved: %s).",
                lookedAt.getCitizenData().getId(),
                lookedAt.getCitizenData().getName(),
                rescued)).withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeUnstuckById(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            final int citizenId) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final ICitizenData data = colony.getCitizenManager().getCivilian(citizenId);
        if (data == null || data.getEntity().isEmpty()) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.citizen_not_found", citizenId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final AbstractEntityCitizen citizen = data.getEntity().get();
        final boolean rescued = ServerDebugSessionManager.forceUnstuckCitizenAndAI(citizen);
        player.sendSystemMessage(Component.literal(String.format(
                "[AC Debug] Executed unstuck & AI reset on [#%d] %s (moved: %s).",
                data.getId(),
                data.getName(),
                rescued)).withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeUnstuckAll(@NotNull final CommandContext<CommandSourceStack> ctx) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        int rescuedCount = 0;
        for (final ICitizenData data : colony.getCitizenManager().getCitizens()) {
            final Optional<AbstractEntityCitizen> entityOpt = data.getEntity();
            if (entityOpt.isPresent() && entityOpt.get() instanceof EntityCitizen citizen) {
                final CitizenDebugSnapshot snap = ServerDebugSessionManager.buildSnapshot(colony.getID(), data, citizen);
                if (snap.isGenuinelyStuck() || snap.realStalledTicks() >= 40 || !snap.builderStageInfo().isEmpty()) {
                    ServerDebugSessionManager.forceUnstuckCitizenAndAI(citizen);
                    rescuedCount++;
                }
            }
        }
        player.sendSystemMessage(Component.literal(String.format(
                "[AC Debug] Executed unstuck & Builder AI reset on %d stalled/active citizen(s) in '%s'.",
                rescuedCount,
                colony.getName())).withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeInspectLook(@NotNull final CommandContext<CommandSourceStack> ctx) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final EntityCitizen targetCitizen = raycastCitizen(player, 32.0D);
        if (targetCitizen == null || targetCitizen.getCitizenData() == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_citizen_looked_at")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final ICitizenData data = targetCitizen.getCitizenData();
        sendDetailedInspectionReport(player, data.getColony(), data, targetCitizen);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeInspectById(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            final int citizenId) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final ICitizenData data = colony.getCitizenManager().getCivilian(citizenId);
        if (data == null || data.getEntity().isEmpty() || !(data.getEntity().get() instanceof EntityCitizen citizen)) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.citizen_not_found", citizenId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        sendDetailedInspectionReport(player, colony, data, citizen);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendDetailedInspectionReport(
            @NotNull final ServerPlayer player,
            @NotNull final IColony colony,
            @NotNull final ICitizenData data,
            @NotNull final EntityCitizen citizen) {
        final CitizenDebugSnapshot snap = ServerDebugSessionManager.buildSnapshot(colony.getID(), data, citizen);
        final BlockPos pos = citizen.blockPosition();

        final MutableComponent header = Component.literal(String.format(
                "═══ [AC Inspector] [#%d] %s (%s) ═══ ",
                snap.citizenId(),
                snap.citizenName(),
                snap.jobName())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        header.append(buildActionLinks(snap.citizenId()));
        player.sendSystemMessage(header);

        player.sendSystemMessage(Component.literal(String.format(
                " • Pos: (%d, %d, %d) | Work Building: %s%s",
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                snap.workBuildingInfo(),
                snap.workBuildingPos() != null
                        ? String.format(" @ (%d, %d, %d)",
                                snap.workBuildingPos().getX(),
                                snap.workBuildingPos().getY(),
                                snap.workBuildingPos().getZ())
                        : "")).withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.literal(String.format(
                " • AI State: Citizen=%s -> Worker=%s",
                snap.citizenAiState(),
                snap.workerAiState())).withStyle(ChatFormatting.AQUA));

        if (!snap.builderStageInfo().isEmpty()) {
            player.sendSystemMessage(Component.literal(String.format(
                    " • Builder Progress: %s | Block Target: %s | WorkFrom Stand: %s",
                    snap.builderStageInfo(),
                    formatPos(snap.workTargetPos()),
                    formatPos(snap.workStandPos()))).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        if (!snap.activeRequestSummary().isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    " • Active Alert/Request: " + snap.activeRequestSummary())
                    .withStyle(ChatFormatting.RED));
        }

        player.sendSystemMessage(Component.literal(String.format(
                " • Vitals & Inv: HP %.1f/%.1f | Sat %.1f | Hap %.1f | Hand: %s | Inv: %d/%d slots",
                snap.health(),
                snap.maxHealth(),
                snap.saturation(),
                snap.happiness(),
                snap.heldItemName(),
                snap.inventoryUsedSlots(),
                snap.inventoryTotalSlots())).withStyle(ChatFormatting.GREEN));

        final ChatFormatting navColor = snap.isGenuinelyStuck() ? ChatFormatting.RED : ChatFormatting.YELLOW;
        player.sendSystemMessage(Component.literal(String.format(
                " • Navigation: Status=%s [%d/%d nodes] | Target=%s (%.1fm) | Speed=%.2f b/s | Stalled=%.1fs (StuckLv=%d, Timeout=%dt) | LastRescue=%s",
                snap.pathStatus(),
                Math.min(snap.currentNodeIndex() + 1, snap.pathNodes().size()),
                snap.pathNodes().size(),
                formatPos(snap.targetPos()),
                snap.distanceToTarget(),
                snap.moveSpeedBps(),
                snap.realStalledTicks() / 20.0D,
                snap.stuckLevel(),
                snap.globalTimeoutTicks(),
                snap.rescueAction())).withStyle(navColor));
    }

    private static int executeTeleportToCitizen(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            final int citizenId) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final ICitizenData data = colony.getCitizenManager().getCivilian(citizenId);
        if (data == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.citizen_not_found", citizenId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final Optional<AbstractEntityCitizen> entityOpt = data.getEntity();
        if (entityOpt.isPresent()) {
            final AbstractEntityCitizen citizen = entityOpt.get();
            player.teleportTo(citizen.getX(), citizen.getY(), citizen.getZ());
            player.sendSystemMessage(Component.literal(String.format(
                    "[AC Debug] Teleported to [#%d] %s at (%.1f, %.1f, %.1f).",
                    data.getId(),
                    data.getName(),
                    citizen.getX(),
                    citizen.getY(),
                    citizen.getZ())).withStyle(ChatFormatting.GREEN));
            return Command.SINGLE_SUCCESS;
        }
        player.sendSystemMessage(Component.literal(String.format(
                "[AC Debug] Citizen [#%d] %s is not currently loaded in the world.",
                data.getId(),
                data.getName())).withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int executeListCitizens(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            @Nullable final String jobFilter) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final IColony colony = resolvePlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.no_colony_found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        final String filter = jobFilter == null ? "" : jobFilter.trim().toLowerCase(Locale.ROOT);
        final List<CitizenDebugSnapshot> matches = new ArrayList<>();
        for (final ICitizenData data : colony.getCitizenManager().getCitizens()) {
            final Optional<AbstractEntityCitizen> entityOpt = data.getEntity();
            if (entityOpt.isPresent() && entityOpt.get() instanceof EntityCitizen citizen) {
                final CitizenDebugSnapshot snap = ServerDebugSessionManager.buildSnapshot(colony.getID(), data, citizen);
                if (filter.isEmpty()
                        || snap.jobName().toLowerCase(Locale.ROOT).contains(filter)
                        || snap.citizenName().toLowerCase(Locale.ROOT).contains(filter)) {
                    matches.add(snap);
                }
            }
        }
        matches.sort(Comparator.comparingInt(CitizenDebugSnapshot::citizenId));

        player.sendSystemMessage(Component.literal(String.format(
                "═══ [AC Debug] Citizens in '%s' (%d loaded%s) ═══",
                colony.getName(),
                matches.size(),
                filter.isEmpty() ? "" : ", filter='" + filter + "'"))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (final CitizenDebugSnapshot snap : matches) {
            final ChatFormatting stateColor = snap.isGenuinelyStuck()
                    ? ChatFormatting.RED
                    : (!snap.activeRequestSummary().isEmpty() ? ChatFormatting.GOLD : ChatFormatting.AQUA);
            final MutableComponent row = Component.literal(String.format(
                    " [#%d] %s (%s) — %s%s ",
                    snap.citizenId(),
                    snap.citizenName(),
                    snap.jobName(),
                    snap.workerAiState(),
                    snap.isGenuinelyStuck()
                            ? String.format(" [STUCK %.1fs]", snap.realStalledTicks() / 20.0D)
                            : ""))
                    .withStyle(stateColor);
            row.append(buildActionLinks(snap.citizenId()));
            player.sendSystemMessage(row);
        }
        return Command.SINGLE_SUCCESS;
    }

    @NotNull
    private static MutableComponent buildActionLinks(final int citizenId) {
        final MutableComponent links = Component.empty();

        final MutableComponent focusBtn = Component.literal("[Focus]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/acdebug citizen " + citizenId))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Toggle 3D visual debugger for citizen #" + citizenId))));

        final MutableComponent inspectBtn = Component.literal(" [Inspect]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/acdebug inspect " + citizenId))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Print full diagnostic inspection for citizen #" + citizenId))));

        final MutableComponent tpBtn = Component.literal(" [TP]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/acdebug tp " + citizenId))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Teleport to citizen #" + citizenId))));

        final MutableComponent unstuckBtn = Component.literal(" [Unstuck]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/acdebug unstuck " + citizenId))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Force unstuck & reset stuck Builder AI for citizen #" + citizenId))));

        return links.append(focusBtn).append(inspectBtn).append(tpBtn).append(unstuckBtn);
    }

    @NotNull
    private static String formatPos(@Nullable final BlockPos pos) {
        return pos == null ? "None" : String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    private static int executeLayerToggle(
            @NotNull final CommandContext<CommandSourceStack> ctx,
            @NotNull final String layerName,
            @Nullable final Boolean enable) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || !checkDebuggerEnabled(player)) {
            return 0;
        }
        final ServerDebugSessionManager.PlayerDebugSession session =
                ServerDebugSessionManager.getOrCreateSession(player);
        final String key = layerName.toLowerCase(Locale.ROOT);
        boolean newState = true;
        switch (key) {
            case "actions", "ai", "state" -> {
                newState = enable != null ? enable : !session.isShowActions();
                session.setShowActions(newState);
            }
            case "stats", "vitals", "hp" -> {
                newState = enable != null ? enable : !session.isShowStats();
                session.setShowStats(newState);
            }
            case "path", "pathfinding", "navigation" -> {
                newState = enable != null ? enable : !session.isShowPathfinding();
                session.setShowPathfinding(newState);
            }
            case "all" -> {
                newState = enable != null ? enable : !(session.isShowActions() && session.isShowStats() && session.isShowPathfinding());
                session.setShowActions(newState);
                session.setShowStats(newState);
                session.setShowPathfinding(newState);
            }
            default -> {
                player.sendSystemMessage(Component.translatable("advancedcolonies.debug.invalid_layer", layerName)
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        }
        ServerDebugSessionManager.cleanupAndSync(player, session);
        player.sendSystemMessage(Component.translatable(
                "advancedcolonies.debug.layer_updated",
                key,
                String.valueOf(newState)).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeStatus(@NotNull final CommandContext<CommandSourceStack> ctx) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        final ServerDebugSessionManager.PlayerDebugSession session = ServerDebugSessionManager.getSession(player);
        if (session == null || session.isEmpty()) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.status_inactive")
                    .withStyle(ChatFormatting.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        int citizenCount = 0;
        for (final Map.Entry<Integer, Set<Integer>> entry : session.getWatchedCitizensByColony().entrySet()) {
            citizenCount += entry.getValue().size();
        }
        player.sendSystemMessage(Component.translatable(
                "advancedcolonies.debug.status_active",
                session.getWatchedColonies().toString(),
                citizenCount,
                session.isShowActions(),
                session.isShowStats(),
                session.isShowPathfinding()).withStyle(ChatFormatting.GOLD));
        if (session.getJobFilter() != null || session.isStuckOnlyFilter()) {
            player.sendSystemMessage(Component.literal(String.format(
                    "[AC Debug] Active Filters -> jobFilter='%s', stuckOnly=%s",
                    session.getJobFilter() != null ? session.getJobFilter() : "none",
                    session.isStuckOnlyFilter())).withStyle(ChatFormatting.AQUA));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeClearAll(@NotNull final CommandContext<CommandSourceStack> ctx) {
        final ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        ServerDebugSessionManager.clearAll(player);
        player.sendSystemMessage(Component.translatable("advancedcolonies.debug.cleared")
                .withStyle(ChatFormatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static boolean checkDebuggerEnabled(@NotNull final ServerPlayer player) {
        if (!AdvancedColoniesConfig.CITIZEN_COMMON.enableVisualDebugger.get()) {
            player.sendSystemMessage(Component.translatable("advancedcolonies.debug.disabled_in_config")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    @Nullable
    private static IColony resolvePlayerColony(@NotNull final ServerPlayer player) {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), player.blockPosition());
        if (colony == null) {
            colony = IColonyManager.getInstance().getIColonyByOwner(player.level(), player);
        }
        if (colony == null) {
            colony = IColonyManager.getInstance().getClosestColony(player.level(), player.blockPosition());
        }
        return colony;
    }

    @Nullable
    private static EntityCitizen raycastCitizen(@NotNull final ServerPlayer player, final double maxDistance) {
        final Vec3 eyePos = player.getEyePosition(1.0F);
        final Vec3 lookVec = player.getViewVector(1.0F);
        final Vec3 endPos = eyePos.add(lookVec.scale(maxDistance));
        final AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(maxDistance)).inflate(1.5D);
        final EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                player,
                eyePos,
                endPos,
                searchBox,
                entity -> entity instanceof EntityCitizen && !entity.isSpectator(),
                maxDistance * maxDistance);
        if (hitResult != null && hitResult.getEntity() instanceof EntityCitizen citizen) {
            return citizen;
        }
        return null;
    }
}
