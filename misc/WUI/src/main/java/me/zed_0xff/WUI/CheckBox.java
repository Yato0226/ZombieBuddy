package me.zed_0xff.WUI;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

class CheckBox extends Label {
    static final Atlas atlas = new Atlas("checkbox");
    private static int tex;

    boolean checked;
    private boolean pressed;

    public CheckBox(int x, int y, int w, int h, String text) {
        super(x, y, w, h, text);
    }

    @Override
    public void handleMouseButton(int action, int mx, int my) {
        boolean hit = mx >= x && mx < x + width && my >= y && my < y + height;
        if (action == GLFW.GLFW_PRESS && hit) {
            pressed = true;
        } else if (action == GLFW.GLFW_RELEASE) {
            if (pressed && hit) checked = !checked;
            pressed = false;
        }
    }

    @Override
    public void render(int fontTex, int originX, int originY) {
        int bx = originX + x;
        int by = originY + y;

        if (atlas.isLoaded()) {
            if (tex == 0) tex = atlas.uploadTexture();
            String tileName = checked ? (pressed ? "checkedClicked" : "checked")
                                      : (pressed ? "clicked"        : "default");
            Atlas.TileJson t = atlas.tiles.get(tileName);
            if (t != null && tex != 0) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
                GL11.glColor3f(1f, 1f, 1f);
                float u0 = t.x / (float) atlas.w, v0 = t.y / (float) atlas.h;
                float u1 = (t.x + t.w) / (float) atlas.w, v1 = (t.y + t.h) / (float) atlas.h;
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(u0, v0); GL11.glVertex2f(bx,        by);
                GL11.glTexCoord2f(u1, v0); GL11.glVertex2f(bx + t.w,  by);
                GL11.glTexCoord2f(u1, v1); GL11.glVertex2f(bx + t.w,  by + t.h);
                GL11.glTexCoord2f(u0, v1); GL11.glVertex2f(bx,        by + t.h);
                GL11.glEnd();
            }
        } else {
            // fallback: draw a simple square
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            Element.outlineRect(bx, by, height, height, 1, Color.BLACK);
            if (checked) {
                Element.fillRect(bx + 3, by + 3, height - 6, height - 6, textColor);
            }
        }

        if (text != null && !text.isEmpty()) {
            int textX = atlas.getMetaInt("textX", height + 2);
            int textY = atlas.getMetaInt("textY", 0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fontTex);
            glColor(textColor);
            font.drawText(bx + textX, by + textY, text);
        }
    }

}
