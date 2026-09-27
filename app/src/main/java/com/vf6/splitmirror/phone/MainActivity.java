package com.vf6.splitmirror.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.vf6.splitmirror.R;
import com.vf6.splitmirror.capture.MirrorCaptureService;
import com.vf6.splitmirror.input.SplitAccessibilityService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_CAPTURE = 1001;
    private static final int REQ_NOTIFICATIONS = 1002;
    private static final String PREFS = "vf6_split";
    private static final String KEY_LEFT = "left";
    private static final String KEY_RIGHT = "right";

    private Spinner leftSpinner;
    private Spinner rightSpinner;
    private TextView status;
    private List<AppEntry> apps = new ArrayList<>();
    private ComponentName pendingLeft;
    private ComponentName pendingRight;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        leftSpinner = findViewById(R.id.spinner_left);
        rightSpinner = findViewById(R.id.spinner_right);
        status = findViewById(R.id.text_status);
        Button accessibility = findViewById(R.id.button_accessibility);
        Button start = findViewById(R.id.button_start);
        Button stop = findViewById(R.id.button_stop);

        loadApps();
        restoreSelection();

        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        start.setOnClickListener(v -> begin());
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, MirrorCaptureService.class));
            status.setText(R.string.status_ready);
        });


    }

    private void loadApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = getPackageManager().queryIntentActivities(launcher, PackageManager.MATCH_ALL);
        for (ResolveInfo info : infos) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            String label = String.valueOf(info.loadLabel(getPackageManager()));
            apps.add(new AppEntry(label,
                    new ComponentName(pkg, info.activityInfo.name)));
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
        ArrayAdapter<AppEntry> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, apps);
        leftSpinner.setAdapter(adapter);
        rightSpinner.setAdapter(adapter);
    }

    private void restoreSelection() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        setSpinnerByFlattened(leftSpinner, p.getString(KEY_LEFT, ""));
        setSpinnerByFlattened(rightSpinner, p.getString(KEY_RIGHT, ""));
    }

    private void setSpinnerByFlattened(Spinner spinner, String flattened) {
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).component.flattenToString().equals(flattened)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void begin() {
        if (apps.isEmpty()) return;
        AppEntry left = (AppEntry) leftSpinner.getSelectedItem();
        AppEntry right = (AppEntry) rightSpinner.getSelectedItem();
        if (left == null || right == null) return;
        if (left.component.equals(right.component)) {
            Toast.makeText(this, "Hãy chọn 2 app khác nhau", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!SplitAccessibilityService.isConnected()) {
            new AlertDialog.Builder(this)
                    .setTitle("Cần Accessibility")
                    .setMessage("Bật dịch vụ VF6 Split Mirror Control để app có thể tạo chế độ chia đôi và chuyển thao tác chạm từ màn hình xe về điện thoại.")
                    .setPositiveButton("Mở cài đặt", (d, w) ->
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton("Huỷ", null)
                    .show();
            return;
        }

        pendingLeft = left.component;
        pendingRight = right.component;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_LEFT, pendingLeft.flattenToString())
                .putString(KEY_RIGHT, pendingRight.flattenToString())
                .apply();

        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            Toast.makeText(this, "Bạn chưa cấp quyền phản chiếu màn hình", Toast.LENGTH_SHORT).show();
            return;
        }
        MirrorCaptureService.start(this, resultCode, data);
        status.setText(R.string.status_capture);

        final ComponentName left = pendingLeft;
        final ComponentName right = pendingRight;
        getWindow().getDecorView().postDelayed(() -> {
            boolean started = SplitAccessibilityService.launchPair(this, left, right);
            if (!started) {
                Toast.makeText(this,
                        "Máy không cung cấp lệnh chia đôi qua Accessibility. Hãy chia đôi 2 app thủ công rồi mở VF6 Split Mirror trong Android Auto.",
                        Toast.LENGTH_LONG).show();
            }
        }, 900);
    }
}
