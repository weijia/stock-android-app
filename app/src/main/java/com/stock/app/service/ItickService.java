package com.stock.app.service;

import android.os.Handler;
import android.os.Looper;

import com.stock.app.model.IntradayData;
import com.stock.app.model.IntradayPoint;
import com.stock.app.model.KLineData;
import com.stock.app.model.StockData;
import com.stock.app.network.HttpClient;
import com.stock.app.util.ConfigManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * iTick 数据服务
 * 使用 iTick REST API 获取股票数据
 * 
 * API 文档：https://docs.itick.org/zh-cn/websocket/stocks
 */
public class ItickService {
    private HttpClient httpClient;
    private ConfigManager configManager;
    private ExecutorService executorService;
    private Handler mainHandler;

    public ItickService(ConfigManager configManager) {
        this.configManager = configManager;
        this.httpClient = new HttpClient();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 数据回调接口
     */
    public interface DataCallback<T> {
        void onSuccess(T data);
        void onFailure(String error);
    }

    /**
     * 执行带 Token 的 GET 请求
     */
    private String getWithToken(String url) throws IOException {
        String token = configManager.getItickToken();
        return httpClient.getWithHeader(url, "token", token);
    }

    /**
     * 获取实时行情数据
     * @param code 股票代码
     * @param callback 回调
     */
    public void fetchRealtime(String code, final DataCallback<StockData> callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = buildRealtimeUrl(code);
                    String response = getWithToken(url);
                    StockData data = parseRealtimeResponse(response, code);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(data);
                        }
                    });
                } catch (IOException e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure("网络错误: " + e.getMessage());
                        }
                    });
                } catch (JSONException e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure("数据解析错误: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /**
     * 获取 K 线数据
     * @param code 股票代码
     * @param days 天数
     * @param callback 回调
     */
    public void fetchKline(String code, int days, final DataCallback<List<KLineData>> callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = buildKlineUrl(code, days);
                    String response = getWithToken(url);
                    List<KLineData> data = parseKlineResponse(response);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(data);
                        }
                    });
                } catch (IOException e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure("网络错误: " + e.getMessage());
                        }
                    });
                } catch (JSONException e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure("数据解析错误: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /**
     * 测试 iTick Token 是否有效
     * @param token iTick Token
     * @param callback 回调
     */
    public void testToken(String token, final DataCallback<Boolean> callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 使用一个简单请求测试 Token
                    String url = ConfigManager.ITICK_API_URL + "/stock/quote?symbol=AAPL&region=US";
                    
                    // 添加 Token 到请求头
                    String response = httpClient.getWithHeader(url, "token", token);
                    JSONObject root = new JSONObject(response);
                    
                    boolean valid = root.optInt("code", 0) == 1;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(valid);
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(false);
                        }
                    });
                }
            }
        });
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // ============ URL 构建 ============

    /**
     * 构建实时行情 URL
     */
    private String buildRealtimeUrl(String code) {
        String region = configManager.getItickRegion();
        String symbol = convertSymbol(code, region);
        return ConfigManager.ITICK_API_URL + "/stock/quote?symbol=" + symbol + "&region=" + region;
    }

    /**
     * 构建K线 URL
     */
    private String buildKlineUrl(String code, int days) {
        String region = configManager.getItickRegion();
        String symbol = convertSymbol(code, region);
        // iTick K线周期：1分钟、5分钟、15分钟、30分钟、1小时、1天
        // 使用日线数据
        return ConfigManager.ITICK_API_URL + "/stock/kline?symbol=" + symbol + "&region=" + region + "&type=8&limit=" + days;
    }

    /**
     * 转换股票代码格式
     * A股：000001 -> 000001$SZ 或 600000$SH
     */
    private String convertSymbol(String code, String region) {
        if ("CN".equals(region)) {
            // A股代码判断交易所
            if (code.startsWith("6")) {
                return code + "$SH";  // 上海
            } else if (code.startsWith("0") || code.startsWith("3")) {
                return code + "$SZ";  // 深圳
            } else if (code.startsWith("68")) {
                return code + "$SH";  // 科创板
            }
            return code + "$SZ";  // 默认深圳
        }
        // 美股和港股直接使用代码
        return code;
    }

    // ============ 数据解析 ============

    /**
     * 解析实时行情响应
     */
    private StockData parseRealtimeResponse(String json, String code) throws JSONException {
        JSONObject root = new JSONObject(json);
        
        if (root.optInt("code", 0) != 1) {
            throw new JSONException("API error: " + root.optString("msg", "unknown"));
        }

        JSONObject data = root.getJSONObject("data");
        StockData stock = new StockData();

        stock.setCode(code);
        stock.setName(data.optString("s", ""));
        stock.setPrice(data.optDouble("ld", 0));
        stock.setLastClose(data.optDouble("p", 0));
        stock.setOpen(data.optDouble("o", 0));
        stock.setHigh(data.optDouble("h", 0));
        stock.setLow(data.optDouble("l", 0));
        stock.setChangeAmt(data.optDouble("ch", 0));
        stock.setChangePct(data.optDouble("chp", 0));
        stock.setVolume(data.optDouble("v", 0));
        stock.setAmount(data.optDouble("tu", 0));

        return stock;
    }

    /**
     * 解析K线响应
     */
    private List<KLineData> parseKlineResponse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        
        if (root.optInt("code", 0) != 1) {
            throw new JSONException("API error: " + root.optString("msg", "unknown"));
        }

        JSONArray dataArray = root.optJSONArray("data");
        if (dataArray == null) {
            return new ArrayList<>();
        }

        List<KLineData> list = new ArrayList<>();
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject item = dataArray.getJSONObject(i);
            KLineData kline = new KLineData();

            // 时间戳转换
            long timestamp = item.optLong("t", 0);
            String date = formatDate(timestamp);
            kline.setDate(date);
            kline.setOpen(item.optDouble("o", 0));
            kline.setHigh(item.optDouble("h", 0));
            kline.setLow(item.optDouble("l", 0));
            kline.setClose(item.optDouble("c", 0));
            kline.setVolume(item.optDouble("v", 0));

            list.add(kline);
        }

        return list;
    }

    /**
     * 格式化时间戳为日期
     */
    private String formatDate(long timestamp) {
        if (timestamp == 0) return "";
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}