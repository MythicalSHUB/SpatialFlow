package com.codetrio.spatialflow.util;

import android.graphics.Color;

public class ColorUtils {

    public static double getLuminance(int color) {
        return (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255;
    }

    public static int lightenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        r = (int) (r + (255 - r) * factor);
        g = (int) (g + (255 - g) * factor);
        b = (int) (b + (255 - b) * factor);

        return Color.argb(a, r, g, b);
    }
}
