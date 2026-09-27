package com.vf6.splitmirror.capture;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import com.vf6.splitmirror.R;

import java.nio.ByteBuffer;

public class MirrorCaptureService extends Service {
    public static final String ACTION_START = "com.vf6.splitmirror.START_CAPTURE";
    public static final String ACTION_STOP = "com.vf6.splitmirror.STOP_CAPTURE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final int CAPTURE_W = 1280;
    private static final int CAPTURE_H = 720;
    private static final int NOTIFICATION_ID = 4106;
    private static final String CHANNEL = "vf6_mirror";

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread imageThread;
    private Handler imageHandler;
    private long lastFrameMs = 0;
    private int phoneWidth;
    private int phoneHeight;

    public static void start(Context context, int resultCode, Intent data) {
        Intent i = new Intent(context, MirrorCaptureService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data);
        context.startForegroundService(i);
    }

    @Override public void onCreate() {
        super.onCreate();
        imageThread = new HandlerThread("VF6Capture");
        imageThread.start();
        imageHandler = new Handler(imageThread.getLooper());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(intent.getAction())) {
            startForegroundCompat();
            if (projection == null) acquireProjection(intent);
        }
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, MirrorCaptureService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(getString(R.string.notification_title))
                .setOngoing(true)
                .addAction(0, getString(R.string.notification_stop), stop);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, b.build());
        }
    }

    private void acquireProjection(Intent intent) {
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        //noinspection deprecation
        Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (data == null) { stopSelf(); return; }

        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) { stopSelf(); return; }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, imageHandler);

        readPhoneSize();
        int density = getResources().getDisplayMetrics().densityDpi;
        imageReader = ImageReader.newInstance(CAPTURE_W, CAPTURE_H, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, imageHandler);
        virtualDisplay = projection.createVirtualDisplay(
                "vf6-split-mirror", CAPTURE_W, CAPTURE_H, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, imageHandler);
    }

    private void readPhoneSize() {
        WindowManager wm = getSystemService(WindowManager.class);
        DisplayMetrics dm = new DisplayMetrics();
        //noinspection deprecation
        wm.getDefaultDisplay().getRealMetrics(dm);
        phoneWidth = dm.widthPixels;
        phoneHeight = dm.heightPixels;
    }

    private void onImageAvailable(ImageReader reader) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastFrameMs < 120) {
            Image drop = reader.acquireLatestImage();
            if (drop != null) drop.close();
            return;
        }
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        lastFrameMs = now;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * CAPTURE_W;
            int paddedWidth = CAPTURE_W + rowPadding / pixelStride;
            Bitmap padded = Bitmap.createBitmap(paddedWidth, CAPTURE_H, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            Bitmap frame = paddedWidth == CAPTURE_W
                    ? padded
                    : Bitmap.createBitmap(padded, 0, 0, CAPTURE_W, CAPTURE_H);
            if (frame != padded) padded.recycle();
            readPhoneSize();
            FrameStore.publish(frame, phoneWidth, phoneHeight, CAPTURE_W, CAPTURE_H);
        } finally {
            image.close();
        }
    }

    @Override public void onDestroy() {
        FrameStore.clear();
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (projection != null) { projection.stop(); projection = null; }
        if (imageThread != null) { imageThread.quitSafely(); imageThread = null; }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
