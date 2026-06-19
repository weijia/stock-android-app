package com.stock.app.model;

/**
 * K线数据模型
 */
public class KLineData {
    private String date;        // 日期 YYYY-MM-DD
    private double open;        // 开盘价
    private double high;        // 最高价
    private double low;         // 最低价
    private double close;       // 收盘价
    private double volume;      // 成交量

    public KLineData() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    /**
     * 获取简短日期显示（MM-DD格式）
     */
    public String getShortDate() {
        if (date != null && date.length() >= 10) {
            return date.substring(5, 10); // 取 MM-DD 部分
        }
        return date;
    }
}