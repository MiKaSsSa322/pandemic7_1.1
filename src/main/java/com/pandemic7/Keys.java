package com.pandemic7;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    private Keys() {}

    public static final KeyMapping TOGGLE_LIST =
            new KeyMapping("key.pandemic7.toggle_list", GLFW.GLFW_KEY_H, "key.categories.pandemic7");
    public static final KeyMapping TOGGLE_HUD =
            new KeyMapping("key.pandemic7.toggle_hud", GLFW.GLFW_KEY_J, "key.categories.pandemic7");
}
