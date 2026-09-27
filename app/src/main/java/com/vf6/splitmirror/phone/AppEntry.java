package com.vf6.splitmirror.phone;

import android.content.ComponentName;

public final class AppEntry {
    public final String label;
    public final ComponentName component;

    public AppEntry(String label, ComponentName component) {
        this.label = label;
        this.component = component;
    }

    @Override public String toString() {
        return label;
    }
}
