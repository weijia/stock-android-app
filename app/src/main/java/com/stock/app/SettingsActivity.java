package com.stock.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.stock.app.service.StockService;
import com.stock.app.util.ConfigManager;

/**
 * 设置界面 Activity
 */
public class SettingsActivity extends Activity {

    // UI 组件
    private EditText etServerIp;
    private EditText etServerPort;
    private Button btnSave;
    private Button btnTestConnection;
    private TextView tvTestResult;

    // 服务组件
    private ConfigManager configManager;
    private StockService stockService;

    public static void start(Activity activity) {
        Intent intent = new Intent(activity, SettingsActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 初始化配置和服务
        configManager = new ConfigManager(this);
        stockService = new StockService(configManager);

        // 初始化 UI
        initViews();

        // 加载当前配置
        loadConfig();

        // 设置按钮点击事件
        setupClickListeners();
    }

    private void initViews() {
        etServerIp = findViewById(R.id.et_server_ip);
        etServerPort = findViewById(R.id.et_server_port);
        btnSave = findViewById(R.id.btn_save);
        btnTestConnection = findViewById(R.id.btn_test_connection);
        tvTestResult = findViewById(R.id.tv_test_result);
    }

    private void loadConfig() {
        // 显示当前配置
        etServerIp.setText(configManager.getServerIp());
        etServerPort.setText(String.valueOf(configManager.getServerPort()));
    }

    private void setupClickListeners() {
        // 保存按钮
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
            }
        });

        // 测试连接按钮
        btnTestConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testConnection();
            }
        });
    }

    private void saveConfig() {
        String ip = etServerIp.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();

        // 验证输入
        if (TextUtils.isEmpty(ip)) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }

        int port = 8080;
        if (!TextUtils.isEmpty(portStr)) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "端口格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 保存配置
        configManager.saveServerConfig(ip, port);

        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();

        // 返回主界面
        finish();
    }

    private void testConnection() {
        String ip = etServerIp.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();

        // 验证输入
        if (TextUtils.isEmpty(ip)) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }

        int port = 8080;
        if (!TextUtils.isEmpty(portStr)) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "端口格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 显示测试结果
        tvTestResult.setVisibility(View.VISIBLE);
        tvTestResult.setText("正在测试连接...");
        tvTestResult.setTextColor(Color.parseColor("#6c757d"));

        // 测试连接
        stockService.testConnection(ip, port, new StockService.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean healthy) {
                if (healthy) {
                    tvTestResult.setText(R.string.connection_test_success);
                    tvTestResult.setTextColor(Color.parseColor("#20c997"));
                } else {
                    tvTestResult.setText(R.string.connection_test_failed);
                    tvTestResult.setTextColor(Color.parseColor("#dc3545"));
                }
            }

            @Override
            public void onFailure(String error) {
                tvTestResult.setText(R.string.connection_test_failed + ": " + error);
                tvTestResult.setTextColor(Color.parseColor("#dc3545"));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stockService.shutdown();
    }
}