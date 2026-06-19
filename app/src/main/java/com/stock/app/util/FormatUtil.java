package com.stock.app.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 格式化工具类
 */
public class FormatUtil {

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    /**
     * 格式化时间
     * @param timestamp 时间戳字符串
     * @return 格式化后的时间
     */
    public static String formatTime(String timestamp) {
        try {
            // ISO 8601 格式: 2026-06-19T10:00:00
            if (timestamp != null && timestamp.contains("T")) {
                return timestamp.substring(timestamp.indexOf("T") + 1);
            }
            return timestamp;
        } catch (Exception e) {
            return timestamp;
        }
    }

    /**
     * 格式化当前时间
     * @return 当前时间字符串
     */
    public static String formatCurrentTime() {
        return TIME_FORMAT.format(new Date());
    }

    /**
     * 格式化价格
     * @param price 价格
     * @return 格式化后的价格
     */
    public static String formatPrice(double price) {
        return String.format("%.2f", price);
    }

    /**
     * 格式化涨跌幅
     * @param changePct 涨跌幅
     * @return 格式化后的涨跌幅
     */
    public static String formatChangePct(double changePct) {
        return String.format("%.2f%%", changePct);
    }

    /**
     * 格式化成交量（万手）
     * @param volume 成交量（手）
     * @return 格式化后的成交量
     */
    public static String formatVolume(double volume) {
        return String.format("%.2f万手", volume / 10000);
    }

    /**
     * 格式化成交额（万元）
     * @param amount 成交额（万元）
     * @return 格式化后的成交额
     */
    public static String formatAmount(double amount) {
        return String.format("%.2f万元", amount);
    }

    /**
     * 验证股票代码格式
     * @param code 股票代码
     * @return 是否有效
     */
    public static boolean isValidStockCode(String code) {
        if (code == null || code.length() != 6) {
            return false;
        }
        for (char c : code.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
}