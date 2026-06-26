package com.stock.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.stock.app.service.MDNSDiscovery;
import com.stock.app.service.ServerDiscovery;
import com.stock.app.service.StockService;
import com.stock.app.util.ConfigManager;
import com.stock.app.util.ExternalStorageManager;

import org.json.JSONObject;

import java.util.List;

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
    private Button btnAutoDiscover;
    private Button btnAutoDiscoverMDNS;
    private TextView tvDiscoveryStatus;
    private LinearLayout discoveredServersPanel;
    private TextView tvDebugLog;
    private ScrollView svDebugLog;
    private Button btnSelectStorage;
    private TextView tvStorageLocation;

    // 服务组件
    private ConfigManager configManager;
    private ExternalStorageManager externalStorageManager;
    private StockService stockService;
    private ServerDiscovery serverDiscovery;  // UDP 广播发现（备选）
    private MDNSDiscovery mdnsDiscovery;      // mDNS 发现（推荐）
    
    // 调试日志缓冲
    private StringBuilder debugLogBuffer = new StringBuilder();

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
        serverDiscovery = new ServerDiscovery(this);
        mdnsDiscovery = new MDNSDiscovery(this);
        
        // 设置调试日志回调（UDP 广播）
        serverDiscovery.setDebugLogCallback(new ServerDiscovery.DebugLogCallback() {
            @Override
            public void onDebugLog(String log) {
                appendDebugLog(log);
            }
        });
        
        // 设置调试日志回调（mDNS）
        mdnsDiscovery.setDebugLogCallback(new MDNSDiscovery.DebugLogCallback() {
            @Override
            public void onDebugLog(String log) {
                appendDebugLog(log);
            }
        });

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
        btnAutoDiscover = findViewById(R.id.btn_auto_discover);
        btnAutoDiscoverMDNS = findViewById(R.id.btn_auto_discover_mdns);
        tvDiscoveryStatus = findViewById(R.id.tv_discovery_status);
        discoveredServersPanel = findViewById(R.id.discovered_servers_panel);
        tvDebugLog = findViewById(R.id.tv_debug_log);
        svDebugLog = findViewById(R.id.sv_debug_log);
        
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

        // mDNS 发现按钮（推荐）
        btnAutoDiscoverMDNS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMDNSDiscovery();
            }
        });
        
        // UDP 广播发现按钮（备选）
        btnAutoDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startUDPDiscovery();
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

        // 查看调试日志按钮
        Button btnViewDebugLog = findViewById(R.id.btn_view_debug_log);
        if (btnViewDebugLog != null) {
            btnViewDebugLog.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DebugLogActivity.start(SettingsActivity.this);
                }
            });
        }
    }

    /**
     * 添加调试日志
     */
    private void appendDebugLog(String log) {
        if (tvDebugLog == null) return;
        
        debugLogBuffer.append(log).append("\n");
        tvDebugLog.setText(debugLogBuffer.toString());
        
        // 自动滚动到底部
        if (svDebugLog != null) {
            svDebugLog.post(new Runnable() {
                @Override
                public void run() {
                    svDebugLog.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }
    
    /**
     * 清空调试日志
     */
    private void clearDebugLog() {
        debugLogBuffer.setLength(0);
        if (tvDebugLog != null) {
            tvDebugLog.setText("");
        }
    }

    /**
     * 开始 mDNS 发现（推荐方案）
     */
    private void startMDNSDiscovery() {
        // 清空调试日志
        clearDebugLog();
        
        tvDiscoveryStatus.setVisibility(View.VISIBLE);
        tvDiscoveryStatus.setText("正在通过 mDNS 搜索服务器...");
        tvDiscoveryStatus.setTextColor(Color.parseColor("#6c757d"));
        
        // 清空已发现服务器列表
        discoveredServersPanel.removeAllViews();
        
        // 禁用按钮
        btnAutoDiscoverMDNS.setEnabled(false);
        btnAutoDiscover.setEnabled(false);
        
        // 显示调试日志区域
        if (svDebugLog != null) {
            svDebugLog.setVisibility(View.VISIBLE);
        }
        
        appendDebugLog("=== mDNS 服务发现（推荐方案） ===");
        appendDebugLog("时间: " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
        appendDebugLog("说明: mDNS 不会被 Android 硬件过滤器阻止");
        
        // 开始发现
        mdnsDiscovery.startDiscovery(30000, new MDNSDiscovery.DiscoveryCallback() {
            @Override
            public void onServerFound(MDNSDiscovery.DiscoveredServer server) {
                // 添加服务器到列表
                addDiscoveredServerMDNS(server);
                
                tvDiscoveryStatus.setText("发现服务器: " + server.toString());
                tvDiscoveryStatus.setTextColor(Color.parseColor("#20c997"));
            }

            @Override
            public void onDiscoveryComplete(List<MDNSDiscovery.DiscoveredServer> servers) {
                btnAutoDiscoverMDNS.setEnabled(true);
                btnAutoDiscover.setEnabled(true);
                
                if (servers.isEmpty()) {
                    tvDiscoveryStatus.setText("mDNS 搜索完成，未发现服务器");
                    tvDiscoveryStatus.setTextColor(Color.parseColor("#dc3545"));
                } else {
                    tvDiscoveryStatus.setText("mDNS 搜索完成，发现 " + servers.size() + " 个服务器");
                    tvDiscoveryStatus.setTextColor(Color.parseColor("#20c997"));
                }
            }

            @Override
            public void onError(String error) {
                btnAutoDiscoverMDNS.setEnabled(true);
                btnAutoDiscover.setEnabled(true);
                tvDiscoveryStatus.setText("mDNS 搜索失败: " + error);
                tvDiscoveryStatus.setTextColor(Color.parseColor("#dc3545"));
            }
        });
    }
    
    /**
     * 开始 UDP 广播发现（备选方案）
     */
    private void startUDPDiscovery() {
        // 清空调试日志
        clearDebugLog();
        
        tvDiscoveryStatus.setVisibility(View.VISIBLE);
        tvDiscoveryStatus.setText(R.string.status_discovering);
        tvDiscoveryStatus.setTextColor(Color.parseColor("#6c757d"));
        
        // 清空已发现服务器列表
        discoveredServersPanel.removeAllViews();
        
        // 禁用按钮
        btnAutoDiscover.setEnabled(false);
        btnAutoDiscoverMDNS.setEnabled(false);
        
        // 显示调试日志区域
        if (svDebugLog != null) {
            svDebugLog.setVisibility(View.VISIBLE);
        }
        
        appendDebugLog("=== UDP 广播发现（备选方案） ===");
        appendDebugLog("时间: " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
        appendDebugLog("警告: Android 硬件过滤器可能阻止 UDP 广播");
        
        // 开始发现
        serverDiscovery.startDiscovery(30000, new ServerDiscovery.DiscoveryCallback() {
            @Override
            public void onServerFound(ServerDiscovery.DiscoveredServer server) {
                // 添加服务器到列表
                addDiscoveredServerUDP(server);
                
                tvDiscoveryStatus.setText(getString(R.string.status_discovery_found, server.toString()));
                tvDiscoveryStatus.setTextColor(Color.parseColor("#dc3545")); // 红色表示发现
            }

            @Override
            public void onDiscoveryComplete(List<ServerDiscovery.DiscoveredServer> servers) {
                btnAutoDiscover.setEnabled(true);
                btnAutoDiscoverMDNS.setEnabled(true);
                
                if (servers.isEmpty()) {
                    tvDiscoveryStatus.setText(R.string.status_discovery_timeout);
                    tvDiscoveryStatus.setTextColor(Color.parseColor("#dc3545"));
                    appendDebugLog("提示: 如果服务器正在广播但客户端未发现，请尝试 mDNS 发现");
                } else {
                    tvDiscoveryStatus.setText(getString(R.string.status_discovery_complete, servers.size()));
                    tvDiscoveryStatus.setTextColor(Color.parseColor("#20c997"));
                }
            }

            @Override
            public void onError(String error) {
                btnAutoDiscover.setEnabled(true);
                btnAutoDiscoverMDNS.setEnabled(true);
                tvDiscoveryStatus.setText(getString(R.string.status_discovery_failed, error));
                tvDiscoveryStatus.setTextColor(Color.parseColor("#dc3545"));
            }
        });
    }

    /**
     * 添加 mDNS 发现的服务器到 UI
     */
    private void addDiscoveredServerMDNS(MDNSDiscovery.DiscoveredServer server) {
        Button serverBtn = new Button(this);
        serverBtn.setText(server.toString());
        serverBtn.setBackgroundColor(Color.parseColor("#20c997")); // 绿色表示 mDNS
        serverBtn.setTextColor(Color.parseColor("#ffffff"));
        
        serverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 选择这个服务器
                etServerIp.setText(server.getHost());
                etServerPort.setText(String.valueOf(server.getPort()));
                
                Toast.makeText(SettingsActivity.this, 
                    "已选择服务器: " + server.toString(), Toast.LENGTH_SHORT).show();
            }
        });
        
        discoveredServersPanel.addView(serverBtn);
    }
    
    /**
     * 添加 UDP 发现的服务器到 UI
     */
    private void addDiscoveredServerUDP(ServerDiscovery.DiscoveredServer server) {
        Button serverBtn = new Button(this);
        serverBtn.setText(server.toString());
        serverBtn.setBackgroundColor(Color.parseColor("#e9ecef"));
        serverBtn.setTextColor(Color.parseColor("#212529"));
        
        serverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 选择这个服务器
                etServerIp.setText(server.getAddress());
                etServerPort.setText(String.valueOf(server.getHttpPort()));
                
                Toast.makeText(SettingsActivity.this, 
                    "已选择服务器: " + server.toString(), Toast.LENGTH_SHORT).show();
            }
        });
        
        discoveredServersPanel.addView(serverBtn);
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
        
        // 同时保存到外部存储，并检查结果
        boolean externalSaved = saveToExternalStorageWithResult(ip, port);
        
        if (externalSaved) {
            Toast.makeText(this, "配置已保存到外部存储", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "配置已保存到应用内部（外部存储不可用）", Toast.LENGTH_LONG).show();
        }

        // 返回主界面
        finish();
    }
    
    /**
     * 保存配置到外部存储，返回是否成功
     */
    private boolean saveToExternalStorageWithResult(String ip, int port) {
        try {
            JSONObject config = new JSONObject();
            config.put("server_ip", ip);
            config.put("server_port", port);
            config.put("last_code", configManager.getLastCode());
            
            ExternalStorageManager.SaveResult result = externalStorageManager.saveConfig(config);
            
            // 显示详细结果
            if (result.safSuccess) {
                Toast.makeText(this, "配置已保存到 SAF 目录", Toast.LENGTH_SHORT).show();
            } else if (result.externalSuccess) {
                Toast.makeText(this, "配置已保存到: " + result.externalLocation, Toast.LENGTH_LONG).show();
            } else {
                // SAF 和外部存储都失败
                String errorMsg = "外部存储保存失败";
                if (result.safError != null) {
                    errorMsg += " (SAF: " + result.safError + ")";
                }
                if (result.externalError != null) {
                    errorMsg += " (外部存储: " + result.externalError + ")";
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                android.util.Log.e("SettingsActivity", errorMsg);
            }
            
            return result.isExternalStorageSuccess();
        } catch (Exception e) {
            android.util.Log.e("SettingsActivity", "保存到外部存储失败: " + e.getMessage());
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
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
            updateStorageLocation();
            
            // 立即保存当前配置到新目录并验证
            String ip = etServerIp.getText().toString().trim();
            String portStr = etServerPort.getText().toString().trim();
            int port = 8080;
            if (!TextUtils.isEmpty(portStr)) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    port = 8080;
                }
            }
            
            boolean saved = saveToExternalStorageWithResult(ip, port);
            if (saved) {
                Toast.makeText(this, "已选择外部存储目录，配置已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已选择目录，但保存失败，请检查目录权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stockService.shutdown();
        serverDiscovery.shutdown();
        mdnsDiscovery.shutdown();
    }
}
