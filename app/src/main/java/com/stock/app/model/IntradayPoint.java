package com.stock.app.model;

/**
 * 分时数据点模型
 */
public class IntradayPoint {
    private String time;        // 时间 HH:MM
    private double price;       // 价格
    private double volume;      // 成交量
    private double avgPrice;    // 均价

    public IntradayPoint() {
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(double avgPrice) {
        this.avgPrice = avgPrice;
    }
}