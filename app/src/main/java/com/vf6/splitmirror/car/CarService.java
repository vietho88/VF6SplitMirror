package com.vf6.splitmirror.car;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;

public class CarService extends CarActivityService {
    @Override public Class<? extends CarActivity> getCarActivity() {
        return MainCarActivity.class;
    }
}
