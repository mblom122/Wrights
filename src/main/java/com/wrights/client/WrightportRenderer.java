package com.wrights.client;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.block.WrightportLamp;
import com.wrights.drone.Swarm;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Draws the roof iris, the corner lamps and the charge gauge. The tower itself is the block model. */
public class WrightportRenderer implements BlockEntityRenderer<WrightportBlockEntity> {
    public WrightportRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WrightportBlockEntity port, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int roofLight = port.getLevel() == null ? light : LevelRenderer.getLightColor(port.getLevel(), port.getBlockPos().above(2));
        PartModels.renderIris(pose, buffers, PartModels.IRIS_BLADE, port.hatchOpen(partialTick),
                true, 8.0F, 8.0F, 1.0F, 0.035F, roofLight);
        PartModels.render(pose, buffers, PartModels.PORT_LAMPS, PartModels.FULL_BRIGHT, 0xFF000000 | colorOf(port));
        PartModels.renderGauge(pose, buffers, PartModels.PORT_BARS, port.energyBars());
    }

    /** The tower is two blocks tall and a little wider than its block. */
    @Override
    public AABB getRenderBoundingBox(WrightportBlockEntity port) {
        BlockPos pos = port.getBlockPos();
        return new AABB(pos.getX() - 0.1D, pos.getY(), pos.getZ() - 0.1D, pos.getX() + 1.1D, pos.getY() + 2.0D, pos.getZ() + 1.1D);
    }

    /** Red when empty, yellow when every wright is out, green when one is still home. */
    public static int colorOf(WrightportBlockEntity port) {
        Swarm swarm = port.swarm();
        if (swarm.units().isEmpty()) {
            return port.lampColor();
        }
        if (port.energyStored() <= 0) {
            return WrightportLamp.AMBER;
        }
        return WrightportLamp.color(swarm, id -> {
            for (var job : WrightsClientState.jobs) {
                if (job.finished() || job.saving()) {
                    continue;
                }
                if (job.crew().contains(id)) {
                    return true;
                }
            }
            return false;
        });
    }
}
