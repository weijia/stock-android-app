package com.stock.app.model;

import java.util.List;

/**
 * 分时数据模型
 */
public class IntradayData {
    private String code;            // 股票代码
    private String date;            // 数据日期
    private int count;              // 数据条数
    private double preClose;        // 昨收价
    private List<IntradayPoint> data;  // 分时数据点列表

    public IntradayData() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public double getPreClose() {
        return preClose;
    }

    public void setPreClose(double preClose) {
        this.preClose = preClose;
    }

    public List<IntradayPoint> getData() {
        return data;
    }

    public void setData(List<IntradayPoint> data) {
        this.data = data;
    }

    /**
     * 获取最高价格
     */
    public double getMaxPrice() {
        if (data == null || data.isEmpty()) return preClose;
        double max = preClose;
        for (IntradayPoint p : data) {
            if (p.getPrice() > max) max = p.getPrice();
        }
        return max;
    }

    /**
     * 获取最低价格
     */
    public double getMinPrice() {
        if (data == null || data.isEmpty()) return preClose;
        double min = preClose;
        for (IntradayPoint p : data) {
            if (p.getPrice() < min) min = p.getPrice();
        }
        return min;
    }

    /**
     * 获取最大成交量
     */
    public double getMaxVolume() {
        if (data == null || data.isEmpty()) return 0;
        double max = 0;
        for (IntradayPoint p : data) {
            if (p.getVolume() > max) max = p.getVolume();
        }
        return max;
    }

    /**
     * 获取最后价格
     */
    public double getLastPrice() {
        if (data == null || data.isEmpty()) return preClose;
        return data.get(data.size() - 1).getPrice();
    }

    /**
     * 计算涨跌幅
     */
    public double getChangePct() {
        if (preClose == 0) return 0;
        return (getLastPrice() - preClose) / preClose * 100;
    }
}