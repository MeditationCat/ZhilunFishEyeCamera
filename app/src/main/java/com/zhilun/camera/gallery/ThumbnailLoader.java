package com.zhilun.camera.gallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;
import android.view.View;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

/**
 * Created by taipp on 11/4/2016.
 */

public class ThumbnailLoader {
    private static final String TAG = "ThumbnailLoader";
    private LruCache<String, Bitmap> lruCache;

    public ThumbnailLoader() {
        //获取系统分配给每个应用程序的最大内存，每个应用系统分配32M
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int mCacheSize = maxMemory / 8;
        //给LruCache分配1/8 4M
        lruCache = new LruCache<String, Bitmap>(mCacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    public Bitmap getThumbnailFromCache(String url) {
        return lruCache.get(url);
    }

    public void addThumbnailToCache(String url, Bitmap bitmap) {
        if (getThumbnailFromCache(url) == null) {
            lruCache.put(url, bitmap);
        }
    }

    public void showThumbnailByAsyncTask(FileInfo fileInfo, GridWithHeaderFragment.ItemViewHolder itemViewHolder) {
        new MyAsyncTask(fileInfo, itemViewHolder).execute(fileInfo.getUrl());

    }

    private class MyAsyncTask extends AsyncTask<String, Void, Bitmap> {

        private GridWithHeaderFragment.ItemViewHolder itemViewHolder;
        private FileInfo fileInfo;

        public MyAsyncTask(FileInfo fileInfo, GridWithHeaderFragment.ItemViewHolder itemViewHolder) {
            this.fileInfo = fileInfo;
            this.itemViewHolder = itemViewHolder;
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            Bitmap bitmap = getThumbnailFromCache(params[0]);
            if (bitmap == null) {
                bitmap = getBitmapFormUrl(params[0]);
                if (bitmap != null) {
                    addThumbnailToCache(params[0], bitmap);
                }
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if (bitmap != null && itemViewHolder.rootView.getTag().equals(fileInfo.getUrl())) {
                if (fileInfo.getMime_type().startsWith("video")) {
                    itemViewHolder.imgSubItem.setVisibility(View.VISIBLE);
                } else {
                    itemViewHolder.imgSubItem.setVisibility(View.INVISIBLE);
                }
                itemViewHolder.imgItem.setImageBitmap(bitmap);
            }
        }

        private Bitmap getBitmapFormUrl(String url) {
            Bitmap bitmap = null;
            Log.d(TAG, "-->" + url);
            switch (MediaURL.getMediaType(url)) {
                case MediaURL.URL_TYPE_NETWORK_VIDEO:
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    try {
                        Log.d(TAG, "-->" + Build.VERSION.SDK_INT);

                        if (Build.VERSION.SDK_INT >= 14) {
                            //network video
                            retriever.setDataSource(url, new HashMap<String, String>());
                        } else {
                            // local video
                            retriever.setDataSource(url);
                        }
                        bitmap = retriever.getFrameAtTime();
                    } catch (RuntimeException ex) {
                        // Assume this is a corrupt video file.
                    } finally {
                        try {
                            retriever.release();
                        } catch (RuntimeException ex) {
                            // Ignore failures while cleaning up.
                        }
                    }
                    break;
                case MediaURL.URL_TYPE_LOCAL_VIDEO:
                    bitmap = ThumbnailUtils.createVideoThumbnail(url, MediaStore.Video.Thumbnails.MICRO_KIND);
                    break;
                case MediaURL.URL_TYPE_LOCAL_PICTURE:
                    bitmap = BitmapFactory.decodeFile(url);
                    break;
                case MediaURL.URL_TYPE_NETWORK_PICTURE:
                    HttpURLConnection con = null;
                    try {
                        URL mImageUrl = new URL(url);
                        con = (HttpURLConnection) mImageUrl.openConnection();
                        con.setConnectTimeout(10 * 1000);
                        con.setReadTimeout(10 * 1000);
                        con.setDoInput(true);
                        con.setDoOutput(true);
                        bitmap = BitmapFactory.decodeStream(con.getInputStream());
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (con != null) {
                            con.disconnect();
                        }
                    }
                    break;
            }
            bitmap = ThumbnailUtils.extractThumbnail(bitmap,
                    itemViewHolder.imgItem.getMeasuredWidth(),
                    itemViewHolder.imgItem.getMeasuredWidth(),
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            return bitmap;
        }
    }

    private static class MediaURL {
        static final int URL_TYPE_DEFAULT = 0x00;
        static final int URL_TYPE_LOCAL_PICTURE = 0x01;
        static final int URL_TYPE_LOCAL_VIDEO = 0x02;
        static final int URL_TYPE_NETWORK_PICTURE = 0x11;
        static final int URL_TYPE_NETWORK_VIDEO = 0x12;

        static int getMediaType(String url) {
            int type = URL_TYPE_DEFAULT;
            if (url.endsWith(".png")
                    || url.endsWith(".jpg") || url.endsWith(".jpeg")
                    || url.endsWith(".gif")
                    || url.endsWith(".bmp")
                    || url.endsWith(".wbmp")) {
                if (url.startsWith("http://") || url.startsWith("https://")
                        || url.startsWith("ftp://")) {
                    type = URL_TYPE_NETWORK_PICTURE;
                } else {
                    type = URL_TYPE_LOCAL_PICTURE;
                }
            } else if (url.endsWith(".mp4") || url.endsWith(".m4v")
                    || url.endsWith(".3gp") || url.endsWith(".3gpp") || url.endsWith(".3gpp2")
                    || url.endsWith(".wmv")
                    || url.endsWith(".wbmp")) {
                if (url.startsWith("http://") || url.startsWith("https://")
                        || url.startsWith("ftp://")) {
                    type = URL_TYPE_NETWORK_VIDEO;
                } else {
                    type = URL_TYPE_LOCAL_VIDEO;
                }
            }
            return type;
        }
    }
}
