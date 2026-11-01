package com.wrights.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public final class BlueprintModelView {
    private static List<WrightsClientState.Ghost> bakedFor;
    private static ModelMesh baked = ModelMesh.EMPTY;

    private BlueprintModelView() {
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height, List<WrightsClientState.Ghost> ghosts, int sizeX, int sizeY, int sizeZ, float yaw, float pitch) {
        render(graphics, x, y, width, height, ghosts, sizeX, sizeY, sizeZ, yaw, pitch, false, 0, 0, 0);
    }

    /**
     * @param showFrame when true, draws the AABB wireframe and a marker at the pivot block
     * @param pivotX pivot block coords relative to the preview origin (0..size-1)
     */
    public static void render(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            List<WrightsClientState.Ghost> ghosts,
            int sizeX,
            int sizeY,
            int sizeZ,
            float yaw,
            float pitch,
            boolean showFrame,
            int pivotX,
            int pivotY,
            int pivotZ) {
        if (ghosts == null || ghosts.isEmpty() || width < 8 || height < 8) {
            return;
        }
        graphics.flush();
        graphics.enableScissor(x, y, x + width, y + height);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width / 2.0D, y + height / 2.0D, 250.0D);
        float span = Math.max(1, Math.max(sizeX, Math.max(sizeY, sizeZ)));
        float scale = Math.min(width, height) * 0.62F / span;
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-sizeX / 2.0D, -sizeY / 2.0D, -sizeZ / 2.0D);

        ModelMesh mesh = meshFor(ghosts);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.enableDepthTest();
        mesh.draw(modelView, projection);
        if (showFrame) {
            drawFrameAndPivot(pose, sizeX, sizeY, sizeZ, pivotX, pivotY, pivotZ);
        }
        RenderSystem.disableDepthTest();
        pose.popPose();
        graphics.disableScissor();
    }

    /** Thin AABB around the preview blocks, plus a copper cross at the chosen pivot cell. */
    private static void drawFrameAndPivot(PoseStack pose, int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY, int pivotZ) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        float pad = 0.02F;
        float x0 = -pad;
        float y0 = -pad;
        float z0 = -pad;
        float x1 = sizeX + pad;
        float y1 = sizeY + pad;
        float z1 = sizeZ + pad;
        // Soft steel frame around the shape box.
        boxEdges(pose, lines, x0, y0, z0, x1, y1, z1, 0.55F, 0.72F, 0.86F, 0.9F);
        int px = Math.max(0, Math.min(sizeX - 1, pivotX));
        int py = Math.max(0, Math.min(sizeY - 1, pivotY));
        int pz = Math.max(0, Math.min(sizeZ - 1, pivotZ));
        float cx = px + 0.5F;
        float cy = py + 0.5F;
        float cz = pz + 0.5F;
        float arm = 0.42F;
        // Pivot marker — copper cross through the pin cell.
        line(pose, lines, cx - arm, cy, cz, cx + arm, cy, cz, 0.91F, 0.55F, 0.23F, 1.0F);
        line(pose, lines, cx, cy - arm, cz, cx, cy + arm, cz, 0.91F, 0.55F, 0.23F, 1.0F);
        line(pose, lines, cx, cy, cz - arm, cx, cy, cz + arm, 0.91F, 0.55F, 0.23F, 1.0F);
        float box = 0.18F;
        boxEdges(pose, lines, cx - box, cy - box, cz - box, cx + box, cy + box, cz + box, 0.98F, 0.78F, 0.35F, 1.0F);
        buffers.endBatch(RenderType.lines());
    }

    private static void boxEdges(
            PoseStack pose,
            VertexConsumer lines,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float red,
            float green,
            float blue,
            float alpha) {
        line(pose, lines, x0, y0, z0, x1, y0, z0, red, green, blue, alpha);
        line(pose, lines, x1, y0, z0, x1, y0, z1, red, green, blue, alpha);
        line(pose, lines, x1, y0, z1, x0, y0, z1, red, green, blue, alpha);
        line(pose, lines, x0, y0, z1, x0, y0, z0, red, green, blue, alpha);
        line(pose, lines, x0, y1, z0, x1, y1, z0, red, green, blue, alpha);
        line(pose, lines, x1, y1, z0, x1, y1, z1, red, green, blue, alpha);
        line(pose, lines, x1, y1, z1, x0, y1, z1, red, green, blue, alpha);
        line(pose, lines, x0, y1, z1, x0, y1, z0, red, green, blue, alpha);
        line(pose, lines, x0, y0, z0, x0, y1, z0, red, green, blue, alpha);
        line(pose, lines, x1, y0, z0, x1, y1, z0, red, green, blue, alpha);
        line(pose, lines, x1, y0, z1, x1, y1, z1, red, green, blue, alpha);
        line(pose, lines, x0, y0, z1, x0, y1, z1, red, green, blue, alpha);
    }

    private static void line(
            PoseStack pose,
            VertexConsumer lines,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float red,
            float green,
            float blue,
            float alpha) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        PoseStack.Pose last = pose.last();
        lines.addVertex(last, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(last, dx, dy, dz);
        lines.addVertex(last, x2, y2, z2).setColor(red, green, blue, alpha).setNormal(last, dx, dy, dz);
    }

    /** Every block stays on its real coordinate. The mesh is built once so turning it stays cheap. */
    private static ModelMesh meshFor(List<WrightsClientState.Ghost> ghosts) {
        if (ghosts == bakedFor) {
            return baked;
        }
        ModelMesh next = ModelMesh.build(ghosts);
        baked.close();
        bakedFor = ghosts;
        baked = next;
        return baked;
    }

    /** Water and lava have no block model, so the preview draws the fluid texture as a cube. */
    static void renderFluid(PoseStack pose, MultiBufferSource buffer, BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getType());
        ResourceLocation texture = fluid.isSource() ? ext.getStillTexture() : ext.getFlowingTexture();
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(texture);
        int tint = ext.getTintColor();
        int alpha = state.is(Blocks.WATER) ? 170 : (tint >>> 24) == 0 ? 255 : (tint >>> 24);
        int red = (tint >> 16) & 255;
        int green = (tint >> 8) & 255;
        int blue = tint & 255;
        float top = state.is(Blocks.LAVA) ? 1.0F : 0.9F;
        VertexConsumer consumer = buffer.getBuffer(RenderType.translucent());
        Matrix4f matrix = pose.last().pose();
        float u0 = sprite.getU(0.0F);
        float u1 = sprite.getU(1.0F);
        float v0 = sprite.getV(0.0F);
        float v1 = sprite.getV(1.0F);
        quad(consumer, matrix, 0, top, 1, 1, top, 1, 1, top, 0, 0, top, 0, u0, v0, u1, v1, red, green, blue, alpha, 0, 1, 0);
        quad(consumer, matrix, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, u0, v0, u1, v1, red, green, blue, alpha, 0, -1, 0);
        quad(consumer, matrix, 0, 0, 1, 0, top, 1, 1, top, 1, 1, 0, 1, u0, v0, u1, v1, red, green, blue, alpha, 0, 0, 1);
        quad(consumer, matrix, 1, 0, 0, 1, top, 0, 0, top, 0, 0, 0, 0, u0, v0, u1, v1, red, green, blue, alpha, 0, 0, -1);
        quad(consumer, matrix, 1, 0, 1, 1, top, 1, 1, top, 0, 1, 0, 0, u0, v0, u1, v1, red, green, blue, alpha, 1, 0, 0);
        quad(consumer, matrix, 0, 0, 0, 0, top, 0, 0, top, 1, 0, 0, 1, u0, v0, u1, v1, red, green, blue, alpha, -1, 0, 0);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
            float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
            float u0, float v0, float u1, float v1, int red, int green, int blue, int alpha, float nx, float ny, float nz) {
        vertex(consumer, matrix, x0, y0, z0, u0, v1, red, green, blue, alpha, nx, ny, nz);
        vertex(consumer, matrix, x1, y1, z1, u0, v0, red, green, blue, alpha, nx, ny, nz);
        vertex(consumer, matrix, x2, y2, z2, u1, v0, red, green, blue, alpha, nx, ny, nz);
        vertex(consumer, matrix, x3, y3, z3, u1, v1, red, green, blue, alpha, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
            float u, float v, int red, int green, int blue, int alpha, float nx, float ny, float nz) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }

    private static final class ModelMesh {
        private static final ModelMesh EMPTY = new ModelMesh(List.of());

        private final List<Layer> layers;

        private ModelMesh(List<Layer> layers) {
            this.layers = layers;
        }

        private static ModelMesh build(List<WrightsClientState.Ghost> ghosts) {
            Capture capture = new Capture();
            PoseStack pose = new PoseStack();
            BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
            for (WrightsClientState.Ghost ghost : ghosts) {
                BlockState state = Block.stateById(ghost.stateId());
                if (state.isAir()) {
                    continue;
                }
                pose.pushPose();
                pose.translate(ghost.x(), ghost.y(), ghost.z());
                if (state.getBlock() instanceof LiquidBlock) {
                    renderFluid(pose, capture, state);
                } else if (state.getRenderShape() != RenderShape.INVISIBLE) {
                    renderer.renderSingleBlock(state, pose, capture, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
                pose.popPose();
            }
            List<Layer> opaque = new ArrayList<>();
            List<Layer> clear = new ArrayList<>();
            try {
                for (Map.Entry<RenderType, BufferBuilder> entry : capture.builders.entrySet()) {
                    MeshData mesh = entry.getValue().build();
                    if (mesh == null) {
                        continue;
                    }
                    VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    buffer.bind();
                    try {
                        buffer.upload(mesh);
                    } finally {
                        VertexBuffer.unbind();
                    }
                    Layer layer = new Layer(entry.getKey(), buffer);
                    if (entry.getKey().sortOnUpload()) {
                        clear.add(layer);
                    } else {
                        opaque.add(layer);
                    }
                }
            } catch (RuntimeException ex) {
                for (Layer layer : opaque) {
                    layer.buffer.close();
                }
                for (Layer layer : clear) {
                    layer.buffer.close();
                }
                throw ex;
            } finally {
                for (ByteBufferBuilder bytes : capture.memory.values()) {
                    bytes.close();
                }
            }
            opaque.addAll(clear);
            return new ModelMesh(opaque);
        }

        private void draw(Matrix4f modelView, Matrix4f projection) {
            for (Layer layer : layers) {
                layer.type.setupRenderState();
                ShaderInstance shader = RenderSystem.getShader();
                if (shader != null) {
                    layer.buffer.bind();
                    layer.buffer.drawWithShader(modelView, projection, shader);
                    VertexBuffer.unbind();
                }
                layer.type.clearRenderState();
            }
        }

        private void close() {
            for (Layer layer : layers) {
                layer.buffer.close();
            }
        }
    }

    private static final class Layer {
        private final RenderType type;
        private final VertexBuffer buffer;

        private Layer(RenderType type, VertexBuffer buffer) {
            this.type = type;
            this.buffer = buffer;
        }
    }

    private static final class Capture implements MultiBufferSource {
        private final Map<RenderType, ByteBufferBuilder> memory = new LinkedHashMap<>();
        private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            BufferBuilder builder = builders.get(type);
            if (builder == null) {
                ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(256, type.bufferSize()));
                memory.put(type, bytes);
                builder = new BufferBuilder(bytes, type.mode(), type.format());
                builders.put(type, builder);
            }
            return builder;
        }
    }
}
