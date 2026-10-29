package com.autobots.client;

import org.lwjgl.glfw.GLFW;

import com.autobots.AutobotsMod;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(modid = AutobotsMod.MOD_ID, value = Dist.CLIENT)
public final class AutobotsInput {
    public static final KeyMapping MENU = new KeyMapping(
            "key.autobots.menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.autobots");

    private static boolean windowRaised;
    private static boolean wasHeld;
    private static int resumeDelay;
    private static boolean paceSent;

    private AutobotsInput() {
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
        AutobotsClientState.prepareJoin();
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
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.autobots.network.BlueprintPayloads.SetPace(com.autobots.AutobotsClientConfig.INSTANT_JOBS.get()));
            paceSent = true;
        }
        boolean held = com.autobots.item.ControllerItem.isHeld(minecraft.player);
        if (held) {
            if (resumeDelay > 0) {
                resumeDelay--;
                if (resumeDelay == 0) {
                    AutobotsClientState.tryResume();
                }
            } else if (!wasHeld) {
                AutobotsClientState.tryResume();
            }
        }
        wasHeld = held;
        if (!held) {
            AutobotsClientState.noteUnequipped();
        }
        AutobotsClientState.tickCapture();
        if (!com.autobots.item.ControllerItem.isHeld(minecraft.player)) {
            if (minecraft.screen instanceof AutobotsMenuScreen
                    || minecraft.screen instanceof BlueprintAdjustScreen
                    || minecraft.screen instanceof PlacementScreen
                    || minecraft.screen instanceof BlueprintEditScreen
                    || minecraft.screen instanceof BlockPickerScreen) {
                minecraft.setScreen(null);
            }
            while (MENU.consumeClick()) {
            }
            return;
        }
        AutobotsClientState.tickFollow();
        while (MENU.consumeClick()) {
            if (minecraft.screen != null && minecraft.screen.getFocused() instanceof EditBox) {
                continue;
            }
            if (minecraft.screen == null) {
                minecraft.setScreen(new AutobotsMenuScreen());
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.autobots.network.BlueprintPayloads.LibraryRequest());
            } else             if (minecraft.screen instanceof AutobotsMenuScreen
                    || minecraft.screen instanceof BlueprintAdjustScreen
                    || minecraft.screen instanceof PlacementScreen
                    || minecraft.screen instanceof BlueprintEditScreen
                    || minecraft.screen instanceof BlockPickerScreen) {
                minecraft.setScreen(null);
            }
        }
    }

    @SubscribeEvent
    public static void onBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!com.autobots.item.ControllerItem.isHeld(event.getEntity())) {
            return;
        }
        if (com.autobots.blueprint.BlueprintClientFlags.capture) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            AutobotsClientState.onCaptureClick(event.getPos(), event.getEntity().isShiftKeyDown());
            return;
        }
        if (com.autobots.blueprint.BlueprintClientFlags.build && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (event.getEntity().isShiftKeyDown()) {
                if (AutobotsClientState.bindSupply && AutobotsClientState.supplyBound) {
                    AutobotsClientState.supplyBound = false;
                    AutobotsClientState.supplyPos = null;
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.autobots.network.BlueprintPayloads.BindSupply(BlockPos.ZERO, true));
                } else {
                    Minecraft.getInstance().setScreen(new PlacementScreen());
                }
            } else if (AutobotsClientState.bindSupply) {
                BlockPos pos = event.getPos();
                if (Minecraft.getInstance().level != null && SelectionRenderer.isSupplyContainer(Minecraft.getInstance().level, pos)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.autobots.network.BlueprintPayloads.BindSupply(pos, false));
                } else {
                    event.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.autobots.supply_not_container"), true);
                }
            } else {
                AutobotsClientState.tryBuild();
            }
        }
    }

    @SubscribeEvent
    public static void onItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!com.autobots.item.ControllerItem.isHeld(event.getEntity())) {
            return;
        }
        if (com.autobots.blueprint.BlueprintClientFlags.capture) {
            event.setCanceled(true);
            if (!(Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                    || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
                AutobotsClientState.onCaptureClick(null, event.getEntity().isShiftKeyDown());
            }
            return;
        }
        if (com.autobots.blueprint.BlueprintClientFlags.build && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
            if (event.getEntity().isShiftKeyDown()) {
                if (AutobotsClientState.bindSupply && AutobotsClientState.supplyBound) {
                    AutobotsClientState.supplyBound = false;
                    AutobotsClientState.supplyPos = null;
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.autobots.network.BlueprintPayloads.BindSupply(BlockPos.ZERO, true));
                } else {
                    Minecraft.getInstance().setScreen(new PlacementScreen());
                }
            } else if (AutobotsClientState.bindSupply) {
                event.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.autobots.supply_not_container"), true);
            } else {
                AutobotsClientState.tryBuild();
            }
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!com.autobots.item.ControllerItem.isHeld(minecraft.player)) {
            return;
        }
        if (!minecraft.player.isShiftKeyDown() || event.getScrollDeltaY() == 0.0D) {
            return;
        }
        if (AutobotsClientState.mode == AutobotsClientState.Mode.CAPTURE) {
            AutobotsClientState.cycleStyle(event.getScrollDeltaY() > 0.0D ? -1 : 1);
            event.setCanceled(true);
        } else if (AutobotsClientState.mode == AutobotsClientState.Mode.BUILD) {
            AutobotsClientState.cycleBuildTool();
            event.setCanceled(true);
        }
    }
}
