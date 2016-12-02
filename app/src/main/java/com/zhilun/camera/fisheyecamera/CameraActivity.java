package com.zhilun.camera.fisheyecamera;
/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: CameraActivity.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.zhilun.camera.gallery.GalleryActivity;
import com.zhilun.camera.widget.CameraViewInterface;
import com.zhilun.camera.widget.UVCCameraTextureView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class CameraActivity extends Activity implements CameraDialog.CameraDialogParent {
    private static final boolean DEBUG = true;    // FIXME set false when production
    private static final String TAG = "CameraActivity";

    // for thread pool
    private static final int CORE_POOL_SIZE = 1;        // initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;            // maximum threads
    private static final int KEEP_ALIVE_TIME = 10;        // time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;

    private CameraHandler mHandler;
    private CameraViewInterface mUVCCameraView;
    private ImageButton mSwitchModeButton;
    private ImageButton mShutterButton;
    private ImageButton mActionMenuButton;
    private ImageButton mGalleryButton;
    private ImageButton mCalibrationButton;
    private ImageView mCameraBackgroundImageView;
    private Chronometer mRecordingTimeChronometer;
    private boolean mIsVideoMode = false;

    private final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.v(TAG, "onCreate:");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_camera);

        mUVCCameraView = (CameraViewInterface) findViewById(R.id.camera_view);
        mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        ((UVCCameraTextureView) mUVCCameraView).setOnClickListener(mOnClickListener);

        mActionMenuButton = (ImageButton) findViewById(R.id.imageButtonActionMenu);
        mActionMenuButton.setOnClickListener(mOnClickListener);
        mGalleryButton = (ImageButton) findViewById(R.id.imageButtonGallery);
        mGalleryButton.setOnClickListener(mOnClickListener);
        mCalibrationButton = (ImageButton) findViewById(R.id.imageButtonCalibration);
        mCalibrationButton.setOnClickListener(mOnClickListener);
        mSwitchModeButton = (ImageButton) findViewById(R.id.imageButtonShutterMode);
        mSwitchModeButton.setOnClickListener(mOnClickListener);
        mShutterButton = (ImageButton) findViewById(R.id.imageButtonActionShutter);
        mShutterButton.setOnClickListener(mOnClickListener);

        mCameraBackgroundImageView = (ImageView) findViewById(R.id.imageViewCameraBackground);
        mCameraBackgroundImageView.setVisibility(View.INVISIBLE);
        mRecordingTimeChronometer = (Chronometer) findViewById(R.id.chronometerRecordingTime);
        mRecordingTimeChronometer.setVisibility(View.INVISIBLE);

        mHandler = CameraHandler.createHandler(this, mUVCCameraView);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mUSBMonitor.setDeviceFilter(DeviceFilter.getDeviceFilters(this, R.xml.device_filter));

        WindowManager wm = this.getWindowManager();
        Point size = new Point();
        wm.getDefaultDisplay().getSize(size);
        if (DEBUG)
            Log.v(TAG, String.format(Locale.US, "size:%d, %d, sdpectRatio: %f", size.x, size.y, size.x * 1.0f / size.y));
        //mUVCCameraView.setAspectRatio(size.x * 1.0f / size.y);
    }

    public void updateModeSwitchIcon() {
        if (mIsVideoMode) {
            mSwitchModeButton.setImageResource(R.drawable.btn_mode_camera);
            mShutterButton.setImageResource(R.drawable.btn_shutter_video);
        } else {
            mSwitchModeButton.setImageResource(R.drawable.btn_mode_video);
            mShutterButton.setImageResource(R.drawable.btn_shutter);
        }
    }

    public void updateIconState(boolean isRecording) {
        int visibility = isRecording ? View.INVISIBLE : View.VISIBLE;
        mActionMenuButton.setVisibility(visibility);
        mSwitchModeButton.setVisibility(visibility);
        mGalleryButton.setVisibility(visibility);
        //mCalibrationButton.setVisibility(visibility);
        if (isRecording) {
            mRecordingTimeChronometer.setVisibility(View.VISIBLE);
        } else {
            mRecordingTimeChronometer.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.v(TAG, "onResume:");
        mUSBMonitor.register();
        if (mUVCCameraView != null) {
            mUVCCameraView.onResume();
        }
        if (mUSBMonitor.getDeviceCount() > 0) {
            mCameraBackgroundImageView.setVisibility(View.INVISIBLE);
        } else {
            mCameraBackgroundImageView.setVisibility(View.VISIBLE);
        }

        updateModeSwitchIcon();

        if (mHandler != null && !mHandler.isRecording()) {
            updateIconState(false);
        }
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.v(TAG, "onPause:");
        mHandler.closeCamera();
        if (mUVCCameraView != null) {
            mUVCCameraView.onPause();
        }
        mShutterButton.setClickable(false);
        mUSBMonitor.unregister();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mHandler != null) {
            mHandler = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mUVCCameraView = null;
        mShutterButton = null;
        super.onDestroy();
    }

    //double press back-key to exit app.
    private static final int TIME_INTERVAL = 2000;
    private long mBackPressed;
    @Override
    public void onBackPressed() {
        if (mBackPressed + TIME_INTERVAL > System.currentTimeMillis()) {
            super.onBackPressed();
            return;
        } else {
            Toast.makeText(getBaseContext(), R.string.double_click_exit_tips, Toast.LENGTH_SHORT).show();
        }
        mBackPressed = System.currentTimeMillis();
    }

    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(final View view) {
            switch (view.getId()) {
                case R.id.imageButtonActionMenu:
                    if (!mHandler.isCameraOpened()) {
                    } else {
                    }
                    break;

                case R.id.imageButtonShutterMode:
                    mIsVideoMode = !mIsVideoMode;
                    updateModeSwitchIcon();
                    break;

                case R.id.imageButtonActionShutter:
                    if (mHandler.isCameraOpened()) {
                        if (mIsVideoMode) {
                            if (!mHandler.isRecording()) {
                                int checkPermissionAudio, checkPermissionStorage;
                                checkPermissionAudio = ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.RECORD_AUDIO);
                                checkPermissionStorage = ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                                if (checkPermissionAudio != PackageManager.PERMISSION_GRANTED
                                        || checkPermissionStorage != PackageManager.PERMISSION_GRANTED) {
                                    if (checkPermissionStorage != PackageManager.PERMISSION_GRANTED) {
                                        ActivityCompat.requestPermissions(CameraActivity.this,
                                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                                MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                                    }
                                    if (checkPermissionAudio != PackageManager.PERMISSION_GRANTED) {
                                        ActivityCompat.requestPermissions(CameraActivity.this,
                                                new String[]{Manifest.permission.RECORD_AUDIO},
                                                MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                                    }
                                    return;
                                }

                                mHandler.startRecording();
                                mRecordingTimeChronometer.setBase(SystemClock.elapsedRealtime());
                                mRecordingTimeChronometer.setFormat("00:%s");
                                mRecordingTimeChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                                    @Override
                                    public void onChronometerTick(Chronometer chronometer) {
                                        int hh = (int) ((SystemClock.elapsedRealtime() - mRecordingTimeChronometer.getBase()) / 1000 + 1) / 3600;
                                        chronometer.setFormat(String.format(Locale.US, "%02d", hh) + ":%s");
                                    }
                                });
                                mRecordingTimeChronometer.start();

                                mShutterButton.setImageResource(R.drawable.btn_shutter_video_recording);
                                updateIconState(true);
                            } else {
                                mHandler.stopRecording();
                                mRecordingTimeChronometer.stop();
                                mShutterButton.setImageResource(R.drawable.btn_shutter_video);
                                updateIconState(false);
                            }
                        } else {
                            int checkPermissionStorage;
                            checkPermissionStorage = ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                            if (checkPermissionStorage != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(CameraActivity.this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                                return;
                            }
                            mHandler.captureStill();
                        }
                    }
                    break;

                case R.id.imageButtonGallery:
                    //Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    //intent.setType("image/*");
                    //Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()));
                    //Log.d(TAG, "-->" + intent);
                    Intent intent1 = new Intent();
                    intent1.setClass(CameraActivity.this, GalleryActivity.class);
                    startActivityForResult(intent1, 1);
                    break;

                case R.id.imageButtonCalibration:
                    break;

                default:
                    break;
            }
        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mHandler.captureStill();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                break;
            }
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mHandler.startRecording();
                    mRecordingTimeChronometer.setBase(SystemClock.elapsedRealtime());
                    mRecordingTimeChronometer.setFormat("00:%s");
                    mRecordingTimeChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                        @Override
                        public void onChronometerTick(Chronometer chronometer) {
                            int hh = (int) ((SystemClock.elapsedRealtime() - mRecordingTimeChronometer.getBase()) / 1000 + 1) / 3600;
                            chronometer.setFormat(String.format(Locale.US, "%02d", hh) + ":%s");
                        }
                    });
                    mRecordingTimeChronometer.start();

                    mShutterButton.setImageResource(R.drawable.btn_shutter_video_recording);
                    updateIconState(true);
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                break;
            }
            default:
                break;
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private static final float[] BANDWIDTH_FACTORS = {0.67f, 0.67f};
    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onAttach:" + device);
            //Toast.makeText(CameraActivity.this, R.string.usb_device_attached, Toast.LENGTH_SHORT).show();
            if (mUSBMonitor != null) {
                List<UsbDevice> usbDeviceList;
                usbDeviceList = mUSBMonitor.getDeviceList();
                if (usbDeviceList.size() == 1) {
                    mUSBMonitor.requestPermission(usbDeviceList.get(0));
                }
            }
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            if (DEBUG) Log.v(TAG, "onConnect:" + device);
            if (!mHandler.isCameraOpened()) {
                mHandler.openCamera(ctrlBlock);
                final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
                mHandler.startPreview(new Surface(st));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mShutterButton.setClickable(true);
                        mCameraBackgroundImageView.setVisibility(View.INVISIBLE);
                    }
                });
            }
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            if (DEBUG) Log.v(TAG, "onDisconnect:" + device);
            if (mHandler != null && mHandler.isEqual(device)) {
                if (DEBUG) Log.v(TAG, "onDisconnect:2===");
                mHandler.stopPreview();
                mHandler.closeCamera();
                if (mUVCCameraView != null) {
                    mUVCCameraView.onPause();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mShutterButton.setClickable(false);
                        mCameraBackgroundImageView.setVisibility(View.VISIBLE);
                        mRecordingTimeChronometer.stop();
                        updateModeSwitchIcon();
                        updateIconState(false);
                    }
                });
            }
        }

        @Override
        public void onDetach(final UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDetach:" + device);
            Toast.makeText(CameraActivity.this, R.string.usb_device_detached, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel() {
            if (DEBUG) Log.v(TAG, "onCancel:");
        }
    };

    /**
     * to access from CameraDialog
     *
     * @return
     */
    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

/*
    // if you need frame data as byte array on Java side, you can use this callback method with UVCCamera#setFrameCallback
	private final IFrameCallback mIFrameCallback = new IFrameCallback() {
		@Override
		public void onFrame(final ByteBuffer frame) {
		}
	}; */
}
