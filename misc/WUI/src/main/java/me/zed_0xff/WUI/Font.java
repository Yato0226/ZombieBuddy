package me.zed_0xff.WUI;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.google.gson.Gson;

class Font {
    Map<Integer, GlyphJson> glyphById = new HashMap<>();
    Map<Long, Integer> kernAmount = new HashMap<>();
    GlyphJson spaceGlyph;
    int atlasW;
    int atlasH;
    int fontTex;
    FaceJson face;

    static final class FontJson {
        AtlasJson atlas;
        FaceJson face;
        List<GlyphJson> glyphs;
        List<KerningJson> kernings;
    }

    static final class AtlasJson {
        int width, height;
        String image;
    }

    static final class FaceJson {
        String family;
        int size;
        int lineHeight;
        int base;
        int[] padding;
        int[] spacing;
    }

    static final class GlyphJson {
        int id, x, y, w, h, xo, yo, xa;
    }

    static final class KerningJson {
        int first, second, amount;
    }

    public Font() {
        File jsonFile = new File("font.json");
        FontJson cfg = Atlas.readJson(jsonFile, new Gson(), FontJson.class);
        if (cfg == null) throw new IllegalStateException("failed reading " + jsonFile);
        if (cfg.glyphs == null || cfg.glyphs.isEmpty()) throw new IllegalStateException("no glyphs in " + jsonFile);

        Atlas a = Atlas.load(jsonFile.getParentFile(), cfg.atlas.image, cfg.atlas.width, cfg.atlas.height);
        if (a == null) throw new RuntimeException("failed loading font atlas: " + cfg.atlas.image);
        atlasW = a.w;
        atlasH = a.h;
        fontTex = a.uploadTexture();

        for (GlyphJson g : cfg.glyphs) glyphById.put(g.id, g);
        spaceGlyph = glyphById.getOrDefault(32, cfg.glyphs.get(0));
        if (cfg.kernings != null) {
            for (KerningJson k : cfg.kernings) kernAmount.put(Utils.packPair(k.first, k.second), k.amount);
        }
        face = cfg.face;
    }

    /** Horizontal advance of the first line, in font pixels (same rules as {@link #drawText}). */
    int measureTextAdvancePx(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int relX = 0;
        int prevCp = -1;
        for (int off = 0; off < s.length();) {
            int cp = s.codePointAt(off);
            off += Character.charCount(cp);
            if (cp == '\n') {
                break;
            }
            if (prevCp >= 0) {
                Integer k = kernAmount.get(Utils.packPair(prevCp, cp));
                if (k != null) {
                    relX += k;
                }
            }
            GlyphJson g = glyphById.getOrDefault(cp, spaceGlyph);
            relX += g.xa;
            prevCp = cp;
        }
        return relX;
    }

    /**
     * @param y of the first line’s top (glWindow coords, origin top-left).
     * @param scale integer scale factor (font pixels → screen pixels).
     */
    void drawText(int x, int y, String s) {
        int scale = 1;
        int relX = 0;
        int relLineY = 0;
        int prevCp = -1;
        int lineSkip = face.lineHeight;

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
                Integer k = kernAmount.get(Utils.packPair(prevCp, cp));
                if (k != null) {
                    relX += k;
                }
            }

            GlyphJson g = glyphById.getOrDefault(cp, spaceGlyph);
            if (g.w > 0 && g.h > 0) {
                float sx = x + (relX + g.xo) * scale;
                float sy = y + (relLineY + g.yo) * scale;
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

    /**
     * Draw a single-line string centered horizontally within {@code boxW} and vertically around {@code centerY}.
     * X centering uses advance (xa + kerning). Y centering uses face.base within face.lineHeight.
     *
     * @param boxX    left of box
     * @param centerY absolute vertical center for the text
     * @param boxW    width of box
     * @param s       text to draw
     */
    void drawTextCentered(int boxX, int centerY, int boxW, String s) {
        if (s == null || s.isEmpty()) {
            return;
        }
        int tw = measureTextAdvancePx(s);
        int tx = boxX + (boxW - tw) / 2;

        int baselineY = centerY + face.base - face.lineHeight / 2;
        int ty = baselineY - face.base;

        drawText(tx, ty, s);
    }
}
