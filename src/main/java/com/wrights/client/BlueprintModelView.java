package com.wrights.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
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
        // Fit/center from the real ghost AABB so a stale or mismatched sizeX/Y/Z cannot shift
        // the mesh relative to the wireframe (one side clipped, opposite side empty).
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (WrightsClientState.Ghost ghost : ghosts) {
            minX = Math.min(minX, ghost.x());
            minY = Math.min(minY, ghost.y());
            minZ = Math.min(minZ, ghost.z());
            maxX = Math.max(maxX, ghost.x());
            maxY = Math.max(maxY, ghost.y());
            maxZ = Math.max(maxZ, ghost.z());
        }
        // Unit cubes occupy [min, max+1]; fall back to caller sizes only if needed.
        if (minX > maxX) {
            minX = 0;
            maxX = Math.max(0, sizeX - 1);
            minY = 0;
            maxY = Math.max(0, sizeY - 1);
            minZ = 0;
            maxZ = Math.max(0, sizeZ - 1);
        }
        float x0 = minX;
        float y0 = minY;
        float z0 = minZ;
        float x1 = maxX + 1.0F;
        float y1 = maxY + 1.0F;
        float z1 = maxZ + 1.0F;
        float cx = (x0 + x1) * 0.5F;
        float cy = (y0 + y1) * 0.5F;
        float cz = (z0 + z1) * 0.5F;
        float sx = Math.max(1.0F, x1 - x0);
        float sy = Math.max(1.0F, y1 - y0);
        float sz = Math.max(1.0F, z1 - z0);
        // Circumscribed sphere so corners stay inside the panel while spinning.
        float radius = 0.5F * (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        float span = Math.max(sx, Math.max(sy, sz));

        graphics.flush();
        graphics.enableScissor(x, y, x + width, y + height);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width / 2.0D, y + height / 2.0D, 250.0D);
        float scale = Math.min(width, height) * 0.48F / Math.max(0.5F, radius);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-cx, -cy, -cz);

        ModelMesh mesh = meshFor(ghosts);
        // Same matrix for blocks and frame — BufferSource lines were drifting because the
        // modern line shader expands in screen space (and glLineWidth is ignored).
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.enableDepthTest();
        mesh.draw(modelView, projection);
        if (showFrame) {
            // Half the old shader-line look: thin solid edge beams in local block space.
            // Yellow border + blue anchor — same language as world holograms / layer grids.
            float frameThick = Math.max(0.012F, span * 0.004F);
            int place = com.wrights.WrightsClientConfig.placeEdgeRgb();
            float br = com.wrights.WrightsClientConfig.channel(place, 16);
            float bg = com.wrights.WrightsClientConfig.channel(place, 8);
            float bb = com.wrights.WrightsClientConfig.channel(place, 0);
            drawFrameBeams(modelView, projection, x0, y0, z0, x1, y1, z1, frameThick, br, bg, bb, 0.85F);
            int px = Math.max(minX, Math.min(maxX, pivotX));
            int py = Math.max(minY, Math.min(maxY, pivotY));
            int pz = Math.max(minZ, Math.min(maxZ, pivotZ));
            float mx = px + 0.5F;
            float my = py + 0.5F;
            float mz = pz + 0.5F;
            // Fixed pin size — must not track mesh span / Ctrl+scroll yaw.
            float arm = 0.85F;
            float pin = 0.55F;
            float pinThick = Math.max(frameThick, 0.02F);
            float ar = WrightsUi.anchorR();
            float ag = WrightsUi.anchorG();
            float ab = WrightsUi.anchorB();
            RenderSystem.disableDepthTest();
            drawFrameBeams(modelView, projection, mx - arm, my, mz, mx + arm, my, mz, pinThick, ar, ag, ab, 1.0F);
            drawFrameBeams(modelView, projection, mx, my - arm, mz, mx, my + arm, mz, pinThick, ar, ag, ab, 1.0F);
            drawFrameBeams(modelView, projection, mx, my, mz - arm, mx, my, mz + arm, pinThick, ar, ag, ab, 1.0F);
            // Wire cube only in the menu — solid fill is reserved for the world look/anchor tweak glow.
            drawFrameBeams(modelView, projection, mx - pin, my - pin, mz - pin, mx + pin, my + pin, mz + pin, pinThick, ar, ag, ab, 1.0F);
            RenderSystem.enableDepthTest();
        }
        RenderSystem.disableDepthTest();
        pose.popPose();
        graphics.disableScissor();
    }

    /** Twelve AABB edges as thin beams, or a single segment when only one edge is passed. */
    private static void drawFrameBeams(
            Matrix4f modelView,
            Matrix4f projection,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float thick,
            float red,
            float green,
            float blue,
            float alpha) {
        // Full box when all three axes span; otherwise one beam between the two points.
        boolean box = Math.abs(x1 - x0) > 1.0E-4F && Math.abs(y1 - y0) > 1.0E-4F && Math.abs(z1 - z0) > 1.0E-4F;
        ByteBufferBuilder bytes = new ByteBufferBuilder(4096);
        BufferBuilder buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (box) {
            beam(buffer, x0, y0, z0, x1, y0, z0, thick, red, green, blue, alpha);
            beam(buffer, x1, y0, z0, x1, y0, z1, thick, red, green, blue, alpha);
            beam(buffer, x1, y0, z1, x0, y0, z1, thick, red, green, blue, alpha);
            beam(buffer, x0, y0, z1, x0, y0, z0, thick, red, green, blue, alpha);
            beam(buffer, x0, y1, z0, x1, y1, z0, thick, red, green, blue, alpha);
            beam(buffer, x1, y1, z0, x1, y1, z1, thick, red, green, blue, alpha);
            beam(buffer, x1, y1, z1, x0, y1, z1, thick, red, green, blue, alpha);
            beam(buffer, x0, y1, z1, x0, y1, z0, thick, red, green, blue, alpha);
            beam(buffer, x0, y0, z0, x0, y1, z0, thick, red, green, blue, alpha);
            beam(buffer, x1, y0, z0, x1, y1, z0, thick, red, green, blue, alpha);
            beam(buffer, x1, y0, z1, x1, y1, z1, thick, red, green, blue, alpha);
            beam(buffer, x0, y0, z1, x0, y1, z1, thick, red, green, blue, alpha);
        } else {
            beam(buffer, x0, y0, z0, x1, y1, z1, thick, red, green, blue, alpha);
        }
        drawBuilt(buffer, bytes, modelView, projection, RenderType.debugQuads());
    }

    private static void drawBuilt(
            BufferBuilder buffer,
            ByteBufferBuilder bytes,
            Matrix4f modelView,
            Matrix4f projection,
            RenderType type) {
        MeshData mesh = buffer.build();
        if (mesh == null) {
            bytes.close();
            return;
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            vb.bind();
            vb.upload(mesh);
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                vb.drawWithShader(modelView, projection, shader);
            }
            type.clearRenderState();
            VertexBuffer.unbind();
        } finally {
            vb.close();
            bytes.close();
        }
    }

    /** Thin rectangular beam from A to B in local block space. */
    private static void beam(
            BufferBuilder buffer,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float thick,
            float red,
            float green,
            float blue,
            float alpha) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        // Pick a helper axis that is not parallel to the edge (Y fails for verticals).
        float hx;
        float hy;
        float hz;
        if (Math.abs(dy) < 0.9F) {
            hx = 0.0F;
            hy = 1.0F;
            hz = 0.0F;
        } else {
            hx = 1.0F;
            hy = 0.0F;
            hz = 0.0F;
        }
        // Perpendicular pair for the beam cross-section.
        float px = hy * dz - hz * dy;
        float py = hz * dx - hx * dz;
        float pz = hx * dy - hy * dx;
        float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 1.0E-4F) {
            return;
        }
        px = px / pl * thick * 0.5F;
        py = py / pl * thick * 0.5F;
        pz = pz / pl * thick * 0.5F;
        float qx = dy * pz - dz * py;
        float qy = dz * px - dx * pz;
        float qz = dx * py - dy * px;
        // 8 corners of the beam prism.
        float x00 = ax - px - qx;
        float y00 = ay - py - qy;
        float z00 = az - pz - qz;
        float x01 = ax - px + qx;
        float y01 = ay - py + qy;
        float z01 = az - pz + qz;
        float x10 = ax + px - qx;
        float y10 = ay + py - qy;
        float z10 = az + pz - qz;
        float x11 = ax + px + qx;
        float y11 = ay + py + qy;
        float z11 = az + pz + qz;
        float X00 = bx - px - qx;
        float Y00 = by - py - qy;
        float Z00 = bz - pz - qz;
        float X01 = bx - px + qx;
        float Y01 = by - py + qy;
        float Z01 = bz - pz + qz;
        float X10 = bx + px - qx;
        float Y10 = by + py - qy;
        float Z10 = bz + pz - qz;
        float X11 = bx + px + qx;
        float Y11 = by + py + qy;
        float Z11 = bz + pz + qz;
        coloredQuad(buffer, x00, y00, z00, x10, y10, z10, X10, Y10, Z10, X00, Y00, Z00, red, green, blue, alpha);
        coloredQuad(buffer, x01, y01, z01, X01, Y01, Z01, X11, Y11, Z11, x11, y11, z11, red, green, blue, alpha);
        coloredQuad(buffer, x00, y00, z00, X00, Y00, Z00, X01, Y01, Z01, x01, y01, z01, red, green, blue, alpha);
        coloredQuad(buffer, x10, y10, z10, x11, y11, z11, X11, Y11, Z11, X10, Y10, Z10, red, green, blue, alpha);
        coloredQuad(buffer, x00, y00, z00, x01, y01, z01, x11, y11, z11, x10, y10, z10, red, green, blue, alpha);
        coloredQuad(buffer, X00, Y00, Z00, X10, Y10, Z10, X11, Y11, Z11, X01, Y01, Z01, red, green, blue, alpha);
    }

    private static void coloredQuad(
            BufferBuilder buffer,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float red, float green, float blue, float alpha) {
        buffer.addVertex(x0, y0, z0).setColor(red, green, blue, alpha);
        buffer.addVertex(x1, y1, z1).setColor(red, green, blue, alpha);
        buffer.addVertex(x2, y2, z2).setColor(red, green, blue, alpha);
        buffer.addVertex(x3, y3, z3).setColor(red, green, blue, alpha);
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
                float alpha = ghost.a() <= 0.0F ? 1.0F : Math.min(1.0F, ghost.a());
                MultiBufferSource output = capture;
                if (alpha < 0.996F || ghost.r() < 0.996F || ghost.g() < 0.996F || ghost.b() < 0.996F) {
                    int packed = Math.round(alpha * 255.0F);
                    float tr = ghost.r();
                    float tg = ghost.g();
                    float tb = ghost.b();
                    output = type -> new TintConsumer(
                            capture.getBuffer(alpha < 0.996F ? fadeLayer(type) : type),
                            packed, tr, tg, tb);
                }
                pose.pushPose();
                pose.translate(ghost.x(), ghost.y(), ghost.z());
                if (state.getBlock() instanceof LiquidBlock) {
                    renderFluid(pose, output, state);
                } else if (state.getRenderShape() != RenderShape.INVISIBLE) {
                    renderer.renderSingleBlock(state, pose, output, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
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

    /** Same atlas remaps as world holograms so faded menu blocks actually blend. */
    private static RenderType fadeLayer(RenderType type) {
        if (type == RenderType.solid()
                || type == RenderType.cutout()
                || type == RenderType.cutoutMipped()
                || type == RenderType.translucent()
                || type == RenderType.tripwire()
                || type == Sheets.solidBlockSheet()
                || type == Sheets.cutoutBlockSheet()
                || type == Sheets.translucentCullBlockSheet()
                || type == Sheets.translucentItemSheet()
                || type == RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)) {
            return RenderType.translucent();
        }
        if (type == Sheets.chestSheet()) {
            return RenderType.entityTranslucentCull(Sheets.CHEST_SHEET);
        }
        if (type == Sheets.shulkerBoxSheet()) {
            return RenderType.entityTranslucent(Sheets.SHULKER_SHEET);
        }
        return type;
    }

    /** Multiplies baked vertex color by the ghost tint and forces a packed alpha. */
    private static final class TintConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;
        private final float redMul;
        private final float greenMul;
        private final float blueMul;

        private TintConsumer(VertexConsumer delegate, int alpha, float redMul, float greenMul, float blueMul) {
            this.delegate = delegate;
            this.alpha = alpha;
            this.redMul = redMul;
            this.greenMul = greenMul;
            this.blueMul = blueMul;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int ignored) {
            delegate.setColor(
                    Math.min(255, Math.round(red * redMul)),
                    Math.min(255, Math.round(green * greenMul)),
                    Math.min(255, Math.round(blue * blueMul)),
                    alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
