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
import com.stock.app.util.ExternalStorageManager;

import org.json.JSONObject;

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
    private Button btnSelectStorage;
    private TextView tvStorageLocation;

    // 服务组件
    private ConfigManager configManager;
    private ExternalStorageManager externalStorageManager;
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
        externalStorageManager = new ExternalStorageManager(this);
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
        
        // 外部存储相关 UI
        btnSelectStorage = findViewById(R.id.btn_select_storage);
        tvStorageLocation = findViewById(R.id.tv_storage_location);
        
        // 显示当前存储位置
        updateStorageLocation();
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
        
        // 选择外部存储目录按钮
        if (btnSelectStorage != null) {
            btnSelectStorage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectExternalStorage();
                }
            });
        }
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

        // 保存配置到 SharedPreferences
        configManager.saveServerConfig(ip, port);
        
        // 同时保存到外部存储
        saveToExternalStorage(ip, port);

        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();

        // 返回主界面
        finish();
    }
    
    /**
     * 保存配置到外部存储
     */
    private void saveToExternalStorage(String ip, int port) {
        try {
            JSONObject config = new JSONObject();
            config.put("server_ip", ip);
            config.put("server_port", port);
            config.put("last_code", configManager.getLastCode());
            
            externalStorageManager.saveConfig(config);
        } catch (Exception e) {
            Toast.makeText(this, "保存到外部存储失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
    
    /**
     * 选择外部存储目录（SAF）
     */
    private void selectExternalStorage() {
        if (externalStorageManager.isSafAvailable()) {
            externalStorageManager.requestSafDirectory(this);
        } else {
            // Android 4.0-4.3 不支持 SAF，使用默认外部存储
            Toast.makeText(this, "您的系统版本不支持 SAF，配置将保存到外部存储: " + 
                externalStorageManager.getStorageLocation(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * 更新存储位置显示
     */
    private void updateStorageLocation() {
        if (tvStorageLocation != null) {
            tvStorageLocation.setText("配置保存位置: " + externalStorageManager.getStorageLocation());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 处理 SAF 目录选择结果
        if (externalStorageManager.handleSafResult(requestCode, resultCode, data)) {
            Toast.makeText(this, "已选择外部存储目录，配置卸载后保留", Toast.LENGTH_SHORT).show();
            updateStorageLocation();
            
            // 立即保存当前配置到新目录
            saveToExternalStorage(etServerIp.getText().toString().trim(), 
                Integer.parseInt(etServerPort.getText().toString().trim()));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stockService.shutdown();
    }
}