package com.advancedcolonies.client.debug;

import com.advancedcolonies.AdvancedColonies;
import com.advancedcolonies.client.voice.ClientSpeechBubbleCache;
import com.advancedcolonies.common.debug.CitizenDebugSnapshot;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Renders the in-game visual debugger overlays and floating 3D speech bubbles on the client:
 * <ul>
 *   <li>Multi-line floating HUD billboards above debugged citizens showing AI actions, Builder stages,
 *       active requests/alerts, vitals/inventory stats, and accurate 3D pathfinding/stall status.</li>
 *   <li>Floating 3D speech bubbles above citizens when they speak context-reactive voicelines.</li>
 *   <li>3D world-space pathfinding node boxes, connecting route lines, climb/rail indicators,
 *       destination block highlights, and Builder/Miner work target &amp; stand boxes.</li>
 *   <li>Compact 2D top-right HUD summary &amp; focused citizen inspector panel.</li>
 * </ul>
 */
@EventBusSubscriber(modid = AdvancedColonies.MOD_ID, value = Dist.CLIENT)
public final class ClientVisualDebugRenderer {

    /** Full-bright lightmap coordinate so HUD billboards and speech bubbles remain crisp indoors and at night. */
    private static final int FULL_BRIGHT_LIGHT = LightTexture.FULL_BRIGHT;

    /** Line height in font pixels with extra breathing room to prevent descender overlap. */
    private static final int LINE_HEIGHT = 11;

    /** Horizontal and vertical padding (in font pixels) around unified billboard panels. */
    private static final int PANEL_PAD_X = 6;
    private static final int PANEL_PAD_Y = 4;

    private ClientVisualDebugRenderer() {
    }

    @SubscribeEvent
    public static void onClientDisconnect(@NotNull final ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDebugCache.clear();
        ClientSpeechBubbleCache.clear();
    }

    /**
     * Renders a compact top-right 2D HUD summary and focused citizen inspector card when the visual debugger is active.
     */
    @SubscribeEvent
    public static void onRenderGuiPost(@NotNull final RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            return;
        }
        if (!ClientDebugCache.isActive()) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }

        final Collection<CitizenDebugSnapshot> snapshots = ClientDebugCache.getAllSnapshots();
        if (snapshots.isEmpty()) {
            return;
        }

        final List<Component> hudLines = new ArrayList<>();
        int stuckCount = 0;
        int builderCount = 0;
        CitizenDebugSnapshot primaryFocus = null;

        for (final CitizenDebugSnapshot snap : snapshots) {
            if (snap.isGenuinelyStuck()) {
                stuckCount++;
            }
            if (!snap.builderStageInfo().isEmpty()) {
                builderCount++;
            }
            if (primaryFocus == null || snap.isGenuinelyStuck()) {
                primaryFocus = snap;
            }
        }

        hudLines.add(Component.literal(String.format(
                "AC Visual Debugger (%d tracked%s%s)",
                snapshots.size(),
                stuckCount > 0 ? " | " + stuckCount + " STUCK" : "",
                builderCount > 0 ? " | " + builderCount + " building" : ""))
                .withStyle(stuckCount > 0 ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD));

        int listedStuck = 0;
        for (final CitizenDebugSnapshot snap : snapshots) {
            if (snap.isGenuinelyStuck() && listedStuck < 4) {
                hudLines.add(Component.literal(String.format(
                        "⚠ [#%d] %s (%s): Stalled %.1fs | %s",
                        snap.citizenId(),
                        snap.citizenName(),
                        snap.jobName(),
                        snap.realStalledTicks() / 20.0D,
                        snap.workerAiState())).withStyle(ChatFormatting.RED));
                listedStuck++;
            }
        }

        if (snapshots.size() == 1 && primaryFocus != null) {
            hudLines.add(Component.literal(String.format(
                    "[#%d] %s • %s (%s)",
                    primaryFocus.citizenId(),
                    primaryFocus.citizenName(),
                    primaryFocus.jobName(),
                    primaryFocus.workBuildingInfo())).withStyle(ChatFormatting.AQUA));
            hudLines.add(Component.literal(String.format(
                    "AI: %s -> %s",
                    primaryFocus.citizenAiState(),
                    primaryFocus.workerAiState())).withStyle(ChatFormatting.WHITE));
            if (!primaryFocus.builderStageInfo().isEmpty()) {
                hudLines.add(Component.literal(
                        "Build: " + primaryFocus.builderStageInfo())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (!primaryFocus.activeRequestSummary().isEmpty()) {
                hudLines.add(Component.literal(primaryFocus.activeRequestSummary())
                        .withStyle(ChatFormatting.YELLOW));
            }
            hudLines.add(Component.literal(String.format(
                    "Speed: %.2f b/s | Dist: %.1fm | Hand: %s (%d/%d)",
                    primaryFocus.moveSpeedBps(),
                    primaryFocus.distanceToTarget(),
                    primaryFocus.heldItemName(),
                    primaryFocus.inventoryUsedSlots(),
                    primaryFocus.inventoryTotalSlots())).withStyle(ChatFormatting.GREEN));
        }

        final Font font = mc.font;
        final GuiGraphics graphics = event.getGuiGraphics();
        int maxWidth = 0;
        for (final Component line : hudLines) {
            final int w = font.width(line);
            if (w > maxWidth) {
                maxWidth = w;
            }
        }

        final int screenWidth = mc.getWindow().getGuiScaledWidth();
        final int boxWidth = maxWidth + 10;
        final int boxHeight = hudLines.size() * 10 + 6;
        final int xLeft = screenWidth - boxWidth - 6;
        final int yTop = 6;

        graphics.fill(xLeft, yTop, xLeft + boxWidth, yTop + boxHeight, 0xCC0E121A);
        graphics.fill(xLeft, yTop, xLeft + boxWidth, yTop + 1, stuckCount > 0 ? 0xFFFF4444 : 0xFF3A7AFE);

        int yCursor = yTop + 4;
        for (final Component line : hudLines) {
            graphics.drawString(font, line, xLeft + 5, yCursor, 0xFFFFFFFF, true);
            yCursor += 10;
        }
    }

    /**
     * Renders the floating multi-line debug billboard and/or context-reactive speech bubble
     * above any {@link EntityCitizen}.
     */
    @SubscribeEvent
    public static void onRenderLivingPost(@NotNull final RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof EntityCitizen citizen)) {
            return;
        }
        final CitizenDebugSnapshot snapshot = ClientDebugCache.getByEntityId(citizen.getId());
        final ClientSpeechBubbleCache.SpeechBubbleEntry bubble = ClientSpeechBubbleCache.getActiveBubble(citizen.getId());

        if (snapshot == null && bubble == null) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityRenderDispatcher().distanceToSqr(citizen) > 4096.0D) {
            return;
        }

        final Font font = mc.font;
        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource bufferSource = event.getMultiBufferSource();

        // Start cleanly above both the vanilla nametag (bbHeight + 0.50) and MineColonies' status icon (bbHeight + 0.80..1.05)
        final double baseAnchorY = citizen.getBbHeight() + 1.15D;

        int debugLineCount = 0;
        if (snapshot != null) {
            final List<Component> lines = buildBillboardLines(snapshot);
            debugLineCount = lines.size();
            if (!lines.isEmpty()) {
                renderDebugBillboard(
                        poseStack,
                        bufferSource,
                        mc,
                        font,
                        baseAnchorY,
                        lines,
                        snapshot.isGenuinelyStuck());
            }
        }

        if (bubble != null) {
            final double bubbleAnchorY = debugLineCount > 0
                    ? baseAnchorY + (debugLineCount * LINE_HEIGHT + PANEL_PAD_Y * 2 + 6) * 0.026D
                    : baseAnchorY;
            renderSpeechBubble(poseStack, bufferSource, mc, font, bubbleAnchorY, bubble);
        }
    }

    private static void renderDebugBillboard(
            @NotNull final PoseStack poseStack,
            @NotNull final MultiBufferSource bufferSource,
            @NotNull final Minecraft mc,
            @NotNull final Font font,
            final double anchorY,
            @NotNull final List<Component> lines,
            final boolean isStuck) {

        int maxLineWidth = 0;
        for (final Component line : lines) {
            final int w = font.width(line);
            if (w > maxLineWidth) {
                maxLineWidth = w;
            }
        }

        final int totalTextHeight = lines.size() * LINE_HEIGHT - 2;
        final float halfWidth = maxLineWidth / 2.0F;
        final float left = -halfWidth - PANEL_PAD_X;
        final float right = halfWidth + PANEL_PAD_X;
        final float top = -totalTextHeight - PANEL_PAD_Y;
        final float bottom = PANEL_PAD_Y;

        poseStack.pushPose();
        poseStack.translate(0.0D, anchorY, 0.0D);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        // Offset slightly toward the camera in billboard space so it never Z-fights with citizen nametag/icon planes
        poseStack.translate(0.0F, 0.0F, 0.04F);
        poseStack.scale(0.026F, -0.026F, 0.026F);

        // 1. Draw a single unified dark background panel behind the text (red top accent if genuinely stuck)
        drawUnifiedPanelBackground(
                poseStack,
                bufferSource,
                left,
                top,
                right,
                bottom,
                0xD80E121A,
                isStuck ? 0xFFFF3B3B : 0xFF3A4A68);

        // 2. Push text well forward in Z (0.20F in font space) and render with POLYGON_OFFSET + dropShadow + FULL_BRIGHT
        poseStack.translate(0.0F, 0.0F, 0.20F);
        final Matrix4f textMatrix = poseStack.last().pose();

        int currentY = -totalTextHeight;
        for (final Component line : lines) {
            final float xOffset = -font.width(line) / 2.0F;
            font.drawInBatch(
                    line,
                    xOffset,
                    (float) currentY,
                    0xFFFFFFFF,
                    true,
                    textMatrix,
                    bufferSource,
                    Font.DisplayMode.POLYGON_OFFSET,
                    0,
                    FULL_BRIGHT_LIGHT);
            currentY += LINE_HEIGHT;
        }

        poseStack.popPose();
    }

    private static void renderSpeechBubble(
            @NotNull final PoseStack poseStack,
            @NotNull final MultiBufferSource bufferSource,
            @NotNull final Minecraft mc,
            @NotNull final Font font,
            final double anchorY,
            @NotNull final ClientSpeechBubbleCache.SpeechBubbleEntry bubble) {

        final List<FormattedCharSequence> wrappedText = font.split(
                Component.literal("\"" + bubble.text() + "\""),
                175);
        if (wrappedText.isEmpty()) {
            return;
        }

        final String headerStr = "Unemployed".equalsIgnoreCase(bubble.jobName())
                ? String.format("[%s]", bubble.citizenName())
                : String.format("[%s • %s]", bubble.citizenName(), bubble.jobName());
        final Component headerComponent = Component.literal(headerStr);

        int maxLineWidth = font.width(headerComponent);
        for (final FormattedCharSequence seq : wrappedText) {
            final int w = font.width(seq);
            if (w > maxLineWidth) {
                maxLineWidth = w;
            }
        }

        final int totalLines = 1 + wrappedText.size();
        final int totalTextHeight = totalLines * LINE_HEIGHT - 2;
        final float halfWidth = maxLineWidth / 2.0F;
        final float left = -halfWidth - PANEL_PAD_X;
        final float right = halfWidth + PANEL_PAD_X;
        final float top = -totalTextHeight - PANEL_PAD_Y;
        final float bottom = PANEL_PAD_Y;
        final int accentColor = 0xFF000000 | (bubble.accentColorRgb() & 0xFFFFFF);

        poseStack.pushPose();
        poseStack.translate(0.0D, anchorY, 0.0D);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        // Slightly further toward camera than debug billboard so stacked panels never Z-fight
        poseStack.translate(0.0F, 0.0F, 0.06F);
        poseStack.scale(0.026F, -0.026F, 0.026F);

        // 1. Draw unified dark speech bubble panel with accent-colored top border
        drawUnifiedPanelBackground(
                poseStack,
                bufferSource,
                left,
                top,
                right,
                bottom,
                0xDC10121C,
                accentColor);

        // 2. Push text well forward in Z (0.20F in font space) and render with POLYGON_OFFSET + dropShadow + FULL_BRIGHT
        poseStack.translate(0.0F, 0.0F, 0.20F);
        final Matrix4f textMatrix = poseStack.last().pose();

        int currentY = -totalTextHeight;
        final float headerX = -font.width(headerComponent) / 2.0F;
        font.drawInBatch(
                headerComponent,
                headerX,
                (float) currentY,
                accentColor,
                true,
                textMatrix,
                bufferSource,
                Font.DisplayMode.POLYGON_OFFSET,
                0,
                FULL_BRIGHT_LIGHT);
        currentY += LINE_HEIGHT;

        for (final FormattedCharSequence seq : wrappedText) {
            final float xOffset = -font.width(seq) / 2.0F;
            font.drawInBatch(
                    seq,
                    xOffset,
                    (float) currentY,
                    0xFFFFFFFF,
                    true,
                    textMatrix,
                    bufferSource,
                    Font.DisplayMode.POLYGON_OFFSET,
                    0,
                    FULL_BRIGHT_LIGHT);
            currentY += LINE_HEIGHT;
        }

        poseStack.popPose();
    }

    /**
     * Draws a single unified background card and non-overlapping border frame using {@link RenderType#textBackground()},
     * completely avoiding multi-line overlapping background Z-fighting.
     */
    private static void drawUnifiedPanelBackground(
            @NotNull final PoseStack poseStack,
            @NotNull final MultiBufferSource bufferSource,
            final float left,
            final float top,
            final float right,
            final float bottom,
            final int bgColorArgb,
            final int accentBorderArgb) {

        final Matrix4f matrix = poseStack.last().pose();
        final VertexConsumer bgConsumer = bufferSource.getBuffer(RenderType.textBackground());
        final int subtleFrameColor = 0xCC1E2636;

        // Main panel body (disjoint interior rectangle)
        addQuad(bgConsumer, matrix, left, top, right, bottom, 0.0F, bgColorArgb);

        // Non-overlapping outer frame (1.5px top accent bar + 1px left/right/bottom border)
        addQuad(bgConsumer, matrix, left - 1.0F, top - 1.5F, right + 1.0F, top, 0.0F, accentBorderArgb);
        addQuad(bgConsumer, matrix, left - 1.0F, bottom, right + 1.0F, bottom + 1.0F, 0.0F, subtleFrameColor);
        addQuad(bgConsumer, matrix, left - 1.0F, top, left, bottom, 0.0F, subtleFrameColor);
        addQuad(bgConsumer, matrix, right, top, right + 1.0F, bottom, 0.0F, subtleFrameColor);
    }

    private static void addQuad(
            @NotNull final VertexConsumer consumer,
            @NotNull final Matrix4f matrix,
            final float x1,
            final float y1,
            final float x2,
            final float y2,
            final float z,
            final int argb) {
        consumer.addVertex(matrix, x1, y2, z).setColor(argb).setLight(FULL_BRIGHT_LIGHT);
        consumer.addVertex(matrix, x2, y2, z).setColor(argb).setLight(FULL_BRIGHT_LIGHT);
        consumer.addVertex(matrix, x2, y1, z).setColor(argb).setLight(FULL_BRIGHT_LIGHT);
        consumer.addVertex(matrix, x1, y1, z).setColor(argb).setLight(FULL_BRIGHT_LIGHT);
    }

    /**
     * Renders 3D pathfinding routes, node boxes (ground, ladder, rail, reached, current),
     * target block highlights, and Builder/Miner work target &amp; stand boxes in world space.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(@NotNull final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!ClientDebugCache.isActive() || !ClientDebugCache.isShowPathfinding()) {
            return;
        }

        final Collection<CitizenDebugSnapshot> snapshots = ClientDebugCache.getAllSnapshots();
        if (snapshots.isEmpty()) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        final Vec3 camPos = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        final VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        for (final CitizenDebugSnapshot snapshot : snapshots) {
            renderCitizenPath3D(poseStack, lineConsumer, mc, snapshot);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    @NotNull
    private static List<Component> buildBillboardLines(@NotNull final CitizenDebugSnapshot snapshot) {
        final List<Component> lines = new ArrayList<>(6);

        // Line 1: [#ID] Name (Job • WorkBuilding)
        final String buildingSuffix = ("None".equals(snapshot.workBuildingInfo()) || snapshot.workBuildingInfo().isEmpty())
                ? ""
                : " • " + snapshot.workBuildingInfo();
        lines.add(Component.literal(String.format(
                "[#%d] %s (%s%s)",
                snapshot.citizenId(),
                snapshot.citizenName(),
                snapshot.jobName(),
                buildingSuffix)).withStyle(ChatFormatting.GOLD));

        // Line 2: Actions (Citizen AI State + Worker AI State + Builder Progress + Requests)
        if (ClientDebugCache.isShowActions()) {
            final String cState = snapshot.citizenAiState();
            final String wState = snapshot.workerAiState();
            final boolean isCriticalState = snapshot.isGenuinelyStuck()
                    || cState.contains("SICK")
                    || cState.contains("FLEE")
                    || wState.contains("FLEE");
            final boolean isWaitingOrSpecialState = cState.contains("MOURN")
                    || wState.contains("SLEEP")
                    || wState.contains("REGEN")
                    || wState.contains("NEEDS")
                    || wState.contains("GATHERING_REQUIRED_MATERIALS");
            final ChatFormatting actionColor = isCriticalState
                    ? ChatFormatting.RED
                    : (isWaitingOrSpecialState ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA);
            lines.add(Component.literal(String.format(
                    "AI: %s -> %s",
                    cState,
                    wState)).withStyle(actionColor));

            if (!snapshot.builderStageInfo().isEmpty()) {
                final BlockPos workPos = snapshot.workTargetPos();
                final String posStr = workPos != null
                        ? String.format(" @ (%d, %d, %d)", workPos.getX(), workPos.getY(), workPos.getZ())
                        : "";
                lines.add(Component.literal("Build: " + snapshot.builderStageInfo() + posStr)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            if (!snapshot.activeRequestSummary().isEmpty()) {
                lines.add(Component.literal(snapshot.activeRequestSummary())
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        // Line 3: Stats (Health, Saturation, Happiness, Held Item, Inventory Fill)
        if (ClientDebugCache.isShowStats()) {
            final ChatFormatting statsColor = (snapshot.health() < snapshot.maxHealth() * 0.4F || snapshot.saturation() < 4.0D)
                    ? ChatFormatting.RED
                    : ChatFormatting.GREEN;
            final String invSummary = snapshot.inventoryTotalSlots() > 0
                    ? String.format(" | Hand: %s | Inv: %d/%d",
                            snapshot.heldItemName(),
                            snapshot.inventoryUsedSlots(),
                            snapshot.inventoryTotalSlots())
                    : "";
            lines.add(Component.literal(String.format(
                    "HP: %.1f/%.1f | Sat: %.1f | Hap: %.1f%s",
                    snapshot.health(),
                    snapshot.maxHealth(),
                    snapshot.saturation(),
                    snapshot.happiness(),
                    invSummary)).withStyle(statsColor));
        }

        // Line 4: Pathfinding & Genuine 3D Stuck Diagnostics (no more false-positive globalTimeout alerts!)
        if (ClientDebugCache.isShowPathfinding()) {
            final BlockPos target = snapshot.targetPos();
            final String targetStr = target != null
                    ? String.format("(%d, %d, %d)", target.getX(), target.getY(), target.getZ())
                    : "None";

            if (snapshot.isGenuinelyStuck()) {
                final double stalledSeconds = snapshot.realStalledTicks() / 20.0D;
                final String rescueTag = !"None".equals(snapshot.rescueAction())
                        ? " | " + snapshot.rescueAction()
                        : "";
                lines.add(Component.literal(String.format(
                        "⚠ STUCK [Lv.%d | Stalled %.1fs%s] -> %s",
                        snapshot.stuckLevel(),
                        stalledSeconds,
                        rescueTag,
                        targetStr)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            } else if (!snapshot.pathNodes().isEmpty()) {
                lines.add(Component.literal(String.format(
                        "Path: %s [%d/%d] | %.1f b/s | %.1fm -> %s",
                        snapshot.pathStatus(),
                        Math.min(snapshot.currentNodeIndex() + 1, snapshot.pathNodes().size()),
                        snapshot.pathNodes().size(),
                        snapshot.moveSpeedBps(),
                        snapshot.distanceToTarget(),
                        targetStr)).withStyle(ChatFormatting.YELLOW));
            } else {
                lines.add(Component.literal(String.format(
                        "Path: %s | %.1f b/s -> %s",
                        snapshot.pathStatus(),
                        snapshot.moveSpeedBps(),
                        targetStr)).withStyle(ChatFormatting.GRAY));
            }
        }

        return lines;
    }

    private static void renderCitizenPath3D(
            @NotNull final PoseStack poseStack,
            @NotNull final VertexConsumer consumer,
            @NotNull final Minecraft mc,
            @NotNull final CitizenDebugSnapshot snapshot) {
        final List<CitizenDebugSnapshot.PathNodeEntry> nodes = snapshot.pathNodes();
        final boolean isStuck = snapshot.isGenuinelyStuck();

        // 1. Render all path nodes and connecting segments
        for (int i = 0; i < nodes.size(); i++) {
            final CitizenDebugSnapshot.PathNodeEntry node = nodes.get(i);
            final boolean isCurrent = (i == snapshot.currentNodeIndex());

            final float r;
            final float g;
            final float b;
            final float a;

            if (node.reached()) {
                r = 0.40F;
                g = 0.65F;
                b = 0.95F;
                a = 0.45F;
            } else if (isCurrent) {
                r = 1.00F;
                g = 0.85F;
                b = 0.10F;
                a = 0.95F;
            } else if (isStuck) {
                r = 1.00F;
                g = 0.20F;
                b = 0.20F;
                a = 0.95F;
            } else if (node.onLadder()) {
                r = 0.10F;
                g = 0.95F;
                b = 1.00F;
                a = 0.95F;
            } else if (node.onRails()) {
                r = 1.00F;
                g = 0.55F;
                b = 0.10F;
                a = 0.95F;
            } else {
                r = 0.20F;
                g = 0.95F;
                b = 0.35F;
                a = 0.85F;
            }

            final double boxHalfWidth = (isCurrent || node.onLadder()) ? 0.25D : 0.15D;
            final double boxHeight = node.onLadder() ? 0.80D : (isCurrent ? 0.40D : 0.22D);

            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    node.x() + 0.5D - boxHalfWidth,
                    node.y() + 0.05D,
                    node.z() + 0.5D - boxHalfWidth,
                    node.x() + 0.5D + boxHalfWidth,
                    node.y() + 0.05D + boxHeight,
                    node.z() + 0.5D + boxHalfWidth,
                    r,
                    g,
                    b,
                    a);

            if (i + 1 < nodes.size()) {
                final CitizenDebugSnapshot.PathNodeEntry next = nodes.get(i + 1);
                drawLineSegment(
                        poseStack,
                        consumer,
                        (float) (node.x() + 0.5D),
                        (float) (node.y() + 0.20D),
                        (float) (node.z() + 0.5D),
                        (float) (next.x() + 0.5D),
                        (float) (next.y() + 0.20D),
                        (float) (next.z() + 0.5D),
                        r,
                        g,
                        b,
                        a);
            }
        }

        final Entity citizenEntity = mc.level != null ? mc.level.getEntity(snapshot.entityId()) : null;

        // 2. Highlight navigation target block position and draw direct line when stuck or lacking path nodes
        final BlockPos targetPos = snapshot.targetPos();
        if (targetPos != null) {
            final float tr = isStuck ? 1.00F : 0.95F;
            final float tg = isStuck ? 0.15F : 0.25F;
            final float tb = isStuck ? 0.15F : 0.95F;

            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    targetPos.getX() - 0.02D,
                    targetPos.getY() - 0.02D,
                    targetPos.getZ() - 0.02D,
                    targetPos.getX() + 1.02D,
                    targetPos.getY() + 1.02D,
                    targetPos.getZ() + 1.02D,
                    tr,
                    tg,
                    tb,
                    0.95F);

            if ((isStuck || nodes.isEmpty()) && citizenEntity != null) {
                drawLineSegment(
                        poseStack,
                        consumer,
                        (float) citizenEntity.getX(),
                        (float) (citizenEntity.getY() + 1.0D),
                        (float) citizenEntity.getZ(),
                        (float) (targetPos.getX() + 0.5D),
                        (float) (targetPos.getY() + 0.5D),
                        (float) (targetPos.getZ() + 0.5D),
                        tr,
                        tg,
                        tb,
                        0.90F);
            }
        }

        // 3. Highlight Builder/Miner active structure block (workTargetPos) & standing position (workStandPos)
        final BlockPos workTarget = snapshot.workTargetPos();
        if (workTarget != null) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    workTarget.getX() - 0.04D,
                    workTarget.getY() - 0.04D,
                    workTarget.getZ() - 0.04D,
                    workTarget.getX() + 1.04D,
                    workTarget.getY() + 1.04D,
                    workTarget.getZ() + 1.04D,
                    1.00F,
                    0.60F,
                    0.05F,
                    0.95F);

            if (citizenEntity != null) {
                drawLineSegment(
                        poseStack,
                        consumer,
                        (float) citizenEntity.getX(),
                        (float) (citizenEntity.getY() + 1.15D),
                        (float) citizenEntity.getZ(),
                        (float) (workTarget.getX() + 0.5D),
                        (float) (workTarget.getY() + 0.5D),
                        (float) (workTarget.getZ() + 0.5D),
                        1.00F,
                        0.60F,
                        0.05F,
                        0.85F);
            }
        }

        final BlockPos workStand = snapshot.workStandPos();
        if (workStand != null) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    workStand.getX() + 0.10D,
                    workStand.getY() + 0.02D,
                    workStand.getZ() + 0.10D,
                    workStand.getX() + 0.90D,
                    workStand.getY() + 0.35D,
                    workStand.getZ() + 0.90D,
                    0.10F,
                    0.95F,
                    0.75F,
                    0.90F);

            if (workTarget != null) {
                drawLineSegment(
                        poseStack,
                        consumer,
                        (float) (workStand.getX() + 0.5D),
                        (float) (workStand.getY() + 0.25D),
                        (float) (workStand.getZ() + 0.5D),
                        (float) (workTarget.getX() + 0.5D),
                        (float) (workTarget.getY() + 0.5D),
                        (float) (workTarget.getZ() + 0.5D),
                        0.10F,
                        0.95F,
                        0.75F,
                        0.80F);
            }
        }
    }

    private static void drawLineSegment(
            @NotNull final PoseStack poseStack,
            @NotNull final VertexConsumer consumer,
            final float x1,
            final float y1,
            final float z1,
            final float x2,
            final float y2,
            final float z2,
            final float r,
            final float g,
            final float b,
            final float a) {
        final PoseStack.Pose pose = poseStack.last();
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        final float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-4F) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dx = 0.0F;
            dy = 1.0F;
            dz = 0.0F;
        }

        consumer.addVertex(pose, x1, y1, z1)
                .setColor(r, g, b, a)
                .setNormal(pose, dx, dy, dz);
        consumer.addVertex(pose, x2, y2, z2)
                .setColor(r, g, b, a)
                .setNormal(pose, dx, dy, dz);
    }
}
