package com.vf6.splitmirror.car;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;
import com.google.android.apps.auto.sdk.StatusBarController;
import com.vf6.splitmirror.R;
import com.vf6.splitmirror.capture.FrameStore;
import com.vf6.splitmirror.input.SplitAccessibilityService;

public class MainCarActivity extends CarActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView image;
    private TextView status;
    private Bitmap lastBitmap;
    private float downX, downY;
    private long downAt;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            Bitmap b = FrameStore.latest();
            if (b != null && b != lastBitmap) {
                lastBitmap = b;
                image.setImageBitmap(b);
                status.setVisibility(View.GONE);
            } else if (!FrameStore.isActive()) {
                status.setVisibility(View.VISIBLE);
            }
            handler.postDelayed(this, 100);
        }
    };

    @Override public void onCreate(Bundle bundle) {
        setTheme(R.style.AppTheme_Car);
        super.onCreate(bundle);
        setContentView(R.layout.activity_car_main);
        image = (ImageView) findViewById(R.id.mirror_image);
        status = (TextView) findViewById(R.id.car_status);

        CarUiController controller = getCarUiController();
        StatusBarController bar = controller.getStatusBarController();
        bar.setTitle(getString(R.string.app_name));
        bar.showTitle();

        image.setOnTouchListener(this::onTouchMirror);
    }

    @Override public void onStart() {
        super.onStart();
        handler.post(refresh);
    }

    @Override public void onStop() {
        handler.removeCallbacks(refresh);
        super.onStop();
    }

    private boolean onTouchMirror(View view, MotionEvent e) {
        if (!FrameStore.isActive()) return true;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downX = e.getX(); downY = e.getY(); downAt = e.getEventTime();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float dx = e.getX() - downX;
            float dy = e.getY() - downY;
            long dt = e.getEventTime() - downAt;
            PointMap start = mapToPhone(view, downX, downY);
            PointMap end = mapToPhone(view, e.getX(), e.getY());
            if (start == null || end == null) return true;
            if (dt > 180 || Math.hypot(dx, dy) > 30) {
                SplitAccessibilityService.swipe(start.x, start.y, end.x, end.y);
            } else {
                SplitAccessibilityService.tap(end.x, end.y);
            }
            return true;
        }
        return true;
    }

    private PointMap mapToPhone(View view, float vx, float vy) {
        Bitmap b = FrameStore.latest();
        int phoneW = FrameStore.phoneWidth();
        int phoneH = FrameStore.phoneHeight();
        if (b == null || phoneW <= 0 || phoneH <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0) return null;

        // ImageView fitCenter: map car-view coordinate into captured 1280x720 bitmap.
        float bitmapScale = Math.min(view.getWidth() / (float)b.getWidth(), view.getHeight() / (float)b.getHeight());
        float shownW = b.getWidth() * bitmapScale;
        float shownH = b.getHeight() * bitmapScale;
        float bx = (vx - (view.getWidth() - shownW) / 2f) / bitmapScale;
        float by = (vy - (view.getHeight() - shownH) / 2f) / bitmapScale;
        if (bx < 0 || by < 0 || bx >= b.getWidth() || by >= b.getHeight()) return null;

        // Android mirrors the physical phone display aspect-fit into the capture surface.
        float sourceScale = Math.min(b.getWidth() / (float)phoneW, b.getHeight() / (float)phoneH);
        float contentW = phoneW * sourceScale;
        float contentH = phoneH * sourceScale;
        float ox = (b.getWidth() - contentW) / 2f;
        float oy = (b.getHeight() - contentH) / 2f;
        if (bx < ox || by < oy || bx >= ox + contentW || by >= oy + contentH) return null;

        float px = (bx - ox) / sourceScale;
        float py = (by - oy) / sourceScale;
        return new PointMap(px, py);
    }

    private static final class PointMap {
        final float x, y;
        PointMap(float x, float y) { this.x = x; this.y = y; }
    }
}
