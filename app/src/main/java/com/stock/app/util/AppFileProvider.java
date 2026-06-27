package com.stock.app.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 极简 FileProvider 实现（不依赖 AndroidX）
 *
 * 仅支持 APK 更新安装场景，不依赖任何第三方库。
 * 最低兼容 API Level 14。
 */
public class AppFileProvider extends ContentProvider {

    private static final String[] COLUMNS = {
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = uriToFile(uri);
        if (file == null || !file.exists()) {
            return null;
        }

        String[] cols = (projection != null) ? projection : COLUMNS;
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = file.length();
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = uriToFile(uri);
        if (file == null || !file.exists()) {
            throw new FileNotFoundException(uri.toString());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * 将 content URI 转换为文件路径
     * URI 格式: content://{authority}/external-path/apk_download/stock-app-v128.apk
     * 映射到: Environment.getExternalStoragePublicDirectory("Download")/stock-app-v128.apk
     */
    private File uriToFile(Uri uri) {
        String path = uri.getPath();
        if (TextUtils.isEmpty(path)) return null;

        // 移除开头的 "/"
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // 解析路径段: root_type/root_name/relative_path
        String[] segments = path.split("/", 3);
        if (segments.length < 2) return null;

        String rootType = segments[0]; // "external-path" 或 "external-files-path"
        String relativePath = (segments.length >= 3) ? segments[2] : "";

        Context context = getContext();
        if (context == null) return null;

        File rootDir;
        if ("external-path".equals(rootType)) {
            rootDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
        } else if ("external-files-path".equals(rootType)) {
            rootDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        } else {
            rootDir = context.getExternalFilesDir(null);
        }

        if (rootDir == null) return null;

        if (!TextUtils.isEmpty(relativePath)) {
            return new File(rootDir, relativePath);
        }
        return rootDir;
    }
}
