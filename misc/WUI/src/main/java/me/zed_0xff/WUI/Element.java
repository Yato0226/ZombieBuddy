package me.zed_0xff.WUI;

import org.lwjgl.opengl.GL11;

abstract class Element {
    public int x, y, width, height;

    static Font font = new Font();

    public Element(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
    }

    static void glColor(Color color){
        int c = color.getInt();
        GL11.glColor3ub((byte)((c>>16) & 0xff), (byte)((c>>8) & 0xff), (byte)(c & 0xff));
    }

    static void fillRect(int x0, int y0, int w, int h, Color color) {
        glColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x0 + w, y0);
        GL11.glVertex2f(x0 + w, y0 + h);
        GL11.glVertex2f(x0, y0 + h);
        GL11.glEnd();
    }

    static void fillRect(Rect r, Color color) {
        if (r == null) {
            return;
        }
        fillRect(r.x(), r.y(), r.w(), r.h(), color);
    }

    static void outlineRect(int x0, int y0, int w, int h, int lineWidth, Color color) {
        glColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x0 + w, y0);
        GL11.glVertex2f(x0 + w, y0 + h);
        GL11.glVertex2f(x0, y0 + h);
        GL11.glEnd();
    }

    static void drawTexRect(int sx, int sy, int sw, int sh, float u0, float v0, float u1, float v1) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0); GL11.glVertex2f(sx,      sy);
        GL11.glTexCoord2f(u1, v0); GL11.glVertex2f(sx + sw, sy);
        GL11.glTexCoord2f(u1, v1); GL11.glVertex2f(sx + sw, sy + sh);
        GL11.glTexCoord2f(u0, v1); GL11.glVertex2f(sx,      sy + sh);
        GL11.glEnd();
    }

    static void withTexture(int tex, Runnable r) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        r.run();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
}
