package com.stock.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点配置管理器
 * 负责从服务器获取节点配置、本地缓存、增量同步
 *
 * 配置优先级：
 * 1. 服务器配置（在线时）
 * 2. 本地缓存（离线时）
 * 3. 内置默认配置（首次启动且无法连接服务器）
 */
public class NodeConfigManager {
    private static final String PREF_NAME = "stock_node_config";
    private static final String KEY_CONFIG_JSON = "config_json";
    private static final String KEY_PENDING_SYNC = "pending_sync";

    private SharedPreferences prefs;

    public NodeConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ==================== 服务器配置 ====================

    /**
     * 保存从服务器获取的完整配置
     */
    public void saveServerConfig(JSONObject config) {
        prefs.edit().putString(KEY_CONFIG_JSON, config.toString()).apply();
    }

    /**
     * 获取服务器配置（本地缓存）
     */
    public JSONObject getServerConfig() {
        String json = prefs.getString(KEY_CONFIG_JSON, null);
        if (json == null) return null;
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * 获取默认配置（首次启动使用）
     */
    public JSONObject getDefaultConfig() {
        try {
            JSONObject config = new JSONObject();
            config.put("node_name", "");
            config.put("watchlist", new JSONObject()
                .put("stocks", new JSONArray())
                .put("groups", new JSONArray()));
            config.put("refresh", new JSONObject()
                .put("realtime_interval_sec", 5)
                .put("auto_refresh", true));
            config.put("alert", new JSONObject()
                .put("price_change_threshold_pct", 2.0)
                .put("enabled", true)
                .put("quiet_hours", new JSONObject()
                    .put("start", "23:00")
                    .put("end", "09:00")));
            config.put("display", new JSONObject()
                .put("theme", "dark")
                .put("decimal_places", 2));
            return config;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // ==================== 快捷读取 ====================

    public List<String> getWatchlistStocks() {
        JSONObject config = getServerConfig();
        if (config == null) config = getDefaultConfig();
        try {
            JSONObject watchlist = config.optJSONObject("watchlist");
            if (watchlist == null) return new ArrayList<>();
            JSONArray stocks = watchlist.optJSONArray("stocks");
            if (stocks == null) return new ArrayList<>();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < stocks.length(); i++) {
                list.add(stocks.getString(i));
            }
            return list;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public String getTheme() {
        JSONObject config = getServerConfig();
        if (config == null) config = getDefaultConfig();
        JSONObject display = config.optJSONObject("display");
        if (display != null) {
            return display.optString("theme", "dark");
        }
        return "dark";
    }

    public int getRefreshIntervalSec() {
        JSONObject config = getServerConfig();
        if (config == null) config = getDefaultConfig();
        JSONObject refresh = config.optJSONObject("refresh");
        if (refresh != null) {
            return refresh.optInt("realtime_interval_sec", 5);
        }
        return 5;
    }

    public boolean isAutoRefreshEnabled() {
        JSONObject config = getServerConfig();
        if (config == null) config = getDefaultConfig();
        JSONObject refresh = config.optJSONObject("refresh");
        if (refresh != null) {
            return refresh.optBoolean("auto_refresh", true);
        }
        return true;
    }

    // ==================== 增量更新 ====================

    /**
     * 更新关注列表（增量）
     */
    public JSONObject buildWatchlistUpdate(List<String> stocks) {
        try {
            JSONArray arr = new JSONArray();
            for (String s : stocks) arr.put(s);
            return new JSONObject().put("watchlist",
                new JSONObject().put("stocks", arr));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /**
     * 更新主题（增量）
     */
    public JSONObject buildThemeUpdate(String theme) {
        try {
            return new JSONObject().put("display",
                new JSONObject().put("theme", theme));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /**
     * 更新刷新间隔（增量）
     */
    public JSONObject buildRefreshIntervalUpdate(int seconds) {
        try {
            return new JSONObject().put("refresh",
                new JSONObject().put("realtime_interval_sec", seconds));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // ==================== 待同步标记 ====================

    /**
     * 标记有待同步的配置
     */
    public void setPendingSync(boolean pending) {
        prefs.edit().putBoolean(KEY_PENDING_SYNC, pending).apply();
    }

    public boolean hasPendingSync() {
        return prefs.getBoolean(KEY_PENDING_SYNC, false);
    }
}