package com.vf6.splitmirror.capture;

import android.graphics.Bitmap;

public final class FrameStore {
    private static volatile Bitmap latest;
    private static volatile int phoneWidth;
    private static volatile int phoneHeight;
    private static volatile int captureWidth;
    private static volatile int captureHeight;
    private static volatile boolean active;

    private FrameStore() { }

    public static void publish(Bitmap bitmap, int pW, int pH, int cW, int cH) {
        latest = bitmap;
        phoneWidth = pW;
        phoneHeight = pH;
        captureWidth = cW;
        captureHeight = cH;
        active = true;
    }

    public static Bitmap latest() { return latest; }
    public static int phoneWidth() { return phoneWidth; }
    public static int phoneHeight() { return phoneHeight; }
    public static int captureWidth() { return captureWidth; }
    public static int captureHeight() { return captureHeight; }
    public static boolean isActive() { return active; }

    public static void clear() {
        latest = null;
        active = false;
    }
}
