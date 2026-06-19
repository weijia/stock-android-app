package com.stock.app.service;

import android.os.Handler;
import android.os.Looper;

import com.stock.app.model.KLineData;
import com.stock.app.model.StockData;
import com.stock.app.network.HttpClient;
import com.stock.app.parser.JsonParser;
import com.stock.app.util.ConfigManager;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 股票数据服务
 * 封装网络请求和数据解析
 */
public class StockService {
    private HttpClient httpClient;
    private JsonParser jsonParser;
    private ConfigManager configManager;
    private ExecutorService executorService;
    private Handler mainHandler;

    public StockService(ConfigManager configManager) {
        this.configManager = configManager;
        this.httpClient = new HttpClient();
        this.jsonParser = new JsonParser();
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
     * 获取实时行情数据
     * @param code 股票代码
     * @param callback 回调
     */
    public void fetchRealtime(String code, final DataCallback<StockData> callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = configManager.getRealtimeUrl(code);
                    String response = httpClient.get(url);
                    StockData data = jsonParser.parseRealtime(response);
                    data.setCode(code);

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
                    String url = configManager.getKlineUrl(code, days);
                    String response = httpClient.get(url);
                    List<KLineData> data = jsonParser.parseKline(response);

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
     * 检查服务器健康状态
     * @param callback 回调
     */
    public void checkHealth(final DataCallback<Boolean> callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = configManager.getHealthUrl();
                    String response = httpClient.get(url);
                    boolean healthy = jsonParser.parseHealth(response);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(healthy);
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure(e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /**
     * 测试服务器连接
     * @param ip IP 地址
     * @param port 端口
     * @param callback 回调
     */
    public void testConnection(String ip, int port, final DataCallback<Boolean> callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "http://" + ip + ":" + port + "/api/health";
                    String response = httpClient.get(url);
                    boolean healthy = jsonParser.parseHealth(response);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(healthy);
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure(e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /**
     * 关闭服务，释放资源
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}