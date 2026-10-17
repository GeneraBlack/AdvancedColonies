package com.advancedcolonies.common.debug;

import com.advancedcolonies.AdvancedColonies;
import com.advancedcolonies.config.AdvancedColoniesConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
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
import net.minecraft.network.chat.Component;
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

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Registers the {@code /acdebug} (and {@code /advancedcolonies debug}) Brigadier chat command tree
 * for enabling and disabling the in-game visual debugger per citizen or for an entire colony.
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
