package com.stock.app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;

import com.stock.app.model.IntradayData;
import com.stock.app.model.KLineData;
import com.stock.app.model.StockData;
import com.stock.app.service.AutoConnectManager;
import com.stock.app.service.MDNSDiscovery;
import com.stock.app.service.RefreshScheduler;
import com.stock.app.service.StockService;
import com.stock.app.util.ConfigManager;
import com.stock.app.util.ExternalStorageManager;
import com.stock.app.util.FormatUtil;
import com.stock.app.util.NodeConfigManager;
import com.stock.app.util.NodeIdentityManager;
import com.stock.app.util.DebugLogger;
import com.stock.app.view.IntradayChartView;
import com.stock.app.view.PriceChartView;
import com.stock.app.view.VolumeChartView;

import org.json.JSONObject;

import java.util.List;

/**
 * 主界面 Activity
 */
public class MainActivity extends Activity implements RefreshScheduler.RefreshCallback, AutoConnectManager.AutoConnectCallback {

    // UI 组件
    private EditText etStockCode;
    private Button btnQuery;
    private Button btnSettings;
    private Switch swKeepScreenOn;
    private LinearLayout stockInfoPanel;
    private LinearLayout chartPanel;
    private LinearLayout intradayPanel;
    private TextView tvStatus;
    private TextView tvRefreshTime;
    private TextView tvRefreshing;
    private TextView tvStockName;
    private TextView tvPrice;
    private TextView tvChangePct;
    private TextView tvChangeAmt;
    private TextView tvOpen;
    private TextView tvHigh;
    private TextView tvLow;
    private TextView tvLastClose;
    private TextView tvVolume;
    private TextView tvAmount;
    private TextView tvIntradayDate;
    private PriceChartView priceChart;
    private IntradayChartView intradayChart;

    // 服务组件
    private ConfigManager configManager;
    private ExternalStorageManager externalStorageManager;
    private StockService stockService;
    private RefreshScheduler refreshScheduler;
    private AutoConnectManager autoConnectManager;

    // 调试日志
    private DebugLogger debugLogger;

    // 节点配置管理
    private NodeIdentityManager nodeIdentityManager;
    private NodeConfigManager nodeConfigManager;

    // 当前股票代码
    private String currentCode = "";
    
    // 是否已完成自动连接
    private boolean autoConnectCompleted = false;

    // 是否已完成配置同步
    private boolean configSyncCompleted = false;

    // 防烧屏相关
    private android.os.Handler burnInProtectionHandler;
    private Runnable burnInProtectionTask;
    private int burnInOffset = 0;
    private static final int BURN_IN_INTERVAL = 60000; // 60秒执行一次防烧屏偏移
    private static final int BURN_IN_MAX_OFFSET = 3;   // 最大偏移3像素

    // 节点配置定时同步
    private android.os.Handler nodeConfigSyncHandler;
    private Runnable nodeConfigSyncTask;
    private static final int NODE_CONFIG_SYNC_INTERVAL = 300000; // 5分钟同步一次节点配置（与 API 规范一致）

    // 屏幕常亮开关持久化
    private static final String PREF_KEEP_SCREEN_ON = "keep_screen_on";

    // 交易时间检查（15:00 后自动关闭屏幕常亮）
    private android.os.Handler tradingHoursCheckHandler;
    private Runnable tradingHoursCheckTask;
    private static final int TRADING_HOURS_CHECK_INTERVAL = 60000; // 每分钟检查一次

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化调试日志
        debugLogger = DebugLogger.getInstance(this);
        debugLogger.log("App", "=== APP 启动 ===");

        // 初始化外部存储管理器
        externalStorageManager = new ExternalStorageManager(this);

        // 初始化节点身份和配置管理
        nodeIdentityManager = new NodeIdentityManager(this);
        nodeConfigManager = new NodeConfigManager(this);

        // 初始化配置和服务
        configManager = new ConfigManager(this);
        
        // 从外部存储加载配置（必须在 configManager 初始化之后）
        loadConfigFromExternalStorage();
        
        debugLogger.log("App", "服务器配置: " + configManager.getServerIp() + ":" + configManager.getServerPort());
        debugLogger.log("App", "本地 lastCode: " + configManager.getLastCode());

        stockService = new StockService(configManager);
        refreshScheduler = new RefreshScheduler(stockService);
        autoConnectManager = new AutoConnectManager(this, configManager, stockService);
        autoConnectManager.setCallback(this);

        // 初始化 UI
        initViews();

        // 设置按钮点击事件
        setupClickListeners();

        // 恢复屏幕常亮开关状态
        boolean keepScreenOn = getSharedPreferences("stock_prefs", MODE_PRIVATE)
            .getBoolean(PREF_KEEP_SCREEN_ON, false);
        swKeepScreenOn.setChecked(keepScreenOn);
        updateKeepScreenOn(keepScreenOn);

        // 恢复上次查询的股票代码
        String lastCode = configManager.getLastCode();
        if (!TextUtils.isEmpty(lastCode)) {
            etStockCode.setText(lastCode);
        }
        
        // 开始自动连接
        startAutoConnect();
        
        // 启动防烧屏保护
        startBurnInProtection();
        
        // 启动节点配置定时同步
        startNodeConfigSync();

        // 启动交易时间检查（15:00 后自动关闭屏幕常亮）
        startTradingHoursCheck();
    }
    
    /**
     * 开始自动连接流程
     */
    private void startAutoConnect() {
        debugLogger.log("Connect", "开始自动连接，目标: " + configManager.getServerIp() + ":" + configManager.getServerPort());
        tvStatus.setText("正在连接服务器...");
        tvStatus.setTextColor(Color.parseColor("#0d6efd"));
        autoConnectManager.startAutoConnect();
    }
    
    /**
     * 从外部存储加载配置
     */
    private void loadConfigFromExternalStorage() {
        try {
            JSONObject config = externalStorageManager.loadConfig();
            if (config != null && config.length() > 0) {
                // 恢复服务器配置
                String serverIp = config.optString("server_ip", "localhost");
                int serverPort = config.optInt("server_port", 8080);
                String lastCode = config.optString("last_code", "");
                
                // 保存到 SharedPreferences（供其他组件使用）
                configManager.setServerIp(serverIp);
                configManager.setServerPort(serverPort);
                if (!TextUtils.isEmpty(lastCode)) {
                    configManager.setLastCode(lastCode);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "加载配置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 保存配置到外部存储
     */
    private void saveConfigToExternalStorage() {
        try {
            JSONObject config = new JSONObject();
            config.put("server_ip", configManager.getServerIp());
            config.put("server_port", configManager.getServerPort());
            config.put("last_code", configManager.getLastCode());
            
            ExternalStorageManager.SaveResult result = externalStorageManager.saveConfig(config);
            
            if (!result.isExternalStorageSuccess()) {
                // 外部存储失败，记录日志
                android.util.Log.w("MainActivity", "外部存储保存失败，配置仅保存到应用内部");
                if (result.safError != null) {
                    android.util.Log.e("MainActivity", "SAF 错误: " + result.safError);
                }
                if (result.externalError != null) {
                    android.util.Log.e("MainActivity", "外部存储错误: " + result.externalError);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存配置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void initViews() {
        etStockCode = findViewById(R.id.et_stock_code);
        btnQuery = findViewById(R.id.btn_query);
        btnSettings = findViewById(R.id.btn_settings);
        swKeepScreenOn = findViewById(R.id.sw_keep_screen_on);
        stockInfoPanel = findViewById(R.id.stock_info_panel);
        chartPanel = findViewById(R.id.chart_panel);
        intradayPanel = findViewById(R.id.intraday_panel);
        tvStatus = findViewById(R.id.tv_status);
        tvRefreshTime = findViewById(R.id.tv_refresh_time);
        tvRefreshing = findViewById(R.id.tv_refreshing);
        tvStockName = findViewById(R.id.tv_stock_name);
        tvPrice = findViewById(R.id.tv_price);
        tvChangePct = findViewById(R.id.tv_change_pct);
        tvChangeAmt = findViewById(R.id.tv_change_amt);
        tvOpen = findViewById(R.id.tv_open);
        tvHigh = findViewById(R.id.tv_high);
        tvLow = findViewById(R.id.tv_low);
        tvLastClose = findViewById(R.id.tv_last_close);
        tvVolume = findViewById(R.id.tv_volume);
        tvAmount = findViewById(R.id.tv_amount);
        tvIntradayDate = findViewById(R.id.tv_intraday_date);
        priceChart = findViewById(R.id.price_chart);
        intradayChart = findViewById(R.id.intraday_chart);
    }

    private void setupClickListeners() {
        // 查询按钮
        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queryStock();
            }
        });

        // 设置按钮
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });

        // 屏幕常亮开关
        swKeepScreenOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateKeepScreenOn(isChecked);
                getSharedPreferences("stock_prefs", MODE_PRIVATE)
                    .edit().putBoolean(PREF_KEEP_SCREEN_ON, isChecked).apply();
            }
        });
    }

    /**
     * 更新屏幕常亮状态
     * 仅在 A 股交易时间内生效（工作日 9:30 - 15:00 北京时间）
     */
    private void updateKeepScreenOn(boolean keepOn) {
        if (keepOn && isInTradingHours()) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * 判断是否处于 A 股交易时间（工作日 9:30 - 15:00 北京时间）
     */
    private boolean isInTradingHours() {
        java.util.Calendar cal = java.util.Calendar.getInstance(
            java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
        // 周六或周日不开盘
        if (dayOfWeek == java.util.Calendar.SATURDAY
                || dayOfWeek == java.util.Calendar.SUNDAY) {
            return false;
        }
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = cal.get(java.util.Calendar.MINUTE);
        int time = hour * 100 + minute; // 例如 930 = 9:30, 1500 = 15:00
        return time >= 930 && time < 1500;
    }

    /**
     * 启动交易时间检查定时器
     * 每分钟检查一次，15:00 后自动关闭屏幕常亮
     */
    private void startTradingHoursCheck() {
        tradingHoursCheckHandler = new android.os.Handler();
        tradingHoursCheckTask = new Runnable() {
            @Override
            public void run() {
                if (swKeepScreenOn.isChecked() && !isInTradingHours()) {
                    // 过了交易时间，自动关闭屏幕常亮
                    getWindow().clearFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                tradingHoursCheckHandler.postDelayed(this, TRADING_HOURS_CHECK_INTERVAL);
            }
        };
        tradingHoursCheckHandler.post(tradingHoursCheckTask);
    }

    private void checkServerStatus() {
        stockService.checkHealth(new StockService.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean healthy) {
                if (healthy) {
                    tvStatus.setText(R.string.status_connected);
                    tvStatus.setTextColor(Color.parseColor("#20c997"));
                } else {
                    tvStatus.setText(R.string.status_disconnected);
                    tvStatus.setTextColor(Color.parseColor("#dc3545"));
                }
            }

            @Override
            public void onFailure(String error) {
                tvStatus.setText(R.string.status_disconnected);
                tvStatus.setTextColor(Color.parseColor("#dc3545"));
            }
        });
    }

    private void queryStock() {
        queryStock(true);
    }

    /**
     * 查询股票
     * @param syncToServer 是否同步到服务器（从服务器配置同步时不应该回写，避免污染服务器配置）
     */
    private void queryStock(boolean syncToServer) {
        String code = etStockCode.getText().toString().trim();
        debugLogger.log("Query", "queryStock: code=" + code + ", syncToServer=" + syncToServer + ", currentCode=" + currentCode);

        // 验证股票代码
        if (!FormatUtil.isValidStockCode(code)) {
            Toast.makeText(this, R.string.error_invalid_code, Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存股票代码
        configManager.setLastCode(code);
        currentCode = code;

        // 停止之前的刷新
        refreshScheduler.stop();

        // 获取实时行情数据
        fetchRealtimeData(code);

        // 获取 K 线数据
        fetchKlineData(code);
        
        // 获取分时数据
        fetchIntradayData(code);
        
        // 同步到服务器节点配置（更新关注列表）
        // 从服务器配置同步触发的查询不需要回写，避免用旧缓存覆盖服务器配置
        if (syncToServer) {
            syncWatchlistToServer(code);
        }
    }
    
    /**
     * 同步关注列表到服务器
     */
    private void syncWatchlistToServer(String code) {
        if (!configSyncCompleted) return;
        
        final String nodeId = nodeIdentityManager.getNodeId();
        
        // 构建关注列表更新（将当前股票添加到关注列表）
        java.util.List<String> watchlist = new java.util.ArrayList<>();
        watchlist.add(code);
        
        // 也可以保留之前的关注股票
        java.util.List<String> existingWatchlist = nodeConfigManager.getWatchlistStocks();
        for (String existing : existingWatchlist) {
            if (!existing.equals(code) && !watchlist.contains(existing)) {
                watchlist.add(existing);
            }
        }
        
        JSONObject update = nodeConfigManager.buildWatchlistUpdate(watchlist);
        
        stockService.updateNodeConfig(nodeId, update, new StockService.DataCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                // 更新本地缓存
                nodeConfigManager.saveServerConfig(data);
                nodeConfigManager.setPendingSync(false);
            }

            @Override
            public void onFailure(String error) {
                // 标记有待同步的配置，下次连接时重试
                nodeConfigManager.setPendingSync(true);
            }
        });
    }
    
    /**
     * 自动查询上次保存的股票（本地 fallback）
     * 注意：不回写服务器配置，避免用本地旧股票污染服务器 watchlist。
     * 服务器配置同步成功后会自动切换到正确的股票。
     */
    private void autoQueryLastStock() {
        String lastCode = configManager.getLastCode();
        if (!TextUtils.isEmpty(lastCode) && FormatUtil.isValidStockCode(lastCode)) {
            etStockCode.setText(lastCode);
            currentCode = lastCode;

            // 查询股票（不回写服务器：这只是本地 fallback，等服务器配置同步后自动切换）
            queryStock(false);

            Toast.makeText(this, "已自动查询上次股票: " + lastCode, Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchRealtimeData(final String code) {
        debugLogger.log("Query", "fetchRealtimeData: 请求 " + code);
        stockService.fetchRealtime(code, new StockService.DataCallback<StockData>() {
            @Override
            public void onSuccess(StockData data) {
                // 竞态保护：如果用户已切换到其他股票，丢弃过期的回调
                if (!code.equals(currentCode)) {
                    debugLogger.warn("Query", "fetchRealtimeData: 丢弃过期回调 " + code);
                    return;
                }
                debugLogger.log("Query", "fetchRealtimeData: 成功 " + code + " price=" + data.getPrice());
                updateStockInfo(data);
                stockInfoPanel.setVisibility(View.VISIBLE);

                // 启动定时刷新
                refreshScheduler.start(code, MainActivity.this);
            }

            @Override
            public void onFailure(String error) {
                // 竞态保护：丢弃过期的失败回调
                if (!code.equals(currentCode)) {
                    return;
                }
                debugLogger.error("Query", "fetchRealtimeData: 失败 " + code + " error=" + error);
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchKlineData(final String code) {
        stockService.fetchKline(code, 30, new StockService.DataCallback<List<KLineData>>() {
            @Override
            public void onSuccess(List<KLineData> data) {
                if (!code.equals(currentCode)) {
                    return;
                }
                if (data != null && data.size() > 0) {
                    priceChart.setData(data);
                    chartPanel.setVisibility(View.VISIBLE);  // 显示日K线图区域
                }
            }

            @Override
            public void onFailure(String error) {
                // K线数据获取失败不影响分时图显示
            }
        });
    }

    private void fetchIntradayData(final String code) {
        stockService.fetchIntraday(code, new StockService.DataCallback<IntradayData>() {
            @Override
            public void onSuccess(IntradayData data) {
                if (!code.equals(currentCode)) {
                    return;
                }
                if (data != null && data.getData() != null && data.getData().size() > 0) {
                    intradayChart.setData(data);
                    tvIntradayDate.setText(data.getDate());
                    intradayPanel.setVisibility(View.VISIBLE);
                } else {
                    // 没有分时数据时隐藏分时图
                    intradayPanel.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(String error) {
                // 分时数据获取失败时不显示错误，只是隐藏分时图
                intradayPanel.setVisibility(View.GONE);
            }
        });
    }

    private void updateStockInfo(StockData data) {
        // 股票名称
        tvStockName.setText(data.getName() + " (" + data.getCode() + ")");

        // 价格
        tvPrice.setText(data.getFormattedPrice());

        // 涨跌幅和颜色（中国股市习惯：涨红跌绿）
        tvChangePct.setText(data.getFormattedChangePct());
        if (data.isUp()) {
            tvPrice.setTextColor(Color.parseColor("#dc3545"));  // 红色表示上涨
            tvChangePct.setTextColor(Color.parseColor("#dc3545"));
        } else if (data.isDown()) {
            tvPrice.setTextColor(Color.parseColor("#20c997"));  // 绿色表示下跌
            tvChangePct.setTextColor(Color.parseColor("#20c997"));
        } else {
            tvPrice.setTextColor(Color.parseColor("#6c757d"));
            tvChangePct.setTextColor(Color.parseColor("#6c757d"));
        }

        // 涨跌额
        tvChangeAmt.setText(data.getFormattedChangeAmt());

        // 详细数据
        tvOpen.setText(FormatUtil.formatPrice(data.getOpen()));
        tvHigh.setText(FormatUtil.formatPrice(data.getHigh()));
        tvLow.setText(FormatUtil.formatPrice(data.getLow()));
        tvLastClose.setText(FormatUtil.formatPrice(data.getLastClose()));
        tvVolume.setText(data.getFormattedVolume());
        tvAmount.setText(data.getFormattedAmount());

        // 更新时间
        tvRefreshTime.setText(getString(R.string.status_last_update, FormatUtil.formatCurrentTime()));
    }

    private void openSettings() {
        // 打开设置界面
        SettingsActivity.start(this);
    }

    // RefreshScheduler.RefreshCallback 实现

    @Override
    public void onRefreshStart() {
        tvRefreshing.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRefreshSuccess(StockData data) {
        tvRefreshing.setVisibility(View.GONE);
        updateStockInfo(data);
        
        // 同时刷新分时图数据
        if (!TextUtils.isEmpty(currentCode)) {
            fetchIntradayData(currentCode);
        }
    }

    @Override
    public void onRefreshFailure(String error) {
        tvRefreshing.setVisibility(View.GONE);
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }

    // AutoConnectManager.AutoConnectCallback 实现

    @Override
    public void onConnected(String serverIp, int serverPort) {
        debugLogger.log("Connect", "连接成功: " + serverIp + ":" + serverPort);
        tvStatus.setText(R.string.status_connected);
        tvStatus.setTextColor(Color.parseColor("#20c997"));
        autoConnectCompleted = true;
        
        // 保存服务器配置
        configManager.setServerIp(serverIp);
        configManager.setServerPort(serverPort);
        saveConfigToExternalStorage();
        
        Toast.makeText(this, "已连接到服务器: " + serverIp, Toast.LENGTH_SHORT).show();
        
        // 自动同步节点配置
        syncNodeConfig();
    }

    @Override
    public void onNeedSelectServer(List<MDNSDiscovery.DiscoveredServer> servers) {
        // 显示服务器选择对话框
        showServerSelectionDialog(servers);
    }

    @Override
    public void onConnectionFailed(String error) {
        debugLogger.error("Connect", "连接失败: " + error);
        tvStatus.setText(R.string.status_disconnected);
        tvStatus.setTextColor(Color.parseColor("#dc3545"));
        autoConnectCompleted = true;
        
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        
        // 连接失败时，使用本地缓存的配置并查询上次股票
        autoQueryLastStock();
    }

    // ==================== 节点配置同步 ====================

    /**
     * 同步节点配置（连接成功后自动调用）
     * 流程：获取服务器配置 → 获取节点配置 → 应用到界面
     */
    private void syncNodeConfig() {
        debugLogger.log("ConfigSync", "syncNodeConfig 开始，configSyncCompleted=" + configSyncCompleted);
        if (configSyncCompleted) return;

        final String nodeId = nodeIdentityManager.getNodeId();
        debugLogger.log("ConfigSync", "NodeID: " + nodeId);
        debugLogger.log("ConfigSync", "是否新节点: " + nodeIdentityManager.isNewNode());

        // 步骤 1：获取服务器全局配置
        debugLogger.log("ConfigSync", "步骤1: GET /api/config");
        stockService.fetchServerConfig(new StockService.DataCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject serverConfig) {
                debugLogger.log("ConfigSync", "步骤1成功: /api/config 返回");
                // 步骤 2：获取节点配置
                fetchNodeConfigAndApply(nodeId);
            }

            @Override
            public void onFailure(String error) {
                debugLogger.warn("ConfigSync", "步骤1失败: /api/config 错误: " + error);
                // 服务器配置获取失败，继续获取节点配置
                fetchNodeConfigAndApply(nodeId);
            }
        });
    }

    /**
     * 获取节点配置并应用到界面
     */
    private void fetchNodeConfigAndApply(final String nodeId) {
        debugLogger.log("ConfigSync", "步骤2: GET /api/node/config (X-Node-ID: " + nodeId + ")");
        stockService.fetchNodeConfig(nodeId, new StockService.DataCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject nodeConfig) {
                debugLogger.log("ConfigSync", "步骤2成功: /api/node/config 返回: " + nodeConfig.toString());
                // 保存到本地缓存
                nodeConfigManager.saveServerConfig(nodeConfig);
                configSyncCompleted = true;

                // 应用到界面
                applyNodeConfig(nodeConfig);
            }

            @Override
            public void onFailure(String error) {
                debugLogger.error("ConfigSync", "步骤2失败: /api/node/config 错误: " + error);
                // 节点配置获取失败，记录详细错误信息便于诊断
                android.util.Log.e("MainActivity", "fetchNodeConfig 失败: " + error);
                Toast.makeText(MainActivity.this, "节点配置获取失败: " + error, Toast.LENGTH_LONG).show();

                // 节点配置获取失败，使用本地缓存或默认配置
                JSONObject cachedConfig = nodeConfigManager.getServerConfig();
                if (cachedConfig != null) {
                    applyNodeConfig(cachedConfig);
                } else {
                    // 没有缓存，查询上次股票（不回写服务器，避免污染配置）
                    autoQueryLastStock();
                }
                configSyncCompleted = true;

                // 节点配置获取失败，15秒后快速重试（不等5分钟定时同步）
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (autoConnectCompleted) {
                            syncNodeConfigFromServer();
                        }
                    }
                }, 15000);
            }
        });
    }

    /**
     * 应用节点配置到界面
     * 服务器配置优先，本地配置作为 fallback
     */
    private void applyNodeConfig(JSONObject config) {
        debugLogger.log("ConfigSync", "applyNodeConfig: 开始解析配置");
        debugLogger.log("ConfigSync", "applyNodeConfig: 原始 config = " + config.toString());
        // 直接从传入的 config 解析关注列表（避免 SharedPreferences 异步保存延迟）
        java.util.List<String> watchlist = new java.util.ArrayList<>();
        try {
            JSONObject watchlistObj = config.optJSONObject("watchlist");
            if (watchlistObj != null) {
                org.json.JSONArray stocks = watchlistObj.optJSONArray("stocks");
                if (stocks != null) {
                    for (int i = 0; i < stocks.length(); i++) {
                        watchlist.add(stocks.getString(i));
                    }
                }
            }
        } catch (org.json.JSONException e) {
            debugLogger.error("ConfigSync", "applyNodeConfig: watchlist 解析异常: " + e.getMessage());
        }

        debugLogger.log("ConfigSync", "applyNodeConfig: 解析到 watchlist stocks = " + watchlist);

        // 应用刷新间隔配置（从节点配置读取，默认 5 秒）
        try {
            JSONObject refreshObj = config.optJSONObject("refresh");
            if (refreshObj != null) {
                int intervalSec = refreshObj.optInt("realtime_interval_sec", 5);
                // 转换为毫秒并应用（最小 1 秒，最大 60 秒）
                long intervalMs = Math.max(1000, Math.min(intervalSec * 1000L, 60000));
                refreshScheduler.setInterval(intervalMs);
            } else {
                refreshScheduler.setInterval(5000); // 默认 5 秒
            }
        } catch (Exception e) {
            refreshScheduler.setInterval(5000); // 默认 5 秒
        }
        
        String localLastCode = configManager.getLastCode();
        
        if (!watchlist.isEmpty()) {
            // 服务器关注列表优先
            String serverStock = watchlist.get(0);
            debugLogger.log("ConfigSync", "applyNodeConfig: 服务器股票=" + serverStock + ", 本地 lastCode=" + localLastCode);
            
            // 检查是否与本地保存的股票不同
            if (!TextUtils.isEmpty(localLastCode) && !serverStock.equals(localLastCode)) {
                // 服务器配置与本地不同，使用服务器配置
                Toast.makeText(this, "服务器关注股票(" + serverStock + ") 与本地(" + localLastCode + ") 不同，使用服务器配置", Toast.LENGTH_SHORT).show();
            }
            
            currentCode = serverStock;
            etStockCode.setText(currentCode);
            debugLogger.log("ConfigSync", "applyNodeConfig: 使用服务器股票 " + serverStock + "，调用 queryStock(false)");
            // 不回写服务器：刚从服务器拿到配置，不需要反向同步
            queryStock(false);
        } else if (!TextUtils.isEmpty(localLastCode)) {
            // 服务器关注列表为空，使用本地保存的股票
            debugLogger.warn("ConfigSync", "applyNodeConfig: 服务器 watchlist 为空，使用本地股票: " + localLastCode);
            currentCode = localLastCode;
            etStockCode.setText(currentCode);
            queryStock(false);
            Toast.makeText(this, "服务器关注列表为空，使用本地保存的股票: " + localLastCode, Toast.LENGTH_SHORT).show();
        } else {
            // 没有任何股票，等待用户输入
            Toast.makeText(this, "节点配置已同步，请输入股票代码", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onProgressUpdate(String message) {
        tvStatus.setText(message);
    }

    /**
     * 显示服务器选择对话框
     */
    private void showServerSelectionDialog(List<MDNSDiscovery.DiscoveredServer> servers) {
        String[] serverNames = new String[servers.size()];
        for (int i = 0; i < servers.size(); i++) {
            MDNSDiscovery.DiscoveredServer server = servers.get(i);
            serverNames[i] = server.getHost() + ":" + server.getPort();
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择服务器");
        builder.setItems(serverNames, (dialog, which) -> {
            MDNSDiscovery.DiscoveredServer selectedServer = servers.get(which);
            autoConnectManager.connectToServer(selectedServer);
        });
        builder.setCancelable(false);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 只有在自动连接已完成的情况下才重新检查服务器状态
        if (autoConnectCompleted) {
            checkServerStatus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 停止防烧屏保护
        stopBurnInProtection();
        
        // 停止节点配置定时同步
        stopNodeConfigSync();

        // 停止交易时间检查
        if (tradingHoursCheckHandler != null) {
            tradingHoursCheckHandler.removeCallbacks(tradingHoursCheckTask);
        }

        // 保存配置到外部存储
        saveConfigToExternalStorage();
        
        // 停止刷新并释放资源
        refreshScheduler.stop();
        stockService.shutdown();
        autoConnectManager.shutdown();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 处理 SAF 目录选择结果
        if (externalStorageManager.handleSafResult(requestCode, resultCode, data)) {
            Toast.makeText(this, "已选择外部存储目录，配置将保存到该目录", Toast.LENGTH_SHORT).show();
            // 立即保存当前配置
            saveConfigToExternalStorage();
        }
    }
    
    // ==================== 防烧屏保护 ====================
    
    /**
     * 启动防烧屏保护
     * 定期对关键UI元素进行轻微位置偏移，防止OLED屏幕烧屏
     */
    private void startBurnInProtection() {
        burnInProtectionHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        burnInProtectionTask = new Runnable() {
            @Override
            public void run() {
                applyBurnInOffset();
                burnInProtectionHandler.postDelayed(this, BURN_IN_INTERVAL);
            }
        };
        
        // 启动定时任务
        burnInProtectionHandler.post(burnInProtectionTask);
    }
    
    /**
     * 停止防烧屏保护
     */
    private void stopBurnInProtection() {
        if (burnInProtectionHandler != null && burnInProtectionTask != null) {
            burnInProtectionHandler.removeCallbacks(burnInProtectionTask);
        }
    }
    
    /**
     * 应用防烧屏偏移
     * 对价格、涨跌幅等长时间显示的文本进行轻微位置偏移
     */
    private void applyBurnInOffset() {
        // 计算偏移值：交替使用正负偏移
        burnInOffset = (burnInOffset + 1) % (BURN_IN_MAX_OFFSET * 2 + 1);
        int offset = burnInOffset - BURN_IN_MAX_OFFSET; // -3 到 +3
        
        // 对关键显示元素应用偏移
        if (tvPrice != null) {
            tvPrice.setTranslationY(offset);
        }
        if (tvChangePct != null) {
            tvChangePct.setTranslationY(-offset); // 反向偏移
        }
        if (tvChangeAmt != null) {
            tvChangeAmt.setTranslationY(offset);
        }
        if (tvStockName != null) {
            tvStockName.setTranslationY(-offset);
        }
        if (tvRefreshTime != null) {
            tvRefreshTime.setTranslationX(offset);
        }
        if (tvStatus != null) {
            tvStatus.setTranslationX(-offset);
        }
        
        // 对图表区域应用轻微偏移
        if (stockInfoPanel != null && stockInfoPanel.getVisibility() == View.VISIBLE) {
            stockInfoPanel.setTranslationY(offset * 0.5f);
        }
        if (chartPanel != null && chartPanel.getVisibility() == View.VISIBLE) {
            chartPanel.setTranslationY(-offset * 0.5f);
        }
        if (intradayPanel != null && intradayPanel.getVisibility() == View.VISIBLE) {
            intradayPanel.setTranslationY(offset * 0.5f);
        }
    }
    
    // ==================== 节点配置定时同步 ====================
    
    /**
     * 启动节点配置定时同步
     * 每10分钟从服务器同步节点配置
     */
    private void startNodeConfigSync() {
        nodeConfigSyncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        nodeConfigSyncTask = new Runnable() {
            @Override
            public void run() {
                if (configSyncCompleted && autoConnectCompleted) {
                    syncNodeConfigFromServer();
                }
                nodeConfigSyncHandler.postDelayed(this, NODE_CONFIG_SYNC_INTERVAL);
            }
        };
        
        // 首次同步延迟5分钟（避免与初始同步冲突）
        nodeConfigSyncHandler.postDelayed(nodeConfigSyncTask, 300000);
    }
    
    /**
     * 停止节点配置定时同步
     */
    private void stopNodeConfigSync() {
        if (nodeConfigSyncHandler != null && nodeConfigSyncTask != null) {
            nodeConfigSyncHandler.removeCallbacks(nodeConfigSyncTask);
        }
    }
    
    /**
     * 从服务器同步节点配置（定时调用）
     * 获取配置并自动应用到界面
     */
    private void syncNodeConfigFromServer() {
        debugLogger.log("ConfigSync", "syncNodeConfigFromServer: 定时同步开始");
        if (!configSyncCompleted) return;
        
        final String nodeId = nodeIdentityManager.getNodeId();
        debugLogger.log("ConfigSync", "syncNodeConfigFromServer: NodeID=" + nodeId + ", currentCode=" + currentCode);
        
        stockService.fetchNodeConfig(nodeId, new StockService.DataCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject nodeConfig) {
                debugLogger.log("ConfigSync", "syncNodeConfigFromServer: 成功, config=" + nodeConfig.toString());
                // 保存到本地缓存
                nodeConfigManager.saveServerConfig(nodeConfig);
                
                // 直接从返回的 config 解析关注列表（避免 SharedPreferences 异步保存延迟）
                java.util.List<String> serverWatchlist = new java.util.ArrayList<>();
                try {
                    JSONObject watchlistObj = nodeConfig.optJSONObject("watchlist");
                    if (watchlistObj != null) {
                        org.json.JSONArray stocks = watchlistObj.optJSONArray("stocks");
                        if (stocks != null) {
                            for (int i = 0; i < stocks.length(); i++) {
                                serverWatchlist.add(stocks.getString(i));
                            }
                        }
                    }
                } catch (org.json.JSONException e) {
                    // 解析失败
                }
                
                String currentStock = currentCode;
                
                // 如果关注列表不为空，且第一个股票与当前不同，自动切换
                if (!serverWatchlist.isEmpty()) {
                    String newStock = serverWatchlist.get(0);
                    if (!newStock.equals(currentStock)) {
                        // 自动切换到新的关注股票
                        debugLogger.log("ConfigSync", "syncNodeConfigFromServer: 服务器股票变更 " + currentStock + " -> " + newStock);
                        etStockCode.setText(newStock);
                        currentCode = newStock;
                        // 不回写服务器：刚从服务器拿到配置，不需要反向同步
                        queryStock(false);
                        Toast.makeText(MainActivity.this, 
                            "已自动切换到关注股票: " + newStock, 
                            Toast.LENGTH_SHORT).show();
                    }
                }
                
                // 如果没进入上面的 if，说明股票没变
                if (!serverWatchlist.isEmpty() && serverWatchlist.get(0).equals(currentStock)) {
                    debugLogger.log("ConfigSync", "syncNodeConfigFromServer: 服务器股票与当前一致(" + currentStock + ")，不切换");
                }

                // 应用刷新间隔配置（从节点配置读取）
                try {
                    JSONObject refreshObj = nodeConfig.optJSONObject("refresh");
                    if (refreshObj != null) {
                        int intervalSec = refreshObj.optInt("realtime_interval_sec", 5);
                        long intervalMs = Math.max(1000, Math.min(intervalSec * 1000L, 60000));
                        refreshScheduler.setInterval(intervalMs);
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }

            @Override
            public void onFailure(String error) {
                debugLogger.warn("ConfigSync", "syncNodeConfigFromServer: 失败: " + error);
                android.util.Log.w("MainActivity", "syncNodeConfigFromServer 失败: " + error);
            }
        });
    }
}





