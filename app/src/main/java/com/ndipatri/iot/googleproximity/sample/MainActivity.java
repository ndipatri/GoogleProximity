package com.ndipatri.iot.googleproximity.sample;

import android.os.Bundle;
import android.app.Activity;

import com.ndipatri.iot.googleproximity.activities.RequirementsActivity;

public class MainActivity extends RequirementsActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

}
