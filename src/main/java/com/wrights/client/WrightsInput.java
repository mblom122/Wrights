package com.wrights.client;

import org.lwjgl.glfw.GLFW;

import com.wrights.WrightsMod;
import com.wrights.network.BlueprintPayloads;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public final class WrightsInput {
    public static final KeyMapping MENU = new KeyMapping(
            "key.wrights.menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.wrights");

    private static boolean windowRaised;
    private static boolean wasHeld;
    private static int resumeDelay;
    private static boolean paceSent;
    private static boolean boundThisClick;
    private static BlockPos mineWatch;
    private static net.minecraft.world.level.block.state.BlockState mineState;
    private static boolean mineSent;

    private WrightsInput() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(MENU);
    }

    private static void raiseWindow(Minecraft minecraft) {
        if (windowRaised) {
            return;
        }
        long handle = minecraft.getWindow().getWindow();
        if (handle == 0L) {
            return;
        }
        windowRaised = true;
        GLFW.glfwRestoreWindow(handle);
        GLFW.glfwShowWindow(handle);
        GLFW.glfwFocusWindow(handle);
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        WrightsClientState.prepareJoin();
        resumeDelay = 20;
        wasHeld = false;
        paceSent = false;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        raiseWindow(minecraft);
        if (minecraft.player == null) {
            wasHeld = false;
            return;
        }
        if (!paceSent && minecraft.getConnection() != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.wrights.network.BlueprintPayloads.SetPace(com.wrights.WrightsClientConfig.INSTANT_JOBS.get()));
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.wrights.network.BlueprintPayloads.SetPortOrder(com.wrights.WrightsClientConfig.PORT_PLAYER_FIRST.get()));
            paceSent = true;
        }
        boolean held = com.wrights.item.ControllerItem.isHeld(minecraft.player);
        if (held) {
            if (resumeDelay > 0) {
                resumeDelay--;
                if (resumeDelay == 0) {
                    WrightsClientState.tryResume();
                }
            } else if (!wasHeld) {
                WrightsClientState.tryResume();
            }
        }
        wasHeld = held;
        if (!held) {
            WrightsClientState.noteUnequipped();
            // World placement/capture UIs need the tool; the main menu and library edit do not.
            if (minecraft.screen instanceof BlueprintAdjustScreen
                    || minecraft.screen instanceof PlacementScreen) {
                minecraft.setScreen(null);
            } else if (minecraft.screen instanceof BlockPickerScreen picker
                    && (picker.parent() instanceof PlacementScreen || picker.parent() instanceof BlueprintAdjustScreen)) {
                minecraft.setScreen(null);
            }
        }
        boundThisClick = false;
        watchMirrorMine(minecraft);
        WrightsClientState.tickCapture();
        if (held) {
            WrightsClientState.tickFollow();
        }
        while (MENU.consumeClick()) {
            if (minecraft.screen != null && minecraft.screen.getFocused() instanceof EditBox) {
                continue;
            }
            if (minecraft.screen == null) {
                minecraft.setScreen(new WrightsMenuScreen());
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.wrights.network.BlueprintPayloads.LibraryRequest(WrightsClientState.libraryGlobal));
            } else if (minecraft.screen instanceof BlockPickerScreen
                    || minecraft.screen instanceof BlueprintMapScreen
                    || minecraft.screen instanceof BlueprintEditScreen) {
                // Return to the parent edit screen, or discard the edit cleanly.
                minecraft.screen.onClose();
            } else if (minecraft.screen instanceof WrightsMenuScreen
                    || minecraft.screen instanceof BlueprintAdjustScreen
                    || minecraft.screen instanceof PlacementScreen
                    || minecraft.screen instanceof JobActionScreen
                    || minecraft.screen instanceof JobDroneScreen
                    || minecraft.screen instanceof DroneRosterScreen) {
                minecraft.setScreen(null);
            }
        }
    }

    @SubscribeEvent
    public static void onMine(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getEntity() == null || !event.getEntity().getAbilities().instabuild) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.isAir()) {
            sendMirrorMine(event.getPos(), state);
        }
    }

    private static void watchMirrorMine(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || !mirrorMineReady(minecraft)) {
            mineWatch = null;
            mineState = null;
            mineSent = false;
            return;
        }
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && minecraft.options.keyAttack.isDown()) {
            BlockPos pos = hit.getBlockPos();
            net.minecraft.world.level.block.state.BlockState state = minecraft.level.getBlockState(pos);
            if (!state.isAir() && !pos.equals(mineWatch)) {
                mineWatch = pos;
                mineState = state;
                mineSent = false;
            }
        }
        if (mineWatch == null || mineSent || mineState == null || mineState.isAir()) {
            return;
        }
        if (minecraft.level.getBlockState(mineWatch).isAir()) {
            mineSent = true;
            sendMirrorMine(mineWatch, mineState);
        }
    }

    private static boolean mirrorMineReady(Minecraft minecraft) {
        if (!WrightsClientState.mirrorToolActive() || !com.wrights.WrightsClientConfig.mirrorBlocks() || !com.wrights.WrightsClientConfig.mirrorMine()) {
            return false;
        }
        if (!com.wrights.WrightsConfig.allowMirrorMine() || !com.wrights.item.ControllerItem.isHeld(minecraft.player)) {
            return false;
        }
        String dimension = minecraft.level.dimension().location().toString();
        return com.wrights.WrightsClientConfig.mirrorArmed(dimension);
    }

    private static void sendMirrorMine(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !mirrorMineReady(minecraft) || state == null || state.isAir()) {
            return;
        }
        mineWatch = pos;
        mineState = state;
        mineSent = true;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new BlueprintPayloads.MirrorMine(
                pos,
                net.minecraft.world.level.block.Block.getId(state),
                com.wrights.WrightsClientConfig.mirrorAnchor(),
                com.wrights.WrightsClientConfig.MIRROR_FACE.get(),
                com.wrights.WrightsClientConfig.MIRROR_PATTERN.get(),
                com.wrights.WrightsClientConfig.MIRROR_AXES.get(),
                com.wrights.WrightsClientConfig.mirrorMineAny(),
                com.wrights.WrightsClientConfig.mirrorMineVoid()));
    }

    @SubscribeEvent
    public static void onBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (WrightsClientState.bindPort != null && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (event.getEntity().isShiftKeyDown()) {
                WrightsClientState.bindPort = null;
                event.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wrights.port_chest_cancel"), true);
                return;
            }
            if (SelectionRenderer.isSupplyContainer(event.getLevel(), event.getPos())) {
                WrightsClientState.finishPortChest(event.getPos());
            } else {
                event.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wrights.supply_not_container"), true);
            }
            return;
        }
        if (!com.wrights.item.ControllerItem.isHeld(event.getEntity())) {
            return;
        }
        if (Minecraft.getInstance().screen == null && undoChord(event.getEntity())) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            undoLastJob(event.getEntity());
            return;
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.LINK && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            boundThisClick = true;
            com.wrights.storage.DigitalLink.Kind kind = com.wrights.storage.DigitalNetworks.bindKindAt(event.getLevel(), event.getPos());
            if (kind == com.wrights.storage.DigitalLink.Kind.RS) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new BlueprintPayloads.BindDigital(event.getPos(), false));
            } else {
                String key = kind == com.wrights.storage.DigitalLink.Kind.AE2
                        ? "message.wrights.digital_ae2_slot"
                        : "message.wrights.digital_not_target";
                event.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
            }
            return;
        }
        if (com.wrights.blueprint.BlueprintClientFlags.capture) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            WrightsClientState.onCaptureClick(event.getPos(), event.getEntity().isShiftKeyDown());
            return;
        }
        if (com.wrights.blueprint.BlueprintClientFlags.build && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (boundThisClick) {
                return;
            }
            useBuildClick(event.getEntity(), event.getPos(), event.getFace(), event.getHitVec());
            return;
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.JOBS && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            openAimedJob();
            return;
        }
        if (menuMode() && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            openToolMenu();
        }
    }

    @SubscribeEvent
    public static void onItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (WrightsClientState.bindPort != null && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            if (event.getEntity().isShiftKeyDown()) {
                WrightsClientState.bindPort = null;
                event.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wrights.port_chest_cancel"), true);
            }
            return;
        }
        if (!com.wrights.item.ControllerItem.isHeld(event.getEntity())) {
            return;
        }
        if (Minecraft.getInstance().screen == null && undoChord(event.getEntity())) {
            event.setCanceled(true);
            undoLastJob(event.getEntity());
            return;
        }
        if (com.wrights.blueprint.BlueprintClientFlags.capture) {
            event.setCanceled(true);
            if (!(Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                    || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
                WrightsClientState.onCaptureClick(null, event.getEntity().isShiftKeyDown());
            }
            return;
        }
        if (com.wrights.blueprint.BlueprintClientFlags.build && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            if (boundThisClick) {
                return;
            }
            useBuildClick(event.getEntity(), null, null, null);
            return;
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.JOBS && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            if (WrightsClientState.jobAimed() != null) {
                openAimedJob();
            } else {
                openToolMenu();
            }
            return;
        }
        if (menuMode() && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            if (boundThisClick) {
                return;
            }
            if (WrightsClientState.mode == WrightsClientState.Mode.LINK
                    && Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                return;
            }
            openToolMenu();
        }
    }

    private static boolean menuMode() {
        return WrightsClientState.mode == WrightsClientState.Mode.MANAGE
                || WrightsClientState.mode == WrightsClientState.Mode.INFO
                || WrightsClientState.mode == WrightsClientState.Mode.LINK;
    }

    private static void openToolMenu() {
        Minecraft.getInstance().setScreen(new WrightsMenuScreen());
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new BlueprintPayloads.LibraryRequest(WrightsClientState.libraryGlobal));
    }

    private static void useBuildClick(net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face, BlockHitResult hit) {
        if (pos != null) {
            boundThisClick = true;
        }
        if (WrightsClientState.mirrorToolActive()) {
            useMirrorClick(player, pos, face, hit);
            return;
        }
        if (WrightsClientState.shapeToolActive()) {
            useShapeClick(player, pos, face);
            return;
        }
        if (WrightsClientState.buildId == null) {
            Minecraft.getInstance().setScreen(new WrightsMenuScreen());
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new BlueprintPayloads.LibraryRequest(WrightsClientState.libraryGlobal));
            return;
        }
        if (player.isShiftKeyDown()) {
            if (WrightsClientState.bindSupply && WrightsClientState.supplyBound) {
                WrightsClientState.supplyBound = false;
                WrightsClientState.supplyPos = null;
                WrightsClientState.bindFromMenu = false;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.wrights.network.BlueprintPayloads.BindSupply(BlockPos.ZERO, true));
            } else {
                Minecraft.getInstance().setScreen(new PlacementScreen());
            }
            return;
        }
        if (WrightsClientState.bindSupply) {
            if (pos != null && Minecraft.getInstance().level != null && SelectionRenderer.isSupplyContainer(Minecraft.getInstance().level, pos)) {
                WrightsClientState.finishSupplyBind(pos);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wrights.supply_not_container"), true);
            }
            return;
        }
        if (com.wrights.WrightsClientConfig.BUILD_MENU.get()) {
            Minecraft.getInstance().setScreen(new PlacementScreen());
            return;
        }
        WrightsClientState.tryBuild();
    }

    private static void useMirrorClick(net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (pos != null && face != null) {
                WrightsClientState.setMirrorAnchor(pos, face);
                player.displayClientMessage(Component.translatable("message.wrights.mirror_set", Component.translatable(mirrorAxisKey(face.getAxis()))), true);
            } else {
                com.wrights.WrightsClientConfig.clearMirror();
                player.displayClientMessage(Component.translatable("message.wrights.mirror_cleared"), true);
            }
            return;
        }
        if (com.wrights.WrightsClientConfig.mirrorBlocks()) {
            if (!com.wrights.WrightsConfig.allowMirrorBlocks()) {
                player.displayClientMessage(Component.translatable("message.wrights.feature_off"), true);
                return;
            }
            if (pos == null || face == null) {
                player.displayClientMessage(Component.translatable("message.wrights.mirror_look"), true);
                return;
            }
            if (blockHand(player) == null) {
                player.displayClientMessage(Component.translatable("message.wrights.mirror_need_block"), true);
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            String dimension = minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
            boolean armed = com.wrights.WrightsClientConfig.mirrorArmed(dimension);
            Vec3 at = hit == null ? Vec3.atCenterOf(pos).relative(face, 0.5D) : hit.getLocation();
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new BlueprintPayloads.MirrorPlace(
                    pos,
                    face.get3DDataValue(),
                    at.x,
                    at.y,
                    at.z,
                    armed,
                    armed ? com.wrights.WrightsClientConfig.mirrorAnchor() : BlockPos.ZERO,
                    com.wrights.WrightsClientConfig.MIRROR_FACE.get(),
                    com.wrights.WrightsClientConfig.MIRROR_PATTERN.get(),
                    com.wrights.WrightsClientConfig.MIRROR_SHAPES.get(),
                    com.wrights.WrightsClientConfig.MIRROR_ON.get(),
                    com.wrights.WrightsClientConfig.COPY_NBT_BLOCKS.get(),
                    com.wrights.WrightsClientConfig.COPY_NBT_SETTINGS.get(),
                    com.wrights.WrightsClientConfig.COPY_NBT_ALL.get(),
                    com.wrights.WrightsClientConfig.mirrorReplace(),
                    com.wrights.WrightsClientConfig.MIRROR_AXES.get()));
            return;
        }
        if (WrightsClientState.buildId == null) {
            Minecraft.getInstance().setScreen(new WrightsMenuScreen());
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new BlueprintPayloads.LibraryRequest(WrightsClientState.libraryGlobal));
            return;
        }
        WrightsClientState.tryBuild();
    }

    private static InteractionHand blockHand(net.minecraft.world.entity.player.Player player) {
        boolean mainTool = player.getMainHandItem().getItem() instanceof com.wrights.item.ControllerItem;
        boolean offTool = player.getOffhandItem().getItem() instanceof com.wrights.item.ControllerItem;
        if (mainTool && player.getOffhandItem().getItem() instanceof BlockItem) {
            return InteractionHand.OFF_HAND;
        }
        if (offTool && player.getMainHandItem().getItem() instanceof BlockItem) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private static void openAimedJob() {
        BlueprintPayloads.JobLine job = WrightsClientState.jobAimed();
        if (job != null) {
            Minecraft.getInstance().setScreen(new JobActionScreen(job.id()));
        }
    }

    private static boolean undoChord(net.minecraft.world.entity.player.Player player) {
        if (!player.isShiftKeyDown()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /** Most recent finished paste that can still be undone (job list is newest-finished first). */
    private static void undoLastJob(net.minecraft.world.entity.player.Player player) {
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.canUndo() && !job.undoing() && !job.undone()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.UNDO));
                return;
            }
        }
        player.displayClientMessage(Component.translatable("message.wrights.no_undo_job"), true);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!com.wrights.item.ControllerItem.isHeld(minecraft.player)) {
            return;
        }
        if (event.getScrollDeltaY() == 0.0D) {
            return;
        }
        boolean shift = minecraft.player.isShiftKeyDown();
        boolean control = InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (shift && control) {
            WrightsClientState.cycleMainMode(event.getScrollDeltaY() > 0.0D ? -1 : 1);
            event.setCanceled(true);
            return;
        }
        if (control) {
            // Build with a template: Ctrl+scroll rotates 90° (up = clockwise, down = anti-clockwise).
            if (WrightsClientState.mode == WrightsClientState.Mode.BUILD
                    && WrightsClientState.buildId != null
                    && !WrightsClientState.bindSupply
                    && !(WrightsClientState.mirrorToolActive() && com.wrights.WrightsClientConfig.mirrorBlocks())
                    && !WrightsClientState.shapeToolActive()) {
                var op = event.getScrollDeltaY() > 0.0D
                        ? com.wrights.blueprint.BlueprintTransforms.Op.ROTATE
                        : com.wrights.blueprint.BlueprintTransforms.Op.ROTATE_CCW;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new BlueprintPayloads.TransformBlueprint(WrightsClientState.buildId, op.ordinal()));
                event.setCanceled(true);
                return;
            }
            if (WrightsClientState.nudgeBoundary(event.getScrollDeltaY())) {
                event.setCanceled(true);
            }
            return;
        }
        if (!shift) {
            return;
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.CAPTURE
                || WrightsClientState.mode == WrightsClientState.Mode.BUILD) {
            WrightsClientState.cycleBuildTool(event.getScrollDeltaY() > 0.0D ? -1 : 1);
            event.setCanceled(true);
        }
    }

    private static void useShapeClick(net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face) {
        if (!com.wrights.WrightsConfig.allowShape()) {
            player.displayClientMessage(Component.translatable("message.wrights.feature_off"), true);
            return;
        }
        if (player.isShiftKeyDown()) {
            if (pos != null) {
                WrightsClientState.setShapeAnchor(pos);
                player.displayClientMessage(Component.translatable("message.wrights.shape_set", pos.getX(), pos.getY(), pos.getZ()), true);
            } else {
                com.wrights.WrightsClientConfig.clearShapeAnchor();
                player.displayClientMessage(Component.translatable("message.wrights.shape_cleared"), true);
            }
            return;
        }
        WrightsClientState.tryShape(false, pos, face);
    }

    private static String mirrorAxisKey(Direction.Axis axis) {
        return switch (axis) {
            case X -> "menu.wrights.mirror_axis_x";
            case Y -> "menu.wrights.mirror_axis_y";
            case Z -> "menu.wrights.mirror_axis_z";
        };
    }
}
