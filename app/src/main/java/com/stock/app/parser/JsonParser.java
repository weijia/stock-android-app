package com.stock.app.parser;

import com.stock.app.model.IntradayData;
import com.stock.app.model.IntradayPoint;
import com.stock.app.model.KLineData;
import com.stock.app.model.StockData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 解析器
 * 使用 Android 内置 JSONObject，兼容 API Level 14
 */
public class JsonParser {

    /**
     * 解析实时行情数据
     * @param json JSON 字符串
     * @return 股票数据
     * @throws JSONException 解析异常
     */
    public StockData parseRealtime(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int code = root.getInt("code");
        if (code != 200) {
            throw new JSONException("API error: " + code);
        }

        JSONObject data = root.getJSONObject("data");
        StockData stock = new StockData();

        stock.setName(data.optString("name", ""));
        stock.setPrice(data.optDouble("price", 0));
        stock.setLastClose(data.optDouble("last_close", 0));
        stock.setOpen(data.optDouble("open", 0));
        stock.setHigh(data.optDouble("high", 0));
        stock.setLow(data.optDouble("low", 0));
        stock.setChangeAmt(data.optDouble("change_amt", 0));
        stock.setChangePct(data.optDouble("change_pct", 0));
        stock.setVolume(data.optDouble("volume", 0));
        stock.setAmount(data.optDouble("amount", 0));
        stock.setTimestamp(root.optString("timestamp", ""));

        return stock;
    }

    /**
     * 解析 K 线数据
     * @param json JSON 字符串
     * @return K 线数据列表
     * @throws JSONException 解析异常
     */
    public List<KLineData> parseKline(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int code = root.getInt("code");
        if (code != 200) {
            throw new JSONException("API error: " + code);
        }

        JSONObject dataObj = root.getJSONObject("data");
        JSONArray dataArray = dataObj.getJSONArray("data");

        List<KLineData> list = new ArrayList<>();
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject item = dataArray.getJSONObject(i);
            KLineData kline = new KLineData();

            kline.setDate(item.optString("date", ""));
            kline.setOpen(item.optDouble("open", 0));
            kline.setHigh(item.optDouble("high", 0));
            kline.setLow(item.optDouble("low", 0));
            kline.setClose(item.optDouble("close", 0));
            kline.setVolume(item.optDouble("volume", 0));

            list.add(kline);
        }

        return list;
    }

    /**
     * 解析健康检查响应
     * @param json JSON 字符串
     * @return 是否健康
     * @throws JSONException 解析异常
     */
    public boolean parseHealth(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int code = root.getInt("code");
        if (code != 200) {
            return false;
        }
        JSONObject data = root.getJSONObject("data");
        String status = data.optString("status", "");
        return "ok".equals(status);
    }

    /**
     * 解析分时数据
     * @param json JSON 字符串
     * @return 分时数据
     * @throws JSONException 解析异常
     */
    public IntradayData parseIntraday(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int code = root.getInt("code");
        if (code != 200) {
            throw new JSONException("API error: " + code);
        }

        IntradayData intraday = new IntradayData();
        intraday.setCode(root.optString("stock_code", ""));  // 使用 stock_code 字段
        intraday.setDate(root.optString("date", ""));
        intraday.setCount(root.optInt("count", 0));
        intraday.setPreClose(root.optDouble("pre_close", 0));

        JSONArray dataArray = root.optJSONArray("data");
        if (dataArray != null) {
            List<IntradayPoint> points = new ArrayList<>();
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                IntradayPoint point = new IntradayPoint();

                point.setTime(item.optString("time", ""));
                point.setPrice(item.optDouble("price", 0));
                point.setVolume(item.optDouble("volume", 0));
                point.setAvgPrice(item.optDouble("avg_price", 0));

                points.add(point);
            }
            intraday.setData(points);
        }

        return intraday;
    }
}