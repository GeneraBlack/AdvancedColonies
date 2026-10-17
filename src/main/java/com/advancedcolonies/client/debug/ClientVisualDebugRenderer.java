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
import net.minecraft.client.renderer.LevelRenderer;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Renders the in-game visual debugger overlays and floating 3D speech bubbles on the client:
 * <ul>
 *   <li>Multi-line floating HUD billboards above debugged citizens showing AI actions, vitals/stats,
 *       and pathfinding/stuck status.</li>
 *   <li>Floating 3D speech bubbles above citizens when they speak context-reactive voicelines.</li>
 *   <li>3D world-space pathfinding node boxes, connecting route lines, climb/rail indicators, and
 *       destination block highlights.</li>
 * </ul>
 */
@EventBusSubscriber(modid = AdvancedColonies.MOD_ID, value = Dist.CLIENT)
public final class ClientVisualDebugRenderer {

    private ClientVisualDebugRenderer() {
    }

    @SubscribeEvent
    public static void onClientDisconnect(@NotNull final ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDebugCache.clear();
        ClientSpeechBubbleCache.clear();
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
        final Font font = mc.font;
        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource bufferSource = event.getMultiBufferSource();
        final int packedLight = event.getPackedLight();

        int debugLineCount = 0;
        if (snapshot != null) {
            final List<Component> lines = buildBillboardLines(snapshot);
            debugLineCount = lines.size();
            if (!lines.isEmpty()) {
                poseStack.pushPose();
                poseStack.translate(0.0D, citizen.getBbHeight() + 0.95D, 0.0D);
                poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                poseStack.scale(0.022F, -0.022F, 0.022F);

                final Matrix4f matrix4f = poseStack.last().pose();
                final int lineHeight = 10;
                final int totalHeight = lines.size() * lineHeight;
                int currentY = -totalHeight;

                for (final Component line : lines) {
                    final float xOffset = (float) (-font.width(line) / 2);
                    font.drawInBatch(
                            line,
                            xOffset,
                            (float) currentY,
                            0xFFFFFFFF,
                            false,
                            matrix4f,
                            bufferSource,
                            Font.DisplayMode.NORMAL,
                            0x88000000,
                            packedLight);
                    currentY += lineHeight;
                }

                poseStack.popPose();
            }
        }

        if (bubble != null) {
            renderSpeechBubble(poseStack, bufferSource, mc, font, citizen, bubble, debugLineCount, packedLight);
        }
    }

    private static void renderSpeechBubble(
            @NotNull final PoseStack poseStack,
            @NotNull final MultiBufferSource bufferSource,
            @NotNull final Minecraft mc,
            @NotNull final Font font,
            @NotNull final EntityCitizen citizen,
            @NotNull final ClientSpeechBubbleCache.SpeechBubbleEntry bubble,
            final int debugLineCount,
            final int packedLight) {
        final List<FormattedCharSequence> wrappedText = font.split(
                Component.literal("\"" + bubble.text() + "\""),
                170);
        if (wrappedText.isEmpty()) {
            return;
        }

        final String headerStr = "Unemployed".equalsIgnoreCase(bubble.jobName())
                ? String.format("[%s]", bubble.citizenName())
                : String.format("[%s • %s]", bubble.citizenName(), bubble.jobName());
        final Component headerComponent = Component.literal(headerStr);

        final double baseOffset = debugLineCount > 0
                ? (0.95D + debugLineCount * 0.24D + 0.25D)
                : 0.85D;

        poseStack.pushPose();
        poseStack.translate(0.0D, citizen.getBbHeight() + baseOffset, 0.0D);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.023F, -0.023F, 0.023F);

        final Matrix4f matrix4f = poseStack.last().pose();
        final int lineHeight = 10;
        final int totalLines = 1 + wrappedText.size();
        int currentY = -(totalLines * lineHeight);

        final int accentColor = 0xFF000000 | (bubble.accentColorRgb() & 0xFFFFFF);
        final float headerX = (float) (-font.width(headerComponent) / 2);
        font.drawInBatch(
                headerComponent,
                headerX,
                (float) currentY,
                accentColor,
                false,
                matrix4f,
                bufferSource,
                Font.DisplayMode.NORMAL,
                0xA0101018,
                packedLight);
        currentY += lineHeight;

        for (final FormattedCharSequence seq : wrappedText) {
            final float xOffset = (float) (-font.width(seq) / 2);
            font.drawInBatch(
                    seq,
                    xOffset,
                    (float) currentY,
                    0xFFF8F8F8,
                    false,
                    matrix4f,
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    0xA0101018,
                    packedLight);
            currentY += lineHeight;
        }

        poseStack.popPose();
    }

    /**
     * Renders 3D pathfinding routes, node boxes (ground, ladder, rail, reached, current),
     * and target block highlights in world space.
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
        final List<Component> lines = new ArrayList<>(4);

        // Line 1: [#ID] Name (Job)
        lines.add(Component.literal(String.format(
                "[#%d] %s (%s)",
                snapshot.citizenId(),
                snapshot.citizenName(),
                snapshot.jobName())).withStyle(ChatFormatting.GOLD));

        // Line 2: Actions (Citizen AI State + Worker AI State)
        if (ClientDebugCache.isShowActions()) {
            final String cState = snapshot.citizenAiState();
            final String wState = snapshot.workerAiState();
            final boolean isCriticalState = snapshot.stuckLevel() > 0
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
        }

        // Line 3: Stats (Health, Saturation, Happiness)
        if (ClientDebugCache.isShowStats()) {
            final ChatFormatting statsColor = (snapshot.health() < snapshot.maxHealth() * 0.4F || snapshot.saturation() < 4.0D)
                    ? ChatFormatting.RED
                    : ChatFormatting.GREEN;
            lines.add(Component.literal(String.format(
                    "HP: %.1f/%.1f | Sat: %.1f | Hap: %.1f",
                    snapshot.health(),
                    snapshot.maxHealth(),
                    snapshot.saturation(),
                    snapshot.happiness())).withStyle(statsColor));
        }

        // Line 4: Pathfinding & Stuck Diagnostics
        if (ClientDebugCache.isShowPathfinding()) {
            final BlockPos target = snapshot.targetPos();
            final String targetStr = target != null
                    ? String.format("(%d, %d, %d)", target.getX(), target.getY(), target.getZ())
                    : "None";

            if (snapshot.stuckLevel() > 0 || snapshot.globalTimeoutTicks() >= 120) {
                final double stuckSeconds = snapshot.globalTimeoutTicks() / 20.0D;
                lines.add(Component.literal(String.format(
                        "STUCK [Lv.%d | %.1fs] -> %s",
                        snapshot.stuckLevel(),
                        stuckSeconds,
                        targetStr)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            } else if (!snapshot.pathNodes().isEmpty()) {
                lines.add(Component.literal(String.format(
                        "Path: %s [%d/%d] -> %s",
                        snapshot.pathStatus(),
                        Math.min(snapshot.currentNodeIndex() + 1, snapshot.pathNodes().size()),
                        snapshot.pathNodes().size(),
                        targetStr)).withStyle(ChatFormatting.YELLOW));
            } else {
                lines.add(Component.literal(String.format(
                        "Path: %s -> %s",
                        snapshot.pathStatus(),
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
        final boolean isStuck = snapshot.stuckLevel() > 0 || snapshot.globalTimeoutTicks() >= 120;

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

        // 2. Highlight target block position and draw direct line when stuck or lacking path nodes
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

            if (isStuck || nodes.isEmpty()) {
                final Entity citizenEntity = mc.level != null ? mc.level.getEntity(snapshot.entityId()) : null;
                if (citizenEntity != null) {
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
