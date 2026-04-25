package me.zed_0xff.WUI;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

/** GLFW cursor handles. Call {@link #create} after GLFW init, {@link #destroy} on shutdown. */
public final class CursorMgr {
    static long arrow, resizeH, resizeV, curNWSE, curNESW, text, hand, clock;

    private static boolean initialized;
    private CursorMgr() {}

    private static final String[] TILE_NAMES = {
        "arrow", "resizeH", "resizeW", "resizeNWSE", "resizeNESW", "text", "hand", "clock"
    };

    /** Load custom cursors from JSON; falls back to GLFW standard cursors on any failure. */
    public static void create(File cursorsJson) {
        if (initialized) return;
        initialized = true;

        Atlas atlas = new Atlas(cursorsJson);
        if (!atlas.isLoaded() || atlas.tiles == null
                || !atlas.tiles.keySet().containsAll(Arrays.asList(TILE_NAMES))) {
            createStandard(); return;
        }

        long[] h = new long[TILE_NAMES.length];
        for (int i = 0; i < TILE_NAMES.length; i++) {
            Atlas.TileJson tile = atlas.tiles.get(TILE_NAMES[i]);
            if (!atlas.fits(tile)) { destroyRange(h, i); createStandard(); return; }
            int hx = tile.getMetaInt("hx", tile.w / 2);
            int hy = tile.getMetaInt("hy", tile.h / 2);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLFWImage img = GLFWImage.malloc(stack);
                img.set(tile.w, tile.h, atlas.cellToRgba(tile));
                h[i] = GLFW.glfwCreateCursor(img, hx, hy);
            }
            if (h[i] == 0) { destroyRange(h, i); createStandard(); return; }
        }
        arrow = h[0]; resizeH = h[1]; resizeV = h[2]; curNWSE = h[3];
        curNESW = h[4]; text = h[5]; hand = h[6]; clock = h[7];
    }

    public static void destroy() {
        if (!initialized) return;
        initialized = false;
        HashSet<Long> seen = new HashSet<>();
        for (long c : new long[]{ arrow, resizeH, resizeV, curNWSE, curNESW, text, hand, clock })
            if (c != 0 && seen.add(c)) GLFW.glfwDestroyCursor(c);
        arrow = resizeH = resizeV = curNWSE = curNESW = text = hand = clock = 0;
    }

    /** Set cursor; 0 maps to {@link #arrow}. */
    public static void set(long win, long cursor) { GLFW.glfwSetCursor(win, cursor == 0 ? arrow : cursor); }
    /** Reset to OS default (use when mouse is outside all windows). */
    public static void setDefault(long win) { GLFW.glfwSetCursor(win, 0); }

    private static void createStandard() {
        arrow   = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);
        resizeH = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
        resizeV = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR);
        curNWSE = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR);
        curNESW = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR);
        if (curNWSE == 0) curNWSE = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
        if (curNWSE == 0) curNWSE = resizeH;
        if (curNESW == 0) curNESW = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
        if (curNESW == 0) curNESW = resizeV;
    }

    private static void destroyRange(long[] h, int count) {
        for (int j = 0; j < count; j++) if (h[j] != 0) GLFW.glfwDestroyCursor(h[j]);
    }

}
