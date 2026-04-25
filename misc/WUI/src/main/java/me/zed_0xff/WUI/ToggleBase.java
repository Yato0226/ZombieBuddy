package me.zed_0xff.WUI;

import org.lwjgl.opengl.GL11;

abstract class ToggleBase extends ButtonBase {
    boolean checked;

    public ToggleBase(Window window, int x, int y, int w, int h, String text) {
        super(window, x, y, w, h, text);
    }

    protected abstract Atlas getAtlas();

    /** Draw the icon when the atlas is not loaded. */
    protected abstract void renderFallback(int bx, int by);

    @Override
    protected void onClick() { checked = !checked; }

    @Override
    protected boolean isActiveAt(int mx, int my) {
        if (mx < x || my < y || my >= y + height) return false;
        int iconW = getAtlas().getMetaInt("textX", height + 2);
        int textW = (text != null && !text.isEmpty()) ? font.measureTextAdvancePx(text) : 0;
        return mx < x + iconW + textW;
    }

    @Override
    public void render(int fontTex, int originX, int originY) {
        Atlas a = getAtlas();
        int bx = originX + x;
        int by = originY + y;

        if (a.isLoaded()) {
            if (a.texId == 0) a.texId = a.uploadTexture();
            String tileName = checked ? (pressed ? "checkedClicked" : "checked")
                                      : (pressed ? "clicked"        : "default");
            Atlas.TileJson t = a.tiles.get(tileName);
            if (t != null && a.texId != 0) {
                withTexture(a.texId, () -> {
                    GL11.glColor3f(1f, 1f, 1f);
                    float u0 = t.x / (float) a.w, v0 = t.y / (float) a.h;
                    float u1 = (t.x + t.w) / (float) a.w, v1 = (t.y + t.h) / (float) a.h;
                    drawTexRect(bx, by, t.w, t.h, u0, v0, u1, v1);
                });
            }
        } else {
            renderFallback(bx, by);
        }

        if (text != null && !text.isEmpty()) {
            int textX = a.getMetaInt("textX", height + 2);
            int textY = a.getMetaInt("textY", 0);
            withTexture(fontTex, () -> {
                glColor(textColor);
                font.drawText(bx + textX, by + textY, text);
            });
        }
    }
}
