package com.stock.app.model;

/**
 * 股票实时行情数据模型
 */
public class StockData {
    private String code;        // 股票代码
    private String name;        // 股票名称
    private double price;       // 当前价格
    private double lastClose;   // 昨收价
    private double open;        // 开盘价
    private double high;        // 最高价
    private double low;         // 最低价
    private double changeAmt;   // 涨跌额
    private double changePct;   // 涨跌幅
    private double volume;      // 成交量（手）
    private double amount;      // 成交额（万元）
    private String timestamp;   // 时间戳

    public StockData() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getLastClose() {
        return lastClose;
    }

    public void setLastClose(double lastClose) {
        this.lastClose = lastClose;
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

    public double getChangeAmt() {
        return changeAmt;
    }

    public void setChangeAmt(double changeAmt) {
        this.changeAmt = changeAmt;
    }

    public double getChangePct() {
        return changePct;
    }

    public void setChangePct(double changePct) {
        this.changePct = changePct;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 判断是否上涨
     */
    public boolean isUp() {
        return changePct > 0;
    }

    /**
     * 判断是否下跌
     */
    public boolean isDown() {
        return changePct < 0;
    }

    /**
     * 获取格式化的成交量（万手）
     */
    public String getFormattedVolume() {
        return String.format("%.2f万手", volume / 10000);
    }

    /**
     * 获取格式化的成交额（万元）
     */
    public String getFormattedAmount() {
        return String.format("%.2f万元", amount);
    }

    /**
     * 获取格式化的涨跌幅
     */
    public String getFormattedChangePct() {
        return String.format("%.2f%%", changePct);
    }

    /**
     * 获取格式化的涨跌额
     */
    public String getFormattedChangeAmt() {
        return String.format("%.2f", changeAmt);
    }

    /**
     * 获取格式化的价格
     */
    public String getFormattedPrice() {
        return String.format("%.2f", price);
    }
}