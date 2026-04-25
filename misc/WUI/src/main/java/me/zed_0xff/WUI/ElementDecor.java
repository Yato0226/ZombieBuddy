package me.zed_0xff.WUI;

import org.lwjgl.opengl.GL11;

/**
 * 9-slice decoration — "cap corners" layout: corners keep their own size,
 * side thickness comes from the middle slices.
 */
final class ElementDecor {
    private final String name;
    private int texture, atlasW, atlasH;

    private Atlas.TileJson topLeft, topCenter, topRight;
    private Atlas.TileJson middleLeft, middleCenter, middleRight;
    private Atlas.TileJson bottomLeft, bottomCenter, bottomRight;

    private int leftW, rightW, topH, bottomH;
    private int topLeftW, topRightW, bottomLeftW, bottomRightW;

    public int textX = 0;
    public int textY = 0;

    public ElementDecor(String name) { this.name = name; loadFromJson(name); }

    public boolean isLoaded() { return texture != 0; }

    public void dispose() {
        if (texture != 0) { GL11.glDeleteTextures(texture); texture = 0; }
    }

    public Rect contentRect(int x, int y, int w, int h) {
        return new Rect(x + leftW, y + topH, Math.max(0, w - leftW - rightW), Math.max(0, h - topH - bottomH));
    }

    /** Draw decor + fill content; fallback: fill + black outline. */
    public void render(int x, int y, int w, int h, Color fill) {
        if (isLoaded()) {
            renderInternal(x, y, w, h);
            Element.fillRect(contentRect(x, y, w, h), fill);
        } else {
            Element.fillRect(x, y, w, h, fill);
            Element.outlineRect(x, y, w, h, 1, Color.BLACK);
        }
    }

    void renderInternal(int x, int y, int w, int h) {
        if (texture == 0) return;
        int innerW  = Math.max(0, w - leftW - rightW);
        int innerH  = Math.max(0, h - topH - bottomH);
        int topMidW = Math.max(0, w - topLeftW - topRightW);
        int botMidW = Math.max(0, w - bottomLeftW - bottomRightW);

        Element.withTexture(texture, () -> {
            GL11.glColor3f(1f, 1f, 1f);
            blit(topLeft,      x,                    y,                topLeftW,    topH);
            blit(topCenter,    x + topLeftW,         y,                topMidW,     topH);
            blit(topRight,     x + w - topRightW,    y,                topRightW,   topH);
            blit(middleLeft,   x,                    y + topH,         leftW,       innerH);
            blit(middleCenter, x + leftW,            y + topH,         innerW,      innerH);
            blit(middleRight,  x + w - rightW,       y + topH,         rightW,      innerH);
            blit(bottomLeft,   x,                    y + h - bottomH,  bottomLeftW,  bottomH);
            blit(bottomCenter, x + bottomLeftW,      y + h - bottomH,  botMidW,      bottomH);
            blit(bottomRight,  x + w - bottomRightW, y + h - bottomH,  bottomRightW, bottomH);
        });
    }

    private void loadFromJson(String name) {
        Atlas atlas = new Atlas(name);
        if (!atlas.isLoaded() || atlas.tiles == null) { warn("atlas load failed"); return; }

        String[] keys = {
            "topLeft",   "topCenter",   "topRight",
            "middleLeft","middleCenter","middleRight",
            "bottomLeft","bottomCenter","bottomRight"
        };
        for (String k : keys) {
            Atlas.TileJson t = atlas.tiles.get(k);
            if (t == null || !atlas.fits(t)) { warn("bad tile: " + k); return; }
        }

        texture = atlas.uploadTexture();
        if (texture == 0) { warn("texture upload failed"); return; }
        atlasW = atlas.w; atlasH = atlas.h;

        topLeft    = atlas.tiles.get("topLeft"); topCenter = atlas.tiles.get("topCenter"); topRight = atlas.tiles.get("topRight");
        middleLeft = atlas.tiles.get("middleLeft"); middleCenter = atlas.tiles.get("middleCenter"); middleRight = atlas.tiles.get("middleRight");
        bottomLeft = atlas.tiles.get("bottomLeft"); bottomCenter = atlas.tiles.get("bottomCenter"); bottomRight = atlas.tiles.get("bottomRight");

        leftW = middleLeft.w; rightW = middleRight.w; topH = topCenter.h; bottomH = bottomCenter.h;
        topLeftW = topLeft.w; topRightW = topRight.w; bottomLeftW = bottomLeft.w; bottomRightW = bottomRight.w;

        textX = atlas.getMetaInt("textX", textX);
        textY = atlas.getMetaInt("textY", textY);
    }

    private void blit(Atlas.TileJson r, int sx, int sy, int sw, int sh) {
        if (sw <= 0 || sh <= 0) return;
        float u0 = r.x / (float) atlasW, v0 = r.y / (float) atlasH;
        float u1 = (r.x + r.w) / (float) atlasW, v1 = (r.y + r.h) / (float) atlasH;
        Element.drawTexRect(sx, sy, sw, sh, u0, v0, u1, v1);
    }

    private void warn(String msg) { System.err.println("ElementDecor(" + name + "): " + msg); }
}
