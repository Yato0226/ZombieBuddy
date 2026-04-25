package me.zed_0xff.WUI;

abstract class Control extends Element {
    public boolean enabled;

    public Control(int x, int y, int w, int h){
        super(x, y, w, h);
    }

    /** Render this control at (originX + x, originY + y). */
    public abstract void render(int fontTex, int originX, int originY);
}
