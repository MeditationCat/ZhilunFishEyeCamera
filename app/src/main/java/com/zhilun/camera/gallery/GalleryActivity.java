package com.zhilun.camera.gallery;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.zhilun.camera.fisheyecamera.R;

public class GalleryActivity extends AppCompatActivity {

    private static final String TAG = "GalleryActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new GridWithHeaderFragment()).commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
