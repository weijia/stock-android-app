package com.stock.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 调试日志收集器（单例）
 *
 * 收集 APP 运行过程中的所有调试日志，包括：
 * - 启动流程
 * - 服务器连接
 * - 节点配置同步
 * - 股票查询
 * - HTTP 请求/响应
 *
 * 日志保存在 SharedPreferences 中，在设置界面可以查看。
 */
public class DebugLogger {

    private static final String PREF_NAME = "stock_debug_logs";
    private static final String KEY_LOGS = "logs";
    private static final int MAX_LOGS = 1000; // 最多保存 1000 条日志

    private static DebugLogger instance;

    private SharedPreferences prefs;
    private List<String> logList;
    private SimpleDateFormat timeFormat;
    private boolean dirty = false; // 是否有未保存的日志

    private DebugLogger(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        timeFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());
        logList = new ArrayList<>();
        loadFromPrefs();
    }

    public static synchronized DebugLogger getInstance(Context context) {
        if (instance == null && context != null) {
            instance = new DebugLogger(context);
        }
        return instance;
    }

    /**
     * 获取已初始化的实例（不创建新实例）
     */
    public static DebugLogger getInstance() {
        return instance;
    }

    /**
     * 记录普通日志
     */
    public void log(String tag, String message) {
        if (logList == null) return;
        addLog("I", tag, message);
    }

    /**
     * 记录错误日志
     */
    public void error(String tag, String message) {
        if (logList == null) return;
        addLog("E", tag, message);
    }

    /**
     * 记录警告日志
     */
    public void warn(String tag, String message) {
        if (logList == null) return;
        addLog("W", tag, message);
    }

    private synchronized void addLog(String level, String tag, String message) {
        String timestamp = timeFormat.format(new Date());
        String logLine = timestamp + " [" + level + "/" + tag + "] " + message;
        logList.add(logLine);

        // 超过上限时删除旧日志
        while (logList.size() > MAX_LOGS) {
            logList.remove(0);
        }

        // 同时输出到 Android Logcat
        if ("E".equals(level)) {
            android.util.Log.e(tag, message);
        } else if ("W".equals(level)) {
            android.util.Log.w(tag, message);
        } else {
            android.util.Log.d(tag, message);
        }

        // 延迟保存，避免频繁 IO
        dirty = true;
        if (!pendingSave) {
            scheduleSave();
        }
    }

    private volatile boolean pendingSave = false;

    private void scheduleSave() {
        pendingSave = true;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (dirty) {
                    saveToPrefs();
                    dirty = false;
                }
                pendingSave = false;
            }
        }, 2000); // 2秒后批量保存
    }

    /**
     * 获取所有日志文本
     */
    public synchronized String getLogs() {
        StringBuilder sb = new StringBuilder();
        for (String log : logList) {
            sb.append(log).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取日志条数
     */
    public synchronized int getLogCount() {
        return logList.size();
    }

    /**
     * 清空日志
     */
    public synchronized void clear() {
        logList.clear();
        saveToPrefs();
    }

    private void loadFromPrefs() {
        String json = prefs.getString(KEY_LOGS, null);
        if (json != null && !json.isEmpty()) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    logList.add(arr.getString(i));
                }
            } catch (org.json.JSONException e) {
                // 解析失败，忽略
            }
        }
    }

    private synchronized void saveToPrefs() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String log : logList) {
                arr.put(log);
            }
            prefs.edit().putString(KEY_LOGS, arr.toString()).apply();
        } catch (Exception e) {
            // 忽略保存失败
        }
    }
}


