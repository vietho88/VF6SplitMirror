package com.vf6.splitmirror.input;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;


public class SplitAccessibilityService extends AccessibilityService {
    private static volatile SplitAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    public static boolean isConnected() { return instance != null; }

    public static boolean launchPair(Context context, ComponentName left, ComponentName right) {
        SplitAccessibilityService svc = instance;
        if (svc == null || left == null || right == null) return false;
        if (!svc.supportsSplitAction()) return false;

        Intent first = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(left)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        context.startActivity(first);

        svc.handler.postDelayed(() -> {
            boolean split = svc.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
            if (!split) return;
            svc.handler.postDelayed(() -> {
                Intent second = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(right)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT |
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                context.startActivity(second);
            }, 700);
        }, 900);
        return true;
    }

    private boolean supportsSplitAction() {
        // GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN exists in API 24-29.
        // On newer Android versions the action may return false at runtime;
        // launchPair() already handles that and falls back to manual split-screen.
        return Build.VERSION.SDK_INT >= 24;
    }

    public static boolean tap(float x, float y) {
        SplitAccessibilityService svc = instance;
        if (svc == null || Build.VERSION.SDK_INT < 24) return false;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(p, 0, 60);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return svc.dispatchGesture(gesture, null, null);
    }

    public static boolean swipe(float x1, float y1, float x2, float y2) {
        SplitAccessibilityService svc = instance;
        if (svc == null || Build.VERSION.SDK_INT < 24) return false;
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(p, 0, 250);
        return svc.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    public static boolean pressBack() {
        SplitAccessibilityService svc = instance;
        return svc != null && svc.performGlobalAction(GLOBAL_ACTION_BACK);
    }
}
