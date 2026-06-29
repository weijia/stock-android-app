package com.stock.app.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import com.stock.app.util.DebugLogger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

import android.content.UriPermission;
import android.content.ContentValues;

/**
 * 外部存储配置管理器
 * 
 * 支持两种存储方式：
 * 1. SAF (Storage Access Framework) - Android 4.4+ (API 19+)
 *    配置保存到用户选择的外部目录，卸载后保留
 * 2. 外部存储直接写入 - Android 4.0-4.3 (API 14-18)
 *    配置保存到公共目录 /sdcard/StockApp/
 * 
 * 同时备份到 SharedPreferences 作为降级方案
 */
public class ExternalStorageManager {
    private static final String TAG = "ExternalStorage";
    private static final String PREF_NAME = "stock_app_config";
    private static final String KEY_CONFIG = "app_config";
    private static final String KEY_SAF_URI = "saf_dir_uri";
    private static final String CONFIG_FILE_NAME = "stock_settings.json";
    
    // 外部存储公共目录（用于 Android 4.0-4.3）
    private static final String LEGACY_CONFIG_DIR = "StockApp";
    
    // SAF 请求码
    public static final int SAF_REQUEST_CODE = 1001;
    
    private Context context;
    private SharedPreferences prefs;
    private Uri safDirUri;
    private boolean safAvailable;
    
    public ExternalStorageManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // 检查 SAF 是否可用
        safAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT; // API 19
        
        // 恢复 SAF 目录 URI
        restoreSafDirectory();
    }
    
    /**
     * 检查 SAF 是否可用
     */
    public boolean isSafAvailable() {
        return safAvailable;
    }
    
    /**
     * 检查是否有 SAF 目录
     */
    public boolean hasSafDirectory() {
        return safDirUri != null;
    }
    
    /**
     * 请求用户选择 SAF 目录
     * 需要在 Activity 中调用，并处理 onActivityResult
     */
    public void requestSafDirectory(Activity activity) {
        if (!safAvailable) {
            Log.w(TAG, "SAF 不可用，使用外部存储直接写入");
            return;
        }
        
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION 
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION 
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION 
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        
        activity.startActivityForResult(intent, SAF_REQUEST_CODE);
    }
    
    /**
     * 处理 SAF 目录选择结果
     * 在 Activity.onActivityResult 中调用
     */
    public boolean handleSafResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != SAF_REQUEST_CODE || resultCode != Activity.RESULT_OK) {
            return false;
        }
        
        if (data == null || data.getData() == null) {
            return false;
        }
        
        Uri uri = data.getData();
        
        // 持久化权限
        ContentResolver resolver = context.getContentResolver();
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );
        
        // 保存 URI
        safDirUri = uri;
        prefs.edit().putString(KEY_SAF_URI, uri.toString()).apply();
        
        Log.i(TAG, "SAF 目录已选择: " + uri.toString());
        return true;
    }
    
    /**
     * 恢复 SAF 目录（从保存的 URI）
     */
    public void restoreSafDirectory() {
        String uriString = prefs.getString(KEY_SAF_URI, null);
        if (uriString == null || uriString.isEmpty()) {
            Log.i(TAG, "没有保存的 SAF 目录 URI");
            return;
        }
        
        try {
            safDirUri = Uri.parse(uriString);
            Log.i(TAG, "SAF 目录 URI 已解析: " + uriString);
            
            // 检查权限是否仍然有效
            ContentResolver resolver = context.getContentResolver();
            List<UriPermission> permissions = resolver.getPersistedUriPermissions();
            boolean hasPermission = false;
            
            // 安全检查：permissions 可能返回 null
            if (permissions != null) {
                for (UriPermission perm : permissions) {
                    Log.i(TAG, "已有权限: " + perm.getUri());
                    if (perm.getUri().toString().equals(uriString) || 
                        perm.getUri().toString().startsWith(uriString)) {
                        hasPermission = true;
                        break;
                    }
                }
            } else {
                Log.w(TAG, "getPersistedUriPermissions 返回 null");
            }
            
            if (!hasPermission) {
                Log.e(TAG, "SAF 目录权限已丢失，需要重新选择目录");
                safDirUri = null;
                prefs.edit().remove(KEY_SAF_URI).apply();
            } else {
                Log.i(TAG, "SAF 目录权限有效，已恢复: " + uriString);
            }
        } catch (Exception e) {
            Log.e(TAG, "恢复 SAF 目录失败: " + e.getMessage());
            safDirUri = null;
        }
    }
    
    /**
     * 加载配置
     * 优先级：SAF > 外部存储 > SharedPreferences
     */
    public JSONObject loadConfig() {
        JSONObject config = null;
        
        // 1. 尝试从 SAF 加载
        if (safAvailable && safDirUri != null) {
            config = loadFromSaf();
            if (config != null) {
                Log.i(TAG, "从 SAF 加载配置成功");
                return config;
            }
        }
        
        // 2. 尝试从外部存储加载（Android 4.0-4.3）
        config = loadFromExternalStorage();
        if (config != null) {
            Log.i(TAG, "从外部存储加载配置成功");
            return config;
        }
        
        // 3. 从 SharedPreferences 加载（降级）
        config = loadFromSharedPreferences();
        Log.i(TAG, "从 SharedPreferences 加载配置");
        return config;
    }
    
    /**
     * 保存配置
     * 同时保存到 SAF、外部存储和 SharedPreferences
     * 
     * @return 保存结果：SAF成功、外部存储成功、或仅SharedPreferences成功
     */
    public SaveResult saveConfig(JSONObject config) {
        String content = config.toString();
        SaveResult result = new SaveResult();
        Log.i(TAG, "====== 开始保存配置 ======");
        debugLog("保存配置开始，内容长度=" + content.length());

        // 1. 保存到 SAF（用户已选择目录时）
        if (safAvailable && safDirUri != null) {
            try {
                saveToSaf(content);
                result.safSuccess = true;
                result.safLocation = safDirUri.toString();
                Log.i(TAG, "[SAF] 保存成功: " + safDirUri);
                debugLog("SAF 保存成功: " + safDirUri);
            } catch (Exception e) {
                result.safError = e.getMessage();
                Log.e(TAG, "[SAF] 保存失败: " + e.getMessage(), e);
                debugLog("SAF 保存失败: " + e.getMessage());
            }
        } else {
            Log.i(TAG, "[SAF] 跳过，safAvailable=" + safAvailable + ", safDirUri=" + safDirUri);
            debugLog("SAF 跳过，未设置目录");
        }

        // 2. 保存到外部存储（公共可见目录）
        try {
            saveToExternalStorage(content);
            result.externalSuccess = true;
            File configDir = getLegacyConfigDir();
            if (configDir != null) {
                File configFile = new File(configDir, CONFIG_FILE_NAME);
                result.externalLocation = configFile.getAbsolutePath();
                Log.i(TAG, "[外部存储] 保存成功: " + configFile.getAbsolutePath());
                debugLog("外部存储保存成功: " + configFile.getAbsolutePath());
            }
        } catch (Exception e) {
            result.externalError = e.getMessage();
            Log.e(TAG, "[外部存储] 保存失败: " + e.getMessage(), e);
            debugLog("外部存储保存失败: " + e.getMessage());
        }

        // 3. 保存到 SharedPreferences（备份）
        prefs.edit().putString(KEY_CONFIG, content).apply();
        result.sharedPrefsSuccess = true;
        Log.i(TAG, "[SharedPreferences] 保存成功");
        debugLog("SharedPreferences 保存成功");
        Log.i(TAG, "====== 保存配置结束 ======");

        return result;
    }

    private void debugLog(String msg) {
        DebugLogger logger = DebugLogger.getInstance();
        if (logger != null) {
            logger.log("ExternalStorage", msg);
        }
    }
    
    /**
     * 保存结果类
     */
    public static class SaveResult {
        public boolean safSuccess = false;
        public String safLocation = null;
        public String safError = null;
        
        public boolean externalSuccess = false;
        public String externalLocation = null;
        public String externalError = null;
        
        public boolean sharedPrefsSuccess = false;
        
        /**
         * 是否有任何外部存储成功
         */
        public boolean isExternalStorageSuccess() {
            return safSuccess || externalSuccess;
        }
        
        /**
         * 获取成功保存的位置描述
         */
        public String getSuccessLocation() {
            if (safSuccess && safLocation != null) {
                return "SAF: " + safLocation;
            }
            if (externalSuccess && externalLocation != null) {
                return externalLocation;
            }
            return "应用内部存储";
        }
    }
    
    // ============ SAF 操作 ============
    
    private JSONObject loadFromSaf() {
        if (safDirUri == null) return null;
        
        try {
            ContentResolver resolver = context.getContentResolver();
            
            // 查找配置文件
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                safDirUri, DocumentsContract.getTreeDocumentId(safDirUri));
            
            Cursor cursor = resolver.query(childrenUri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, 
                             DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null);
            
            if (cursor == null) return null;
            
            Uri configFileUri = null;
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (name.equals(CONFIG_FILE_NAME)) {
                    String docId = cursor.getString(0);
                    configFileUri = DocumentsContract.buildDocumentUriUsingTree(safDirUri, docId);
                    break;
                }
            }
            cursor.close();
            
            if (configFileUri == null) return null;
            
            // 读取内容
            InputStream is = resolver.openInputStream(configFileUri);
            if (is == null) return null;
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "从 SAF 加载失败: " + e.getMessage());
            return null;
        }
    }
    
    private void saveToSaf(String content) throws Exception {
        if (safDirUri == null) throw new Exception("SAF 目录未设置");
        
        Log.i(TAG, "开始保存到 SAF，目录 URI: " + safDirUri);
        
        ContentResolver resolver = context.getContentResolver();
        
        // 查找并删除旧文件
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            safDirUri, DocumentsContract.getTreeDocumentId(safDirUri));
        
        Log.i(TAG, "查询现有文件，childrenUri: " + childrenUri);
        
        Cursor cursor = resolver.query(childrenUri,
            new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, 
                         DocumentsContract.Document.COLUMN_DISPLAY_NAME},
            null, null, null);
        
        Uri existingFileUri = null;
        if (cursor != null) {
            Log.i(TAG, "Cursor 返回 " + cursor.getCount() + " 行");
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                Log.i(TAG, "发现文件: " + name);
                if (name.equals(CONFIG_FILE_NAME)) {
                    String docId = cursor.getString(0);
                    existingFileUri = DocumentsContract.buildDocumentUriUsingTree(safDirUri, docId);
                    Log.i(TAG, "找到现有配置文件: " + existingFileUri);
                    break;
                }
            }
            cursor.close();
        } else {
            Log.w(TAG, "Cursor 为空，无法查询目录内容");
        }
        
        if (existingFileUri != null) {
            Log.i(TAG, "删除现有文件...");
            boolean deleted = DocumentsContract.deleteDocument(resolver, existingFileUri);
            Log.i(TAG, "删除结果: " + deleted);
        }
        
        // 创建新文件
        Log.i(TAG, "创建新配置文件...");
        // 使用 text/plain MIME 类型，兼容性更好
        Uri newFileUri = DocumentsContract.createDocument(
            resolver, safDirUri, "text/plain", CONFIG_FILE_NAME);

        if (newFileUri == null) {
            Log.e(TAG, "创建文件失败，newFileUri 为空");
            throw new Exception("创建文件失败");
        }

        Log.i(TAG, "新文件 URI: " + newFileUri);

        // 写入内容
        OutputStream os = null;
        try {
            os = resolver.openOutputStream(newFileUri);
            if (os == null) {
                Log.e(TAG, "打开输出流失败");
                throw new Exception("打开输出流失败");
            }

            Log.i(TAG, "写入内容，长度: " + content.length());
            os.write(content.getBytes("UTF-8"));
            os.flush();
            Log.i(TAG, "保存到 SAF 成功");
        } finally {
            if (os != null) {
                try { os.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }
    
    // ============ 外部存储操作（Android 4.0-4.3） ============
    
    private JSONObject loadFromExternalStorage() {
        try {
            File configDir = getLegacyConfigDir();
            if (configDir == null) return null;
            
            File configFile = new File(configDir, CONFIG_FILE_NAME);
            if (!configFile.exists()) return null;
            
            FileInputStream fis = new FileInputStream(configFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "从外部存储加载失败: " + e.getMessage());
            return null;
        }
    }
    
    private void saveToExternalStorage(String content) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore API，无需 WRITE_EXTERNAL_STORAGE 权限
            saveToMediaStore(content);
        } else {
            // Android 9 及以下使用传统文件写入
            saveToFileSystem(content);
        }
    }

    /**
     * Android 10+ 通过 MediaStore 写入 Downloads 目录
     */
    private void saveToMediaStore(String content) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, CONFIG_FILE_NAME);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
        values.put(MediaStore.Downloads.RELATIVE_PATH, LEGACY_CONFIG_DIR);

        // 先删除旧文件
        ContentResolver resolver = context.getContentResolver();
        try {
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Downloads.DISPLAY_NAME + " = ? AND " + MediaStore.Downloads.RELATIVE_PATH + " = ?",
                new String[]{CONFIG_FILE_NAME, LEGACY_CONFIG_DIR}
            );
        } catch (Exception e) {
            Log.w(TAG, "删除旧 MediaStore 文件失败: " + e.getMessage());
        }

        // 插入新文件
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new Exception("MediaStore insert 返回 null，无法创建文件");
        }

        OutputStream os = null;
        try {
            os = resolver.openOutputStream(uri);
            if (os == null) {
                throw new Exception("打开 MediaStore 输出流失败");
            }
            os.write(content.getBytes("UTF-8"));
            os.flush();
            Log.i(TAG, "[MediaStore] 文件已写入: " + uri + " (" + content.length() + " bytes)");
        } finally {
            if (os != null) {
                try { os.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    /**
     * Android 9 及以下直接写文件系统
     */
    private void saveToFileSystem(String content) throws Exception {
        File configDir = getLegacyConfigDir();
        if (configDir == null) throw new Exception("无法访问外部存储");

        if (!configDir.exists()) {
            boolean created = configDir.mkdirs();
            if (!created) {
                throw new Exception("无法创建目录: " + configDir.getAbsolutePath());
            }
        }

        File configFile = new File(configDir, CONFIG_FILE_NAME);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(configFile);
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            Log.i(TAG, "[文件系统] 文件已写入: " + configFile.getAbsolutePath() + " (" + content.length() + " bytes)");
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception e) { /* ignore */ }
            }
        }

        refreshMediaStore(configFile);
    }
    
    private File getLegacyConfigDir() {
        // Android 10+ 使用公共 Downloads 目录（文件管理器可见）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir != null) {
                return new File(downloadsDir, LEGACY_CONFIG_DIR);
            }
        }
        
        // Android 4.0-9 使用公共外部存储根目录
        File storageDir = Environment.getExternalStorageDirectory();
        if (storageDir != null && storageDir.canWrite()) {
            return new File(storageDir, LEGACY_CONFIG_DIR);
        }
        
        return null;
    }
    
    // ============ SharedPreferences 操作 ============
    
    private JSONObject loadFromSharedPreferences() {
        String configJson = prefs.getString(KEY_CONFIG, null);
        if (configJson == null || configJson.isEmpty()) {
            return new JSONObject();
        }
        
        try {
            return new JSONObject(configJson);
        } catch (JSONException e) {
            Log.e(TAG, "解析 SharedPreferences 配置失败: " + e.getMessage());
            return new JSONObject();
        }
    }
    
    /**
     * 清除所有配置
     */
    public void clearConfig() {
        // 清除 SAF 文件
        if (safAvailable && safDirUri != null) {
            try {
                saveToSaf(new JSONObject().toString());
            } catch (Exception e) {
                Log.e(TAG, "清除 SAF 配置失败: " + e.getMessage());
            }
        }
        
        // 清除外部存储文件
        try {
            File configDir = getLegacyConfigDir();
            if (configDir != null) {
                File configFile = new File(configDir, CONFIG_FILE_NAME);
                if (configFile.exists()) {
                    configFile.delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "清除外部存储配置失败: " + e.getMessage());
        }
        
        // 清除 SharedPreferences
        prefs.edit().clear().apply();

        Log.i(TAG, "所有配置已清除");
    }


    /**
     * 刷新 MediaStore（File 版本）
     */
    private void refreshMediaStore(File file) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    new String[]{file.getAbsolutePath()},
                    new String[]{"text/plain"},
                    null
                );
            }
        } catch (Exception e) {
            Log.w(TAG, "刷新 MediaStore 失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置存储位置描述
     */
    public String getStorageLocation() {
        if (safAvailable && safDirUri != null) {
            return "SAF: " + safDirUri.toString();
        }
        
        File configDir = getLegacyConfigDir();
        if (configDir != null) {
            return "外部存储: " + configDir.getAbsolutePath();
        }
        
        return "SharedPreferences";
    }
}


