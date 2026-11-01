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
            WrightsClientState.bridgeActive = false;
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
            WrightsClientState.tickBridge();
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
                    || minecraft.screen instanceof BlueprintEditScreen
                    || minecraft.screen instanceof ShapePivotScreen
                    || minecraft.screen instanceof ShapeAnchorScreen) {
                // Return to the parent edit screen, or discard the edit cleanly.
                minecraft.screen.onClose();
            } else if (minecraft.screen instanceof WrightsMenuScreen
                    || minecraft.screen instanceof BlueprintAdjustScreen
                    || minecraft.screen instanceof PlacementScreen
                    || minecraft.screen instanceof JobActionScreen
                    || minecraft.screen instanceof JobDroneScreen
                    || minecraft.screen instanceof DroneRosterScreen
                    || minecraft.screen instanceof ShapeLookSnapScreen
                    || minecraft.screen instanceof ShapeTweakScreen
                    || minecraft.screen instanceof PlaceTweakScreen) {
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
            if (menuChord(event.getEntity())) {
                openToolMenu();
                return;
            }
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
            // Ctrl+Shift = capture tweak menu / Building page. Shift alone = second corner etc.
            if (menuChord(event.getEntity())) {
                openCaptureMenu();
                return;
            }
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
            if (menuChord(event.getEntity())) {
                openToolMenu();
            } else {
                openAimedJob();
            }
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
            if (menuChord(event.getEntity())) {
                openCaptureMenu();
                return;
            }
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
            if (menuChord(event.getEntity())) {
                openToolMenu();
            } else if (WrightsClientState.jobAimed() != null) {
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
                    && !menuChord(event.getEntity())
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

    /**
     * Shared controller chords:
     * Ctrl+right-click = set / clear that mode’s anchor,
     * Shift+right-click = mode special action (second corner, Shape/Exchange size tweak, Bind menu, …),
     * Middle-click = pick the aimed block as Shape / Exchange material (like creative pick),
     * Ctrl+Shift+right-click = that mode’s options page (Shape / Exchange / Mirror / …),
     * Alt+right-click = undo last finished build.
     */

    /**
     * Middle mouse / pick-block while the controller is the main-hand tool.
     * Always cancels vanilla creative pick so it cannot replace the controller,
     * then (in Shape / Exchange) sets the aimed block as the material.
     */
    @SubscribeEvent
    public static void onPickBlock(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isPickBlock()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        // Creative pick writes into the selected hotbar slot — only steal when that slot is the controller.
        if (!(minecraft.player.getMainHandItem().getItem() instanceof com.wrights.item.ControllerItem)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (WrightsClientState.exchangeToolActive() && com.wrights.WrightsConfig.allowExchange()) {
            pickMaterialBlock(minecraft.player, true);
            return;
        }
        if (WrightsClientState.placeToolActive() && com.wrights.WrightsConfig.allowPlaceTool()) {
            pickMaterialBlock(minecraft.player, false);
            return;
        }
        if (WrightsClientState.shapeToolActive() && com.wrights.WrightsConfig.allowShape()) {
            pickMaterialBlock(minecraft.player, false);
        }
    }
    private static boolean controlHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean altHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static boolean menuChord(net.minecraft.world.entity.player.Player player) {
        return player.isShiftKeyDown() && controlHeld();
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
        if (WrightsClientState.exchangeToolActive()) {
            useExchangeClick(player, pos, face);
            return;
        }
        if (WrightsClientState.placeToolActive()) {
            usePlaceClick(player, pos, face);
            return;
        }
        if (WrightsClientState.bindSupply) {
            // Ctrl+Shift = bind options (same chord as Shape / Exchange).
            // Shift = bind tweak menu (teleport / highlight / clear).
            if (menuChord(player) || player.isShiftKeyDown()) {
                Minecraft.getInstance().setScreen(new SupplyBindScreen());
                return;
            }
            if (pos == null) {
                WrightsClientState.clearSupplyBind();
                return;
            }
            if (Minecraft.getInstance().level != null && SelectionRenderer.isSupplyContainer(Minecraft.getInstance().level, pos)) {
                WrightsClientState.finishSupplyBind(pos);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wrights.supply_not_container"), true);
            }
            return;
        }
        if (WrightsClientState.buildId == null) {
            openToolMenu();
            return;
        }
        // Ctrl+Shift = place / tweak menu.
        if (menuChord(player)) {
            Minecraft.getInstance().setScreen(new PlacementScreen());
            return;
        }
        // Ctrl = pin the build anchor where you look (stop follow).
        if (controlHeld()) {
            pinBuildAnchor(player, pos);
            return;
        }
        if (com.wrights.WrightsClientConfig.BUILD_MENU.get()) {
            Minecraft.getInstance().setScreen(new PlacementScreen());
            return;
        }
        WrightsClientState.tryBuild();
    }

    private static void pinBuildAnchor(net.minecraft.world.entity.player.Player player, BlockPos clicked) {
        BlockPos target = WrightsClientState.lookTarget();
        if (target == null) {
            target = clicked;
        }
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.wrights.build_anchor_look"), true);
            return;
        }
        WrightsClientState.follow = false;
        WrightsClientState.anchor = target.immutable();
        player.displayClientMessage(
                Component.translatable("message.wrights.build_anchor_set", target.getX(), target.getY(), target.getZ()),
                true);
    }

    private static void useMirrorClick(net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face, BlockHitResult hit) {
        // Ctrl+Shift = Mirror page. Ctrl = set / clear mirror anchor.
        if (menuChord(player)) {
            openToolMenu();
            return;
        }
        if (controlHeld()) {
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
                    com.wrights.WrightsClientConfig.MIRROR_AXES.get(),
                    com.wrights.blueprint.ShapeSupply.encode(com.wrights.WrightsClientConfig.shapeSupplyOrder())));
            return;
        }
        if (WrightsClientState.buildId == null) {
            openToolMenu();
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

    /** Alt+right-click undoes the last finished build (Ctrl+Shift is the mode menu). */
    private static boolean undoChord(net.minecraft.world.entity.player.Player player) {
        return altHeld() && !player.isShiftKeyDown() && !controlHeld();
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
            // Place Bridge: Ctrl+scroll adjusts how far ahead the path builds (1–12).
            if (WrightsClientState.placeToolActive()
                    && com.wrights.WrightsConfig.allowPlaceTool()
                    && com.wrights.WrightsClientConfig.placePathStyle()) {
                int step = event.getScrollDeltaY() > 0.0D ? 1 : -1;
                com.wrights.WrightsClientConfig.setPlaceBridgeOffset(
                        com.wrights.WrightsClientConfig.placeBridgeOffset() + step);
                event.setCanceled(true);
                return;
            }
            // Exchange / Place Look·To me: Ctrl+scroll grows Size (Pillar height / Beam length).
            if ((WrightsClientState.exchangeToolActive() && com.wrights.WrightsConfig.allowExchange())
                    || (WrightsClientState.placeToolActive() && com.wrights.WrightsConfig.allowPlaceTool())) {
                int step = event.getScrollDeltaY() > 0.0D ? 1 : -1;
                com.wrights.blueprint.Shapes.ExchangeForm form = com.wrights.WrightsClientConfig.exchangeForm();
                if (form.line()) {
                    com.wrights.WrightsClientConfig.setExchangeLineLength(
                            com.wrights.WrightsClientConfig.exchangeLineLength() + step);
                    com.wrights.WrightsClientConfig.enforceExchangeLineFootprint();
                } else {
                    com.wrights.WrightsClientConfig.setShapeSize(
                            com.wrights.WrightsClientConfig.SHAPE_SIZE.get() + step);
                }
                event.setCanceled(true);
                return;
            }
            // Shape tool: Ctrl+scroll yaws the whole footprint around the look/anchor pin.
            // Scroll up = clockwise so the AABB orbits that cell (needs a corner/edge pivot).
            if (WrightsClientState.shapeToolActive() && com.wrights.WrightsConfig.allowShape()) {
                // Center pivot spins a cube in place — move to the bottom min corner so scroll orbits the box.
                if (com.wrights.WrightsClientConfig.SHAPE_PIVOT.get() == com.wrights.blueprint.Shapes.PIVOT_CENTER) {
                    com.wrights.WrightsClientConfig.setShapePivot(com.wrights.blueprint.Shapes.pivotId(0, 0));
                }
                int yaw = com.wrights.WrightsClientConfig.SHAPE_YAW.get();
                int nextYaw = event.getScrollDeltaY() > 0.0D
                        ? (yaw + 3) & 3
                        : (yaw + 1) & 3;
                com.wrights.WrightsClientConfig.setShapeYaw(nextYaw);
                event.setCanceled(true);
                return;
            }
            // Build with a template: Ctrl+scroll rotates 90° (up = clockwise, down = anti-clockwise).
            if (WrightsClientState.mode == WrightsClientState.Mode.BUILD
                    && WrightsClientState.buildId != null
                    && !WrightsClientState.bindSupply
                    && !(WrightsClientState.mirrorToolActive() && com.wrights.WrightsClientConfig.mirrorBlocks())) {
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

    private static void openCaptureMenu() {
        if (WrightsClientState.hasBox()) {
            Minecraft.getInstance().setScreen(new BlueprintAdjustScreen());
            return;
        }
        openToolMenu();
    }

    private static void useShapeClick(net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face) {
        if (!com.wrights.WrightsConfig.allowShape()) {
            player.displayClientMessage(Component.translatable("message.wrights.feature_off"), true);
            return;
        }
        // Ctrl+Shift = Shape options page (same chord as Exchange / Mirror).
        if (menuChord(player)) {
            openToolMenu();
            return;
        }
        // Ctrl = set world anchor (and switch Look → On anchor). Air clears + Look mode.
        if (controlHeld()) {
            setShapeWorldAnchor(player, pos);
            return;
        }
        // Shift+air = clear anchor and switch to Where I look.
        // Shift+block = look-snap / world-anchor tweak menu.
        if (player.isShiftKeyDown()) {
            if (aimingAtAir()) {
                clearShapeAnchorToLook(player);
                return;
            }
            openShapeTweakMenu(false);
            return;
        }
        boolean lookMode = !com.wrights.WrightsClientConfig.SHAPE_USE_ANCHOR.get()
                || !com.wrights.WrightsConfig.allowShapeAnchor();
        if (lookMode) {
            boolean lockFirst = com.wrights.WrightsClientConfig.SHAPE_LOOK_LOCK.get();
            if (lockFirst && !WrightsClientState.shapeLookSnapActive()) {
                BlockHitResult aim = WrightsClientState.shapeLookHit();
                if (aim == null) {
                    player.displayClientMessage(Component.translatable("message.wrights.shape_look"), true);
                    return;
                }
                WrightsClientState.lockShapeLookSnap(aim.getBlockPos(), aim.getDirection());
                BlockPos pin = WrightsClientState.shapeLookSnapPos;
                player.displayClientMessage(
                        Component.translatable("message.wrights.shape_look_locked", pin.getX(), pin.getY(), pin.getZ()),
                        true);
                return;
            }
            if (WrightsClientState.shapeLookSnapActive()) {
                WrightsClientState.tryShape(false, WrightsClientState.shapeLookSnapPos, Direction.UP);
            } else {
                BlockHitResult aim = WrightsClientState.shapeLookHit();
                if (aim == null) {
                    player.displayClientMessage(Component.translatable("message.wrights.shape_look"), true);
                    return;
                }
                WrightsClientState.tryShape(false, aim.getBlockPos(), aim.getDirection());
            }
            return;
        }
        WrightsClientState.tryShape(false, pos, face);
    }

    /** True when the crosshair is not on a block (Shift+air clear uses this). */
    private static boolean aimingAtAir() {
        BlockHitResult aim = WrightsClientState.lookHit();
        return aim == null || aim.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    /** Clear the shared world anchor and switch Shape / Exchange back to Where I look. */
    private static void clearShapeAnchorToLook(net.minecraft.world.entity.player.Player player) {
        com.wrights.WrightsClientConfig.clearShapeAnchor();
        com.wrights.WrightsClientConfig.setShapeUseAnchor(false);
        WrightsClientState.clearShapeLookSnap();
        player.displayClientMessage(Component.translatable("message.wrights.shape_look_mode"), true);
    }

    /** Ctrl+right-click: pin the shared shape/exchange world anchor and prefer On-anchor place. */
    private static void setShapeWorldAnchor(net.minecraft.world.entity.player.Player player, BlockPos clicked) {
        if (!com.wrights.WrightsConfig.allowShapeAnchor()) {
            player.displayClientMessage(Component.translatable("message.wrights.feature_off"), true);
            return;
        }
        BlockHitResult aim = WrightsClientState.lookHit();
        BlockPos at = aim != null && aim.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? aim.getBlockPos()
                : null;
        if (at == null) {
            at = clicked;
        }
        if (at != null) {
            WrightsClientState.setShapeAnchor(at);
            com.wrights.WrightsClientConfig.setShapeUseAnchor(true);
            WrightsClientState.clearShapeLookSnap();
            player.displayClientMessage(
                    Component.translatable("message.wrights.shape_set", at.getX(), at.getY(), at.getZ()),
                    true);
            return;
        }
        clearShapeAnchorToLook(player);
    }

    /** Shift+block: compact translucent size / aim tweak (Style matches look-snap). */
    private static void openShapeTweakMenu(boolean exchange) {
        Minecraft.getInstance().setScreen(new ShapeTweakScreen(exchange));
    }

    /**
     * Place tool: Look builds at aim, To me under feet, Bridge toggles a walking path.
     * Shift+RMB opens the quick tweak (style + bridge offset). Ctrl+Shift opens the Place page.
     */
    private static void usePlaceClick(net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face) {
        if (!com.wrights.WrightsConfig.allowPlaceTool()) {
            player.displayClientMessage(Component.translatable("message.wrights.feature_off"), true);
            return;
        }
        if (menuChord(player)) {
            openToolMenu();
            return;
        }
        if (player.isShiftKeyDown()) {
            Minecraft.getInstance().setScreen(new PlaceTweakScreen());
            return;
        }
        WrightsClientState.tryPlace(false, pos, face);
    }

    /** Same look/anchor flow as Shape. Middle-click picks Replace with. Ctrl+Shift opens Exchange options. */
    private static void useExchangeClick(net.minecraft.world.entity.player.Player player, BlockPos pos, Direction face) {
        if (!com.wrights.WrightsConfig.allowExchange()) {
            player.displayClientMessage(Component.translatable("message.wrights.feature_off"), true);
            return;
        }
        if (menuChord(player)) {
            openToolMenu();
            return;
        }
        if (controlHeld()) {
            setShapeWorldAnchor(player, pos);
            return;
        }
        // Shift+air = clear anchor and switch to Where I look (same as Shape).
        // Shift+block = look-snap / world-anchor tweak menu.
        if (player.isShiftKeyDown()) {
            if (aimingAtAir()) {
                clearShapeAnchorToLook(player);
                return;
            }
            openShapeTweakMenu(true);
            return;
        }
        boolean lookMode = !com.wrights.WrightsClientConfig.SHAPE_USE_ANCHOR.get()
                || !com.wrights.WrightsConfig.allowShapeAnchor();
        if (lookMode) {
            // Exchange never uses Lock before build — follow look, or a snap set via Edit point.
            if (WrightsClientState.shapeLookSnapActive()) {
                WrightsClientState.tryExchange(false, WrightsClientState.shapeLookSnapPos, Direction.UP);
            } else {
                BlockHitResult aim = WrightsClientState.shapeLookHit();
                if (aim == null) {
                    player.displayClientMessage(Component.translatable("message.wrights.shape_look"), true);
                    return;
                }
                WrightsClientState.tryExchange(false, aim.getBlockPos(), aim.getDirection());
            }
            return;
        }
        WrightsClientState.tryExchange(false, pos, face);
    }

    /** Middle-click: set Shape materials or Exchange Replace with to the aimed block. */
    private static void pickMaterialBlock(net.minecraft.world.entity.player.Player player, boolean exchange) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockHitResult aim = WrightsClientState.lookHit();
        if (aim == null || minecraft.level == null) {
            player.displayClientMessage(Component.translatable(
                    exchange ? "message.wrights.exchange_to_look" : "message.wrights.shape_material_look"), true);
            return;
        }
        BlockPos at = aim.getBlockPos();
        net.minecraft.world.level.block.state.BlockState state = minecraft.level.getBlockState(at);
        if (!com.wrights.util.BlockFilters.canShapeMaterial(state)) {
            player.displayClientMessage(Component.translatable(
                    exchange ? "message.wrights.exchange_to_blocked" : "message.wrights.shape_material_blocked"), true);
            return;
        }
        net.minecraft.world.item.Item item = state.getBlock().asItem();
        if (!(item instanceof BlockItem)) {
            player.displayClientMessage(Component.translatable(
                    exchange ? "message.wrights.exchange_to_blocked" : "message.wrights.shape_material_blocked"), true);
            return;
        }
        com.wrights.WrightsClientConfig.setShapeMaterials(
                java.util.List.of(new com.wrights.blueprint.ShapeMaterials.Entry(item, 1)));
        if (exchange) {
            com.wrights.WrightsClientConfig.setExchangeFrom(java.util.List.of());
        }
        player.displayClientMessage(
                Component.translatable(
                        exchange ? "message.wrights.exchange_to_set" : "message.wrights.shape_material_set",
                        state.getBlock().getName()),
                true);
    }

    private static String mirrorAxisKey(Direction.Axis axis) {
        return switch (axis) {
            case X -> "menu.wrights.mirror_axis_x";
            case Y -> "menu.wrights.mirror_axis_y";
            case Z -> "menu.wrights.mirror_axis_z";
        };
    }
}
