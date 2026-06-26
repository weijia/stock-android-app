package com.stock.app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    // 节点配置管理
    private NodeIdentityManager nodeIdentityManager;
    private NodeConfigManager nodeConfigManager;

    // 当前股票代码
    private String currentCode = "";
    
    // 是否已完成自动连接
    private boolean autoConnectCompleted = false;

    // 是否已完成配置同步
    private boolean configSyncCompleted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化外部存储管理器
        externalStorageManager = new ExternalStorageManager(this);
        
        // 从外部存储加载配置
        loadConfigFromExternalStorage();

        // 初始化节点身份和配置管理
        nodeIdentityManager = new NodeIdentityManager(this);
        nodeConfigManager = new NodeConfigManager(this);

        // 初始化配置和服务
        configManager = new ConfigManager(this);
        stockService = new StockService(configManager);
        refreshScheduler = new RefreshScheduler(stockService);
        autoConnectManager = new AutoConnectManager(this, configManager, stockService);
        autoConnectManager.setCallback(this);

        // 初始化 UI
        initViews();

        // 设置按钮点击事件
        setupClickListeners();

        // 恢复上次查询的股票代码
        String lastCode = configManager.getLastCode();
        if (!TextUtils.isEmpty(lastCode)) {
            etStockCode.setText(lastCode);
        }
        
        // 开始自动连接
        startAutoConnect();
    }
    
    /**
     * 开始自动连接流程
     */
    private void startAutoConnect() {
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
        String code = etStockCode.getText().toString().trim();

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
        syncWatchlistToServer(code);
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
     * 自动查询上次保存的股票
     */
    private void autoQueryLastStock() {
        String lastCode = configManager.getLastCode();
        if (!TextUtils.isEmpty(lastCode) && FormatUtil.isValidStockCode(lastCode)) {
            etStockCode.setText(lastCode);
            currentCode = lastCode;
            
            // 查询股票
            queryStock();
            
            Toast.makeText(this, "已自动查询上次股票: " + lastCode, Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchRealtimeData(String code) {
        stockService.fetchRealtime(code, new StockService.DataCallback<StockData>() {
            @Override
            public void onSuccess(StockData data) {
                updateStockInfo(data);
                stockInfoPanel.setVisibility(View.VISIBLE);

                // 启动定时刷新
                refreshScheduler.start(code, MainActivity.this);
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchKlineData(String code) {
        stockService.fetchKline(code, 30, new StockService.DataCallback<List<KLineData>>() {
            @Override
            public void onSuccess(List<KLineData> data) {
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

    private void fetchIntradayData(String code) {
        stockService.fetchIntraday(code, new StockService.DataCallback<IntradayData>() {
            @Override
            public void onSuccess(IntradayData data) {
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
        if (configSyncCompleted) return;

        final String nodeId = nodeIdentityManager.getNodeId();

        // 步骤 1：获取服务器全局配置
        stockService.fetchServerConfig(new StockService.DataCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject serverConfig) {
                // 步骤 2：获取节点配置
                fetchNodeConfigAndApply(nodeId);
            }

            @Override
            public void onFailure(String error) {
                // 服务器配置获取失败，继续获取节点配置
                fetchNodeConfigAndApply(nodeId);
            }
        });
    }

    /**
     * 获取节点配置并应用到界面
     */
    private void fetchNodeConfigAndApply(final String nodeId) {
        stockService.fetchNodeConfig(nodeId, new StockService.DataCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject nodeConfig) {
                // 保存到本地缓存
                nodeConfigManager.saveServerConfig(nodeConfig);
                configSyncCompleted = true;

                // 应用到界面
                applyNodeConfig(nodeConfig);
            }

            @Override
            public void onFailure(String error) {
                // 节点配置获取失败，使用本地缓存或默认配置
                JSONObject cachedConfig = nodeConfigManager.getServerConfig();
                if (cachedConfig != null) {
                    applyNodeConfig(cachedConfig);
                } else {
                    // 没有缓存，查询上次股票
                    autoQueryLastStock();
                }
                configSyncCompleted = true;
            }
        });
    }

    /**
     * 应用节点配置到界面
     */
    private void applyNodeConfig(JSONObject config) {
        // 加载关注列表
        java.util.List<String> watchlist = nodeConfigManager.getWatchlistStocks();
        if (!watchlist.isEmpty()) {
            // 设置第一个关注股票为当前查询股票
            currentCode = watchlist.get(0);
            etStockCode.setText(currentCode);
            // 自动查询
            queryStock();
        } else {
            // 没有关注股票，尝试恢复上次查询
            autoQueryLastStock();
        }

        Toast.makeText(this, "节点配置已同步", Toast.LENGTH_SHORT).show();
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
}