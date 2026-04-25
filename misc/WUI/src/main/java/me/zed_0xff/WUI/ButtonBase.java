package me.zed_0xff.WUI;

import org.lwjgl.glfw.GLFW;

abstract class ButtonBase extends Label {
    boolean pressed;

    public ButtonBase(Window window, int x, int y, int w, int h, String text) {
        super(window, x, y, w, h, text);
    }

    @Override
    public long cursorAt(int mx, int my) {
        return (enabled && isActiveAt(mx, my)) ? CursorMgr.hand : 0;
    }

    @Override
    public void handleMouseButton(int action, int mx, int my) {
        if (action == GLFW.GLFW_PRESS && isActiveAt(mx, my)) {
            pressed = true;
        } else if (action == GLFW.GLFW_RELEASE) {
            if (pressed && isActiveAt(mx, my)) onClick();
            pressed = false;
        }
    }

    protected void onClick() {}
}
