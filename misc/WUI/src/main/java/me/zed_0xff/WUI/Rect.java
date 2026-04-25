package me.zed_0xff.WUI;

/**
 * Simple integer rectangle in logical UI coordinates.
 */
record Rect(int x, int y, int w, int h) {
    public boolean isEmpty() {
        return w <= 0 || h <= 0;
    }
}

