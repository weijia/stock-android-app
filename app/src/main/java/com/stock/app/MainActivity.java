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

import com.stock.app.model.IntradayData;
import com.stock.app.model.KLineData;
import com.stock.app.model.StockData;
import com.stock.app.service.RefreshScheduler;
import com.stock.app.service.StockService;
import com.stock.app.util.ConfigManager;
import com.stock.app.util.FormatUtil;
import com.stock.app.view.IntradayChartView;
import com.stock.app.view.PriceChartView;
import com.stock.app.view.VolumeChartView;

import java.util.List;

/**
 * 主界面 Activity
 */
public class MainActivity extends Activity implements RefreshScheduler.RefreshCallback {

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
    private VolumeChartView volumeChart;
    private IntradayChartView intradayChart;

    // 服务组件
    private ConfigManager configManager;
    private StockService stockService;
    private RefreshScheduler refreshScheduler;

    // 当前股票代码
    private String currentCode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化配置和服务
        configManager = new ConfigManager(this);
        stockService = new StockService(configManager);
        refreshScheduler = new RefreshScheduler(stockService);

        // 初始化 UI
        initViews();

        // 设置按钮点击事件
        setupClickListeners();

        // 检查服务器连接状态
        checkServerStatus();

        // 恢复上次查询的股票代码
        String lastCode = configManager.getLastCode();
        if (!TextUtils.isEmpty(lastCode)) {
            etStockCode.setText(lastCode);
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
        volumeChart = findViewById(R.id.volume_chart);
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

        // 获取分时数据
        fetchIntradayData(code);

        // 获取 K 线数据
        fetchKlineData(code);
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

    private void fetchIntradayData(String code) {
        stockService.fetchIntraday(code, new StockService.DataCallback<IntradayData>() {
            @Override
            public void onSuccess(IntradayData data) {
                intradayChart.setData(data);
                tvIntradayDate.setText(data.getDate());
                intradayPanel.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(String error) {
                // 分时数据获取失败时不显示错误，只是隐藏分时图
                intradayPanel.setVisibility(View.GONE);
            }
        });
    }

    private void fetchKlineData(String code) {
        stockService.fetchKline(code, 30, new StockService.DataCallback<List<KLineData>>() {
            @Override
            public void onSuccess(List<KLineData> data) {
                priceChart.setData(data);
                volumeChart.setData(data);
                chartPanel.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStockInfo(StockData data) {
        // 股票名称
        tvStockName.setText(data.getName() + " (" + data.getCode() + ")");

        // 价格
        tvPrice.setText(data.getFormattedPrice());

        // 涨跌幅和颜色
        tvChangePct.setText(data.getFormattedChangePct());
        if (data.isUp()) {
            tvPrice.setTextColor(Color.parseColor("#20c997"));
            tvChangePct.setTextColor(Color.parseColor("#20c997"));
        } else if (data.isDown()) {
            tvPrice.setTextColor(Color.parseColor("#dc3545"));
            tvChangePct.setTextColor(Color.parseColor("#dc3545"));
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
    }

    @Override
    public void onRefreshFailure(String error) {
        tvRefreshing.setVisibility(View.GONE);
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重新检查服务器状态
        checkServerStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止刷新并释放资源
        refreshScheduler.stop();
        stockService.shutdown();
    }
}