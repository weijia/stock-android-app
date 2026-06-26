package com.stock.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.stock.app.util.DebugLogger;

/**
 * 调试日志查看界面
 *
 * 在设置界面中点击"查看调试日志"进入。
 * 显示 APP 运行过程中收集的所有调试日志，包括：
 * - 启动流程
 * - 服务器连接
 * - 节点配置同步（fetchNodeConfig 等）
 * - 股票查询
 * - HTTP 请求/响应详情
 */
public class DebugLogActivity extends Activity {

    private TextView tvLogContent;
    private TextView tvLogCount;
    private ScrollView svLogContent;
    private DebugLogger debugLogger;

    public static void start(Activity activity) {
        Intent intent = new Intent(activity, DebugLogActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_log);

        debugLogger = DebugLogger.getInstance(this);

        initViews();
        setupClickListeners();
        refreshLogs();
    }

    private void initViews() {
        tvLogContent = findViewById(R.id.tv_log_content);
        tvLogCount = findViewById(R.id.tv_log_count);
        svLogContent = findViewById(R.id.sv_log_content);
    }

    private void setupClickListeners() {
        Button btnRefresh = findViewById(R.id.btn_refresh_log);
        Button btnClear = findViewById(R.id.btn_clear_log);
        Button btnCopy = findViewById(R.id.btn_copy_log);

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshLogs();
                Toast.makeText(DebugLogActivity.this, "已刷新", Toast.LENGTH_SHORT).show();
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                debugLogger.clear();
                refreshLogs();
                Toast.makeText(DebugLogActivity.this, "日志已清空", Toast.LENGTH_SHORT).show();
            }
        });

        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String logs = debugLogger.getLogs();
                if (logs.isEmpty()) {
                    Toast.makeText(DebugLogActivity.this, "没有日志可复制", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Debug Logs", logs);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(DebugLogActivity.this, "日志已复制到剪贴板 (" + debugLogger.getLogCount() + " 条)", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshLogs() {
        int count = debugLogger.getLogCount();
        tvLogCount.setText("共 " + count + " 条日志");

        if (count == 0) {
            tvLogContent.setText("暂无日志记录\n\n日志会在 APP 启动、连接服务器、同步配置、查询股票时自动记录。\n请返回主界面操作后再次查看。");
        } else {
            tvLogContent.setText(debugLogger.getLogs());
        }

        // 自动滚动到底部
        svLogContent.post(new Runnable() {
            @Override
            public void run() {
                svLogContent.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时刷新日志
        refreshLogs();
    }
}
