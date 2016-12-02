package com.zhilun.camera.gallery;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Created by taipp on 11/7/2016.
 */

public class FileUtils {

    private static final String TAG = "FileUtils";

    private static final String FILE_TYPE_IMAGE = "image";
    private static final String FILE_TYPE_VIDEO = "video";
    private static final String FILE_TYPE_AUDIO = "audio";

    private Context context;
    private String imageDir;

    private ArrayList<FileInfo> fileInfoList;
    private ArrayList<FileGroup> fileGroupList;

    private static final boolean initByMediaStore = false;

    public FileUtils(Context context, String imageDir, String[] mediaTypes) {
        this.context = context;
        this.imageDir = imageDir;
        this.fileInfoList = new ArrayList<>();
        this.fileGroupList = new ArrayList<>();

        initFileGroup(mediaTypes);
    }

    private void initFileGroup(String[] mediaType) {
        if (initByMediaStore) {
            initFileInfoListByMediaStore(mediaType);
        } else {
            initFileList(mediaType);
        }
        if (!fileInfoList.isEmpty()) {
            divideIntoGroup(fileGroupList, fileInfoList);
        }
        for (FileGroup group : fileGroupList) {
            Log.d(TAG, String.format("-->groupName: %s, length: %d", group.getName(), group.getLength()));
        }
    }

    //init by FileList
    private void initFileList(final String[] mediaTypes) {
        File dir = new File(imageDir);

        FileFilter fileFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String mimeType = getFileMimeType(pathname.getAbsolutePath());
                boolean isMyType = false;
                if (mimeType != null) {
                    for (String mediaType : mediaTypes) {
                        isMyType = mimeType.startsWith(mediaType) || isMyType;
                    }
                }
                return isMyType;
            }
        };
        File[] files = dir.listFiles(fileFilter);
        ArrayList<File> fileList = new ArrayList<>();
        Collections.addAll(fileList, files);
        Collections.sort(fileList, new FileComparator());
        for (File file : fileList) {
            fileInfoList.add(new FileInfo(file.getAbsolutePath(), // url
                    file.getName(), // name
                    "title",
                    getFileMimeType(file.getAbsolutePath()),
                    file.lastModified(),
                    0, //FileColumns.MEDIA_TYPE_NONE
                    file.length()));
        }
        Log.d(TAG, String.format(Locale.US, "initFileList: fileList.size() = %d", fileList.size()));
    }

    private boolean isFileExists(String url) {
        return new File(url).exists();
    }

    private String getFileMimeType(String url) {
        String mimeType = "";
        if (isFileExists(url)) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null) {
                MimeTypeMap mime = MimeTypeMap.getSingleton();
                mimeType = mime.getMimeTypeFromExtension(extension);
            }
            //Log.d(TAG, String.format(Locale.US, "getFileMimeType-->%s", mimeType));
        }
        return mimeType;
    }

    private class FileComparator implements Comparator<File> {
        @Override
        public int compare(File o1, File o2) {
            if (o1.lastModified() < o2.lastModified()) {
                return 1;
            } else {
                return -1;
            }
        }
    }

    //init by MediaStore.Files
    private void divideIntoGroup(ArrayList<FileGroup> fileGroupLists, ArrayList<FileInfo> mList) {
        Date curDate = new Date();
        Date nextDate = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String curDateStr = "";
        String nextDateStr = "";
        ArrayList<FileInfo> list = new ArrayList<>();

        Log.d(TAG, String.format(Locale.US, "divideIntoGroup: mList.size() = %d", mList.size()));
        for (int i = 0; i < mList.size(); i++) {
            FileInfo fileInfo = mList.get(i);
            if (i == 0) {
                curDate.setTime(fileInfo.getDate_modified());
                curDateStr = format.format(curDate);
            }
            //Log.d(TAG, String.format(Locale.US, "divideIntoGroup: curDate[%d] = %s name:%s", i, curDateStr, fileInfo.getDisplay_name()));

            nextDate.setTime(fileInfo.getDate_modified());
            nextDateStr = format.format(nextDate);

            if (!nextDateStr.equals(curDateStr)) {
                curDate = nextDate;
                curDateStr = nextDateStr;
                fileGroupLists.add(new FileGroup(list));
                list = new ArrayList<>();
            }
            list.add(fileInfo);

            if (mList.size() - 1 == i) {
                fileGroupLists.add(new FileGroup(list));
            }
        }
    }

    private void initFileInfoListByMediaStore(String[] extension) {
        //从外存中获取
        Uri fileUri = Files.getContentUri("external");
        Log.d(TAG, String.format(Locale.US, "fileUri-->%s", fileUri));
        //筛选列，这里只筛选了：文件路径和不含后缀的文件名
        String[] projection = new String[] {
                FileColumns.DATA,
                FileColumns.DISPLAY_NAME,
                FileColumns.TITLE,
                FileColumns.MIME_TYPE,
                FileColumns.DATE_MODIFIED,
                FileColumns.MEDIA_TYPE,
                FileColumns.SIZE
        };
        //构造筛选语句
        String selection = "";
        for(int i = 0; i < extension.length; i++) {
            if( i != 0) {
                selection = selection + " OR ";
            }
            selection += String.format(FileColumns.MIME_TYPE + " LIKE '%s%%'", extension[i]);
        }
        selection = String.format(FileColumns.DATA + " LIKE '%s/%%' AND (%s)", imageDir, selection);
        Log.d(TAG, String.format(Locale.US, "selection-->%s", selection));
        //按时间递增顺序对结果进行排序;待会从后往前移动游标就可实现时间递减
        String sortOrder = FileColumns.DATE_MODIFIED;
        //获取内容解析器对象
        ContentResolver resolver = context.getContentResolver();
        //获取游标
        Cursor cursor = resolver.query(fileUri, projection, selection, null, sortOrder);
        if(cursor == null)
            return;
        //游标从最后开始往前递减，以此实现时间递减顺序（最近访问的文件，优先显示）
        if (cursor.moveToLast()) {
            do {
                FileInfo info = new FileInfo(cursor.getString(cursor.getColumnIndex(FileColumns.DATA)),
                        cursor.getString(cursor.getColumnIndex(FileColumns.DISPLAY_NAME)),
                        cursor.getString(cursor.getColumnIndex(FileColumns.TITLE)),
                        cursor.getString(cursor.getColumnIndex(FileColumns.MIME_TYPE)),
                        cursor.getLong(cursor.getColumnIndex(FileColumns.DATE_MODIFIED)) * 1000,
                        cursor.getInt(cursor.getColumnIndex(FileColumns.MEDIA_TYPE)),
                        cursor.getLong(cursor.getColumnIndex(FileColumns.SIZE)));
                fileInfoList.add(info);
                //Log.d(TAG, String.format(Locale.US, "cursor-->%s, %s, %s", cursor.getString(0), cursor.getString(3), cursor.getString(4)));
            } while (cursor.moveToPrevious());
        }
        cursor.close();
    }

    public ArrayList<FileGroup> getFileGroupList() {
        return fileGroupList;
    }
}

class FileGroup {
    private String name;
    private String title;
    private int length;
    private ArrayList<FileInfo> fileList;

    public FileGroup(ArrayList<FileInfo> fileInfoList) {
        this.fileList = fileInfoList;
        if (!fileInfoList.isEmpty()) {
            Date date = new Date(fileInfoList.get(0).getDate_modified());
            this.name = SimpleDateFormat.getDateInstance().format(date);
        } else {
            this.name = "";
        }
        this.title = this.name;
        this.length = fileInfoList.size();
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public int getLength() {
        return length;
    }

    public ArrayList<FileInfo> getFileList() {
        return fileList;
    }
}

class FileInfo {
    private String url;
    private String display_name;
    private String title;
    private String mime_type;
    private long date_modified;
    private int media_type;
    private long size;

    public FileInfo() {
    }

    public FileInfo(String url, String display_name, String title, String mime_type, long date_modified, int media_type, long size) {
        this.url = url;
        this.display_name = display_name;
        this.title = title;
        this.mime_type = mime_type;
        this.date_modified =date_modified;
        this.media_type = media_type;
        this.size = size;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setDisplay_name(String display_name) {
        this.display_name = display_name;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setMime_type(String mime_type) {
        this.mime_type = mime_type;
    }

    public void setDate_modified(long date_modified) {
        this.date_modified = date_modified;
    }

    public void setMedia_type(int media_type) {
        this.media_type = media_type;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public String getDisplay_name() {
        return display_name;
    }

    public String getTitle() {
        return title;
    }

    public String getMime_type() {
        return mime_type;
    }

    public long getDate_modified() {
        return date_modified;
    }

    public int getMedia_type() {
        return media_type;
    }

    public long getSize() {
        return size;
    }
}
