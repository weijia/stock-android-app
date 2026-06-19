package com.stock.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 配置管理器
 * 使用 SharedPreferences 存储配置，兼容 API Level 14
 */
public class ConfigManager {
    private static final String PREF_NAME = "stock_app_config";
    
    // 本地服务器配置
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String KEY_SERVER_PORT = "server_port";
    private static final String KEY_LAST_CODE = "last_code";
    
    // iTick 配置
    private static final String KEY_DATA_SOURCE = "data_source";  // "local" 或 "itick"
    private static final String KEY_ITICK_TOKEN = "itick_token";
    private static final String KEY_ITICK_REGION = "itick_region";  // "CN", "US", "HK"

    private SharedPreferences prefs;

    public ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ============ 数据源配置 ============
    
    /**
     * 获取数据源类型
     * @return "local" 或 "itick"
     */
    public String getDataSource() {
        return prefs.getString(KEY_DATA_SOURCE, "local");
    }
    
    /**
     * 设置数据源类型
     * @param source "local" 或 "itick"
     */
    public void setDataSource(String source) {
        prefs.edit().putString(KEY_DATA_SOURCE, source).apply();
    }
    
    /**
     * 是否使用 iTick 数据源
     * @return 是否使用 iTick
     */
    public boolean useItick() {
        return "itick".equals(getDataSource());
    }

    // ============ 本地服务器配置 ============

    /**
     * 获取服务器 IP
     * @return 服务器 IP 地址
     */
    public String getServerIp() {
        return prefs.getString(KEY_SERVER_IP, "localhost");
    }

    /**
     * 设置服务器 IP
     * @param ip IP 地址
     */
    public void setServerIp(String ip) {
        prefs.edit().putString(KEY_SERVER_IP, ip).apply();
    }

    /**
     * 获取服务器端口
     * @return 端口号
     */
    public int getServerPort() {
        return prefs.getInt(KEY_SERVER_PORT, 8080);
    }

    /**
     * 设置服务器端口
     * @param port 端口号
     */
    public void setServerPort(int port) {
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply();
    }

    /**
     * 获取上次查询的股票代码
     * @return 股票代码
     */
    public String getLastCode() {
        return prefs.getString(KEY_LAST_CODE, "");
    }

    /**
     * 设置上次查询的股票代码
     * @param code 股票代码
     */
    public void setLastCode(String code) {
        prefs.edit().putString(KEY_LAST_CODE, code).apply();
    }

    /**
     * 获取服务器基础 URL
     * @return URL 字符串
     */
    public String getBaseUrl() {
        return "http://" + getServerIp() + ":" + getServerPort();
    }

    /**
     * 获取实时行情 API URL
     * @param code 股票代码
     * @return URL 字符串
     */
    public String getRealtimeUrl(String code) {
        return getBaseUrl() + "/api/realtime/" + code;
    }

    /**
     * 获取 K 线数据 API URL
     * @param code 股票代码
     * @param days 天数
     * @return URL 字符串
     */
    public String getKlineUrl(String code, int days) {
        return getBaseUrl() + "/api/kline/" + code + "?days=" + days;
    }

    /**
     * 获取健康检查 API URL
     * @return URL 字符串
     */
    public String getHealthUrl() {
        return getBaseUrl() + "/api/health";
    }

    /**
     * 获取分时数据 API URL
     * @param code 股票代码
     * @return URL 字符串
     */
    public String getIntradayUrl(String code) {
        return getBaseUrl() + "/api/intraday/" + code;
    }

    /**
     * 保存服务器配置
     * @param ip IP 地址
     * @param port 端口号
     */
    public void saveServerConfig(String ip, int port) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_SERVER_IP, ip);
        editor.putInt(KEY_SERVER_PORT, port);
        editor.apply();
    }

    // ============ iTick 配置 ============

    /**
     * 获取 iTick Token
     * @return iTick API Token
     */
    public String getItickToken() {
        return prefs.getString(KEY_ITICK_TOKEN, "");
    }

    /**
     * 设置 iTick Token
     * @param token iTick API Token
     */
    public void setItickToken(String token) {
        prefs.edit().putString(KEY_ITICK_TOKEN, token).apply();
    }

    /**
     * 获取 iTick 市场区域
     * @return 区域代码：CN, US, HK
     */
    public String getItickRegion() {
        return prefs.getString(KEY_ITICK_REGION, "CN");
    }

    /**
     * 设置 iTick 市场区域
     * @param region 区域代码：CN, US, HK
     */
    public void setItickRegion(String region) {
        prefs.edit().putString(KEY_ITICK_REGION, region).apply();
    }

    /**
     * 保存 iTick 配置
     * @param token iTick Token
     * @param region 市场区域
     */
    public void saveItickConfig(String token, String region) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ITICK_TOKEN, token);
        editor.putString(KEY_ITICK_REGION, region);
        editor.apply();
    }

    /**
     * iTick API 基础 URL
     */
    public static final String ITICK_API_URL = "https://api.itick.org";
}