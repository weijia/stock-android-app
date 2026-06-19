package com.stock.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.stock.app.service.ItickService;
import com.stock.app.service.StockService;
import com.stock.app.util.ConfigManager;
import com.stock.app.util.ExternalStorageManager;

import org.json.JSONObject;

/**
 * 设置界面 Activity
 */
public class SettingsActivity extends Activity {

    // UI 组件
    private RadioGroup rgDataSource;
    private RadioButton rbLocal;
    private RadioButton rbItick;
    private LinearLayout localServerPanel;
    private LinearLayout itickPanel;
    private EditText etServerIp;
    private EditText etServerPort;
    private Button btnTestConnection;
    private TextView tvTestResult;
    private EditText etItickToken;
    private Spinner spItickRegion;
    private Button btnTestItick;
    private TextView tvItickTestResult;
    private Button btnSave;
    private Button btnSelectStorage;
    private TextView tvStorageLocation;

    // 服务组件
    private ConfigManager configManager;
    private ExternalStorageManager externalStorageManager;
    private StockService stockService;
    private ItickService itickService;

    // iTick 区域选项
    private String[] itickRegions = {"CN", "US", "HK"};
    private String[] itickRegionNames = {"中国 (A股)", "美国 (美股)", "香港 (港股)"};

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
        itickService = new ItickService(configManager);

        // 初始化 UI
        initViews();

        // 加载当前配置
        loadConfig();

        // 设置按钮点击事件
        setupClickListeners();
    }

    private void initViews() {
        rgDataSource = findViewById(R.id.rg_data_source);
        rbLocal = findViewById(R.id.rb_local);
        rbItick = findViewById(R.id.rb_itick);
        localServerPanel = findViewById(R.id.local_server_panel);
        itickPanel = findViewById(R.id.itick_panel);
        etServerIp = findViewById(R.id.et_server_ip);
        etServerPort = findViewById(R.id.et_server_port);
        btnTestConnection = findViewById(R.id.btn_test_connection);
        tvTestResult = findViewById(R.id.tv_test_result);
        etItickToken = findViewById(R.id.et_itick_token);
        spItickRegion = findViewById(R.id.sp_itick_region);
        btnTestItick = findViewById(R.id.btn_test_itick);
        tvItickTestResult = findViewById(R.id.tv_itick_test_result);
        btnSave = findViewById(R.id.btn_save);
        btnSelectStorage = findViewById(R.id.btn_select_storage);
        tvStorageLocation = findViewById(R.id.tv_storage_location);

        // 设置 iTick 区域下拉框
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, itickRegionNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spItickRegion.setAdapter(adapter);

        // 显示当前存储位置
        updateStorageLocation();
    }

    private void loadConfig() {
        // 加载数据源配置
        String dataSource = configManager.getDataSource();
        if ("itick".equals(dataSource)) {
            rbItick.setChecked(true);
            showItickPanel();
        } else {
            rbLocal.setChecked(true);
            showLocalPanel();
        }

        // 加载本地服务器配置
        etServerIp.setText(configManager.getServerIp());
        etServerPort.setText(String.valueOf(configManager.getServerPort()));

        // 加载 iTick 配置
        etItickToken.setText(configManager.getItickToken());
        String region = configManager.getItickRegion();
        for (int i = 0; i < itickRegions.length; i++) {
            if (itickRegions[i].equals(region)) {
                spItickRegion.setSelection(i);
                break;
            }
        }
    }

    private void setupClickListeners() {
        // 数据源切换
        rgDataSource.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rb_local) {
                    showLocalPanel();
                } else if (checkedId == R.id.rb_itick) {
                    showItickPanel();
                }
            }
        });

        // 测试本地服务器连接
        btnTestConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testLocalConnection();
            }
        });

        // 测试 iTick Token
        btnTestItick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testItickToken();
            }
        });

        // iTick 区域选择
        spItickRegion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 不需要立即保存，在保存按钮时统一保存
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 保存按钮
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
            }
        });

        // 选择外部存储目录按钮
        btnSelectStorage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectExternalStorage();
            }
        });
    }

    private void showLocalPanel() {
        localServerPanel.setVisibility(View.VISIBLE);
        itickPanel.setVisibility(View.GONE);
    }

    private void showItickPanel() {
        localServerPanel.setVisibility(View.GONE);
        itickPanel.setVisibility(View.VISIBLE);
    }

    private void testLocalConnection() {
        String ip = etServerIp.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();

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

        tvTestResult.setVisibility(View.VISIBLE);
        tvTestResult.setText("正在测试连接...");
        tvTestResult.setTextColor(Color.parseColor("#6c757d"));

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

    private void testItickToken() {
        String token = etItickToken.getText().toString().trim();

        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, R.string.itick_no_token, Toast.LENGTH_SHORT).show();
            return;
        }

        tvItickTestResult.setVisibility(View.VISIBLE);
        tvItickTestResult.setText("正在测试 Token...");
        tvItickTestResult.setTextColor(Color.parseColor("#6c757d"));

        itickService.testToken(token, new ItickService.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean valid) {
                if (valid) {
                    tvItickTestResult.setText(R.string.itick_test_success);
                    tvItickTestResult.setTextColor(Color.parseColor("#20c997"));
                } else {
                    tvItickTestResult.setText(R.string.itick_test_failed);
                    tvItickTestResult.setTextColor(Color.parseColor("#dc3545"));
                }
            }

            @Override
            public void onFailure(String error) {
                tvItickTestResult.setText(R.string.itick_test_failed + ": " + error);
                tvItickTestResult.setTextColor(Color.parseColor("#dc3545"));
            }
        });
    }

    private void saveConfig() {
        // 保存数据源配置
        String dataSource = rbItick.isChecked() ? "itick" : "local";
        configManager.setDataSource(dataSource);

        // 保存本地服务器配置
        String ip = etServerIp.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();

        if (!TextUtils.isEmpty(ip)) {
            int port = 8080;
            if (!TextUtils.isEmpty(portStr)) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "端口格式错误", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            configManager.saveServerConfig(ip, port);
        }

        // 保存 iTick 配置
        String token = etItickToken.getText().toString().trim();
        int regionIndex = spItickRegion.getSelectedItemPosition();
        String region = itickRegions[regionIndex];
        configManager.saveItickConfig(token, region);

        // 保存到外部存储
        saveToExternalStorage();

        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void saveToExternalStorage() {
        try {
            JSONObject config = new JSONObject();
            config.put("data_source", configManager.getDataSource());
            config.put("server_ip", configManager.getServerIp());
            config.put("server_port", configManager.getServerPort());
            config.put("itick_token", configManager.getItickToken());
            config.put("itick_region", configManager.getItickRegion());
            config.put("last_code", configManager.getLastCode());

            externalStorageManager.saveConfig(config);
        } catch (Exception e) {
            Toast.makeText(this, "保存到外部存储失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void selectExternalStorage() {
        if (externalStorageManager.isSafAvailable()) {
            externalStorageManager.requestSafDirectory(this);
        } else {
            Toast.makeText(this, "您的系统版本不支持 SAF，配置将保存到: " +
                externalStorageManager.getStorageLocation(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateStorageLocation() {
        tvStorageLocation.setText("配置保存位置: " + externalStorageManager.getStorageLocation());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (externalStorageManager.handleSafResult(requestCode, resultCode, data)) {
            Toast.makeText(this, "已选择外部存储目录，配置卸载后保留", Toast.LENGTH_SHORT).show();
            updateStorageLocation();
            saveToExternalStorage();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stockService.shutdown();
        itickService.shutdown();
    }
}