package com.stock.app.service;

import android.os.Handler;
import android.os.Looper;

import com.stock.app.model.StockData;

/**
 * 定时刷新调度器
 * 使用 Handler + Runnable 实现，兼容 API Level 14
 */
public class RefreshScheduler {
    private static final long REFRESH_INTERVAL = 60000; // 60秒

    private Handler handler;
    private Runnable refreshTask;
    private boolean isRunning = false;
    private StockService stockService;
    private String currentCode;
    private RefreshCallback callback;

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
     * 启动定时刷新
     * @param code 股票代码
     * @param callback 回调
     */
    public void start(String code, RefreshCallback callback) {
        this.currentCode = code;
        this.callback = callback;
        isRunning = true;

        refreshTask = new Runnable() {
            @Override
            public void run() {
                if (isRunning && currentCode != null && !currentCode.isEmpty()) {
                    doRefresh();
                    handler.postDelayed(this, REFRESH_INTERVAL);
                }
            }
        };

        // 立即执行一次
        handler.post(refreshTask);
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