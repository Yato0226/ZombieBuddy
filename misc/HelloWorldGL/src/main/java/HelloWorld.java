import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public final class HelloWorld {
    static final int WIN_W = 640;
    static final int WIN_H = 480;

    static int atlasW;
    static int atlasH;
    static FontJson font;
    static Map<Integer, GlyphJson> glyphById = new HashMap<>();
    static Map<Long, Integer> kernAmount = new HashMap<>();
    static GlyphJson spaceGlyph;

    private static final int[] FONT_SCALES = {1, 2, 3, 4, 5};
    private static volatile int fontScaleIndex = 0;

    static int currentFontScale() {
        return FONT_SCALES[fontScaleIndex % FONT_SCALES.length];
    }

    public static void main(String[] args) throws IOException {
        File jsonFile = new File(args.length > 0 ? args[0] : "font.json");
        Gson gson = new Gson();
        try (InputStreamReader r = new InputStreamReader(
            java.nio.file.Files.newInputStream(jsonFile.toPath()), StandardCharsets.UTF_8)) {
            font = gson.fromJson(r, FontJson.class);
        }
        if (font == null || font.glyphs == null || font.glyphs.isEmpty()) {
            throw new IllegalStateException("no glyphs in " + jsonFile);
        }
        for (GlyphJson g : font.glyphs) {
            glyphById.put(g.id, g);
        }
        spaceGlyph = glyphById.getOrDefault(32, font.glyphs.get(0));
        if (font.kernings != null) {
            for (KerningJson k : font.kernings) {
                kernAmount.put(packPair(k.first, k.second), k.amount);
            }
        }

        File pngFile = new File(jsonFile.getParentFile(), font.atlas.image);
        if (!pngFile.isFile()) {
            throw new IllegalStateException("atlas image not found: " + pngFile);
        }

        if (!GLFW.glfwInit()) throw new IllegalStateException("glfwInit failed");

        long window = GLFW.glfwCreateWindow(WIN_W, WIN_H, "Font JSON hello", 0, 0);
        if (window == 0) throw new RuntimeException("glfwCreateWindow failed");

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GL.createCapabilities();

        GLFW.glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                fontScaleIndex = (fontScaleIndex + 1) % FONT_SCALES.length;
                int s = currentFontScale();
                GLFW.glfwSetWindowTitle(win, "Font JSON hello " + s + "x");
            }
        });

        setup2D();

        int fontTex = loadTexture(pngFile);
        GLFW.glfwSetWindowTitle(window, "Font JSON hello " + currentFontScale() + "x");

        while (!GLFW.glfwWindowShouldClose(window)) {
            GL11.glClearColor(0.08f, 0.08f, 0.08f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fontTex);
            float sc = currentFontScale();
            int lh = font.face != null ? font.face.lineHeight : 16;
            drawText("Hello world!", 32, 32, sc);
            drawText("Привет мир!", 32, 32 + lh * sc, sc);

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }

        GL11.glDeleteTextures(fontTex);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    static void setup2D() {
        GL11.glViewport(0, 0, WIN_W, WIN_H);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, WIN_W, WIN_H, 0, -1, 1);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    static int loadTexture(File path) {
        BufferedImage img;
        try {
            img = ImageIO.read(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (img == null) {
            throw new RuntimeException("ImageIO.read failed: " + path);
        }

        atlasW = img.getWidth();
        atlasH = img.getHeight();
        if (font.atlas.width != atlasW || font.atlas.height != atlasH) {
            System.err.println("warn: atlas size json " + font.atlas.width + "x" + font.atlas.height
                + " != png " + atlasW + "x" + atlasH);
        }

        ByteBuffer pixels = BufferUtils.createByteBuffer(atlasW * atlasH * 4);
        for (int y = 0; y < atlasH; y++) {
            for (int x = 0; x < atlasW; x++) {
                int argb = img.getRGB(x, y);
                pixels.put((byte) ((argb >> 16) & 0xff));
                pixels.put((byte) ((argb >> 8) & 0xff));
                pixels.put((byte) (argb & 0xff));
                pixels.put((byte) ((argb >> 24) & 0xff));
            }
        }
        pixels.flip();

        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA8,
            atlasW,
            atlasH,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            pixels
        );

        return tex;
    }

    /**
     * @param lineTop y of the first line’s top (window coords, origin top-left).
     * @param scale integer scale factor (font pixels → screen pixels).
     */
    static void drawText(String s, float startX, float lineTop, float scale) {
        float relX = 0;
        float relLineY = 0;
        int prevCp = -1;
        int lineSkip = font.face != null ? font.face.lineHeight : 16;

        GL11.glBegin(GL11.GL_QUADS);

        for (int off = 0; off < s.length();) {
            int cp = s.codePointAt(off);
            off += Character.charCount(cp);

            if (cp == '\n') {
                relX = 0;
                relLineY += lineSkip;
                prevCp = -1;
                continue;
            }

            if (prevCp >= 0) {
                Integer k = kernAmount.get(packPair(prevCp, cp));
                if (k != null) {
                    relX += k;
                }
            }

            GlyphJson g = glyphById.getOrDefault(cp, spaceGlyph);
            if (g.w > 0 && g.h > 0) {
                float sx = startX + (relX + g.xo) * scale;
                float sy = lineTop + (relLineY + g.yo) * scale;
                float x1 = sx + g.w * scale;
                float y1 = sy + g.h * scale;

                float u0 = g.x / (float) atlasW;
                float u1 = (g.x + g.w) / (float) atlasW;
                float v0 = g.y / (float) atlasH;
                float v1 = (g.y + g.h) / (float) atlasH;

                GL11.glTexCoord2f(u0, v0); GL11.glVertex2f(sx, sy);
                GL11.glTexCoord2f(u1, v0); GL11.glVertex2f(x1, sy);
                GL11.glTexCoord2f(u1, v1); GL11.glVertex2f(x1, y1);
                GL11.glTexCoord2f(u0, v1); GL11.glVertex2f(sx, y1);
            }

            relX += g.xa;
            prevCp = cp;
        }

        GL11.glEnd();
    }

    static long packPair(int first, int second) {
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    // --- GSON model (glyphs use short keys: w, h, xo, yo, xa) ---

    static final class FontJson {
        AtlasJson atlas;
        FaceJson face;
        List<GlyphJson> glyphs;
        List<KerningJson> kernings;
    }

    static final class AtlasJson {
        int width;
        int height;
        String image;
    }

    static final class FaceJson {
        String family;
        int size;
        int bold;
        int italic;
        int lineHeight;
        int base;
        int[] padding;
        int[] spacing;
    }

    static final class GlyphJson {
        int id;
        int x;
        int y;
        @SerializedName("w") int w;
        @SerializedName("h") int h;
        @SerializedName("xo") int xo;
        @SerializedName("yo") int yo;
        @SerializedName("xa") int xa;
    }

    static final class KerningJson {
        int first;
        int second;
        int amount;
    }
}
