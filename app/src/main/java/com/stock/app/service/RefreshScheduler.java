package com.stock.app.service;

import android.os.Handler;
import android.os.Looper;

import com.stock.app.model.StockData;
import com.stock.app.util.DebugLogger;

import java.util.Calendar;

/**
 * 定时刷新调度器
 * 使用 Handler + Runnable 实现，兼容 API Level 14
 * 
 * 开市时间持续刷新，闭市时间只查询一次
 */
public class RefreshScheduler {
    private static final String TAG = "RefreshScheduler";
    private static final long DEFAULT_REFRESH_INTERVAL = 60000; // 默认60秒

    private Handler handler;
    private Runnable refreshTask;
    private boolean isRunning = false;
    private StockService stockService;
    private String currentCode;
    private RefreshCallback callback;
    private long refreshInterval = DEFAULT_REFRESH_INTERVAL;
    private boolean isInMarketTime = false;  // 是否在开市时间

    /**
     * 刷新回调接口
     */
    public interface RefreshCallback {
        void onRefreshSuccess(StockData data);
        void onRefreshFailure(String error);
        void onRefreshStart();
    }

    public RefreshScheduler(StockService stockService) {
        this.handler = new Handler(Looper.getMainLooper());
        this.stockService = stockService;
    }
    
    /**
     * 判断当前是否在开市时间
     * 开市时间：
     * - 上午：9:30 - 11:30
     * - 下午：13:00 - 15:00
     * 
     * 持续刷新时间（提前10分钟开始）：
     * - 上午：9:10 - 11:30
     * - 下午：13:00 - 15:01
     * 
     * 闭市时间（只查询一次）：
     * - 9:10 之前
     * - 11:31 - 12:58
     * - 15:01 之后
     */
    public static boolean isInMarketTime() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int totalMinutes = hour * 60 + minute;
        
        // 上午开市时间段：9:10 - 11:30 (550 - 690 分钟)
        boolean morningSession = totalMinutes >= 550 && totalMinutes <= 690;
        
        // 下午开市时间段：13:00 - 15:01 (780 - 901 分钟)
        boolean afternoonSession = totalMinutes >= 780 && totalMinutes <= 901;
        
        return morningSession || afternoonSession;
    }
    
    /**
     * 获取开市状态描述
     */
    public static String getMarketTimeStatus() {
        if (isInMarketTime()) {
            return "开市中，持续刷新";
        } else {
            return "闭市中，仅查询一次";
        }
    }

    /**
     * 启动定时刷新
     * 开市时间持续刷新，闭市时间只查询一次
     * @param code 股票代码
     * @param callback 回调
     */
    public void start(String code, RefreshCallback callback) {
        this.currentCode = code;
        this.callback = callback;
        isRunning = true;
        isInMarketTime = isInMarketTime();
        
        DebugLogger logger = DebugLogger.getInstance();
        if (logger != null) {
            logger.log(TAG, "启动刷新，股票: " + code);
            logger.log(TAG, "当前时间状态: " + getMarketTimeStatus());
        }

        refreshTask = new Runnable() {
            @Override
            public void run() {
                if (isRunning && currentCode != null && !currentCode.isEmpty()) {
                    // 检查当前是否在开市时间
                    boolean currentInMarket = isInMarketTime();
                    
                    // 执行刷新
                    doRefresh();
                    
                    // 只有在开市时间才继续定时刷新
                    if (currentInMarket) {
                        handler.postDelayed(this, refreshInterval);
                        if (!isInMarketTime) {
                            // 刚进入开市时间
                            isInMarketTime = true;
                            if (logger != null) {
                                logger.log(TAG, "进入开市时间，开始持续刷新");
                            }
                        }
                    } else {
                        // 闭市时间，停止定时刷新（已经查询了一次）
                        isInMarketTime = false;
                        if (logger != null) {
                            logger.log(TAG, "闭市时间，停止持续刷新（已查询一次）");
                        }
                    }
                }
            }
        };

        // 立即执行一次
        handler.post(refreshTask);
    }

    /**
     * 设置刷新间隔（毫秒）
     * @param intervalMs 刷新间隔，单位毫秒
     */
    public void setInterval(long intervalMs) {
        if (intervalMs < 1000) {
            intervalMs = 1000; // 最小1秒
        }
        this.refreshInterval = intervalMs;
    }

    /**
     * 获取当前刷新间隔（毫秒）
     * @return 刷新间隔
     */
    public long getInterval() {
        return refreshInterval;
    }

    /**
     * 停止定时刷新
     */
    public void stop() {
        isRunning = false;
        if (refreshTask != null) {
            handler.removeCallbacks(refreshTask);
        }
    }

    /**
     * 检查是否正在运行
     * @return 是否运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 更新股票代码
     * @param code 新的股票代码
     */
    public void updateCode(String code) {
        this.currentCode = code;
    }

    /**
     * 执行刷新
     */
    private void doRefresh() {
        if (callback != null) {
            callback.onRefreshStart();
        }

        stockService.fetchRealtime(currentCode, new StockService.DataCallback<StockData>() {
            @Override
            public void onSuccess(StockData data) {
                if (callback != null) {
                    callback.onRefreshSuccess(data);
                }
            }

            @Override
            public void onFailure(String error) {
                if (callback != null) {
                    callback.onRefreshFailure(error);
                }
            }
        });
    }

    /**
     * 立即刷新一次（不等待定时）
     */
    public void refreshNow() {
        if (currentCode != null && !currentCode.isEmpty()) {
            doRefresh();
        }
    }
}
