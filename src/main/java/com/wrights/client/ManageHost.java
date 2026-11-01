package com.wrights.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;

import com.wrights.drone.Swarm;

/** Screen that can show {@link WrightManagePanel} over a personal wrightport. */
interface ManageHost {
    int screenWidth();

    int screenHeight();

    Font font();

    Minecraft minecraft();

    Swarm swarm();

    boolean away(UUID id);

    void edit(int action, int index);

    void rename(int unit, String name);

    void batch(int action, int value, int[] units);

    void addButton(WrightsButton button);

    void addField(EditBox box);

    void focus(GuiEventListener listener);

    void relayout();

    /** Drawn inside an existing window instead of as its own overlay. */
    boolean embedded();

    int areaX();

    int areaY();

    int areaW();

    int areaH();

    /** True when swarm edits are allowed (controller held, or this UI does not require one). */
    boolean toolHeld();

    /** Like {@link #toolHeld()}, but may flash a “need controller” hint when false. */
    boolean requireTool();
}
