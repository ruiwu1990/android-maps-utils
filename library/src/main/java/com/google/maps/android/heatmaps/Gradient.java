/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.maps.android.heatmaps;

import android.graphics.Color;

import java.util.HashMap;

/**
 * A class to generate a color activity_maps from a given array of colors and the fractions
 * that the colors represent by interpolating between their HSV values.
 * This color activity_maps is to be used in the HeatmapTileProvider.
 */
public class Gradient {

    private class ColorInterval {
        private final int color1;
        private final int color2;

        /**
         * The period over which the color changes from color1 to color2.
         * This is given as the number of elements it represents in the colorMap.
         */
        private final float duration;

        private ColorInterval(int color1, int color2, float duration) {
            this.color1 = color1;
            this.color2 = color2;
            this.duration = duration;
        }
    }

    private static final int DEFAULT_COLOR_MAP_SIZE = 1000;

    /**
     * Size of a color activity_maps for the heatmap
     */
    public final int mColorMapSize;

    /**
     * The colors to be used in the gradient
     */
    public int[] mColors;

    /**
     * The starting point for each color, given as a percentage of the maximum intensity
     */
    public float[] mStartPoints;

    /**
     * Creates a Gradient with the given colors and starting points.
     * These are given as parallel arrays.
     *
     * @param colors      The colors to be used in the gradient
     * @param startPoints The starting point for each color, given as a percentage of the maximum intensity
     *                    This is given as an array of floats with values in the interval [0,1]
     */
    public Gradient(int[] colors, float[] startPoints) {
        this(colors, startPoints, DEFAULT_COLOR_MAP_SIZE);
    }

    /**
     * Creates a Gradient with the given colors and starting points which creates a colorMap of given size.
     * The colors and starting points are given as parallel arrays.
     *
     * @param colors       The colors to be used in the gradient
     * @param startPoints  The starting point for each color, given as a percentage of the maximum intensity
     *                     This is given as an array of floats with values in the interval [0,1]
     * @param colorMapSize The size of the colorMap to be generated by the Gradient
     */
    public Gradient(int[] colors, float[] startPoints, int colorMapSize) {
        if (colors.length != startPoints.length) {
            throw new IllegalArgumentException("colors and startPoints should be same length");
        } else if (colors.length == 0) {
            throw new IllegalArgumentException("No colors have been defined");
        }
        for (int i = 1; i < startPoints.length; i++) {
            if (startPoints[i] <= startPoints[i - 1]) {
                throw new IllegalArgumentException("startPoints should be in increasing order");
            }
        }
        mColorMapSize = colorMapSize;
        mColors = new int[colors.length];
        mStartPoints = new float[startPoints.length];
        System.arraycopy(colors, 0, mColors, 0, colors.length);
        System.arraycopy(startPoints, 0, mStartPoints, 0, startPoints.length);
    }

    private HashMap<Integer, ColorInterval> generateColorIntervals() {
        HashMap<Integer, ColorInterval> colorIntervals = new HashMap<Integer, ColorInterval>();
        // Create first color if not already created
        // The initial color is transparent by default
        if (mStartPoints[0] != 0) {
            int initialColor = Color.argb(
                    0, Color.red(mColors[0]), Color.green(mColors[0]), Color.blue(mColors[0]));
            colorIntervals.put(0, new ColorInterval(initialColor, mColors[0], mColorMapSize * mStartPoints[0]));
        }
        // Generate color intervals
        for (int i = 1; i < mColors.length; i++) {
            colorIntervals.put(((int) (mColorMapSize * mStartPoints[i - 1])),
                    new ColorInterval(mColors[i - 1], mColors[i],
                            (mColorMapSize * (mStartPoints[i] - mStartPoints[i - 1]))));
        }
        // Extend to a final color
        // If color for 100% intensity is not given, the color of highest intensity is used.
        if (mStartPoints[mStartPoints.length - 1] != 1) {
            int i = mStartPoints.length - 1;
            colorIntervals.put(((int) (mColorMapSize * mStartPoints[i])),
                    new ColorInterval(mColors[i], mColors[i], mColorMapSize * (1 - mStartPoints[i])));
        }
        return colorIntervals;
    }

    /**
     * Generates the color activity_maps to use with a provided gradient.
     *
     * @param opacity Overall opacity of entire image: every individual alpha value will be
     *                multiplied by this opacity.
     * @return the generated color activity_maps based on the gradient
     */
    int[] generateColorMap(double opacity) {
        HashMap<Integer, ColorInterval> colorIntervals = generateColorIntervals();
        int[] colorMap = new int[mColorMapSize];
        ColorInterval interval = colorIntervals.get(0);
        int start = 0;
        for (int i = 0; i < mColorMapSize; i++) {
            if (colorIntervals.containsKey(i)) {
                interval = colorIntervals.get(i);
                start = i;
            }
            float ratio = (i - start) / interval.duration;
            colorMap[i] = interpolateColor(interval.color1, interval.color2, ratio);
        }
        if (opacity != 1) {
            for (int i = 0; i < mColorMapSize; i++) {
                int c = colorMap[i];
                colorMap[i] = Color.argb((int) (Color.alpha(c) * opacity),
                        Color.red(c), Color.green(c), Color.blue(c));
            }
        }

        return colorMap;
    }

    /**
     * Helper function for creation of color activity_maps
     * Interpolates between two given colors using their HSV values.
     *
     * @param color1 First color
     * @param color2 Second color
     * @param ratio  Between 0 to 1. Fraction of the distance between color1 and color2
     * @return Color associated with x2
     */
    static int interpolateColor(int color1, int color2, float ratio) {

        int alpha = (int) ((Color.alpha(color2) - Color.alpha(color1)) * ratio + Color.alpha(color1));

        float[] hsv1 = new float[3];
        Color.RGBToHSV(Color.red(color1), Color.green(color1), Color.blue(color1), hsv1);
        float[] hsv2 = new float[3];
        Color.RGBToHSV(Color.red(color2), Color.green(color2), Color.blue(color2), hsv2);

        // adjust so that the shortest path on the color wheel will be taken
        if (hsv1[0] - hsv2[0] > 180) {
            hsv2[0] += 360;
        } else if (hsv2[0] - hsv1[0] > 180) {
            hsv1[0] += 360;
        }

        // Interpolate using calculated ratio
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = (hsv2[i] - hsv1[i]) * (ratio) + hsv1[i];
        }

        return Color.HSVToColor(alpha, result);
    }

}
