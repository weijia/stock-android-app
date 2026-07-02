package com.stock.app.service;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.stock.app.util.ConfigManager;
import com.stock.app.util.DebugLogger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自动连接管理器
 * 
 * 实现启动时的自动连接逻辑：
 * 1. 先尝试连接上次保存的服务器
 * 2. 如果上次服务器可用，直接连接
 * 3. 如果不可用，开始搜索新服务器
 * 4. 如果只找到一台服务器，自动连接
 */
public class AutoConnectManager {
    private static final String TAG = "AutoConnectManager";
    
    // 连接超时时间
    private static final int CONNECT_TIMEOUT = 5000;  // 5秒
    private static final int DISCOVERY_TIMEOUT = 10000;  // 10秒
    
    private Context context;
    private ConfigManager configManager;
    private StockService stockService;
    private MDNSDiscovery mdnsDiscovery;
    private ServerDiscovery udpDiscovery;
    private ExecutorService executorService;
    private Handler mainHandler;
    
    // 回调接口
    private AutoConnectCallback callback;
    
    /**
     * 自动连接回调接口
     */
    public interface AutoConnectCallback {
        // 连接成功
        void onConnected(String serverIp, int serverPort);
        // 需要用户选择服务器（多台服务器时）
        void onNeedSelectServer(List<MDNSDiscovery.DiscoveredServer> servers);
        // 连接失败
        void onConnectionFailed(String error);
        // 进度更新
        void onProgressUpdate(String message);
    }
    
    public AutoConnectManager(Context context, ConfigManager configManager, StockService stockService) {
        this.context = context;
        this.configManager = configManager;
        this.stockService = stockService;
        this.mdnsDiscovery = new MDNSDiscovery(context);
        this.udpDiscovery = new ServerDiscovery(context);
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 设置回调
     */
    public void setCallback(AutoConnectCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 开始自动连接流程
     */
    public void startAutoConnect() {
        DebugLogger logger = DebugLogger.getInstance();
        if (logger != null) {
            logger.log(TAG, "开始自动连接流程");
        }
        
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                // 步骤1: 尝试连接上次的服务器
                String lastServerIp = configManager.getServerIp();
                int lastServerPort = configManager.getServerPort();
                
                String msg = "尝试连接上次服务器: " + lastServerIp + ":" + lastServerPort;
                updateProgress(msg);
                Log.d(TAG, msg);
                if (logger != null) {
                    logger.log(TAG, msg);
                }
                
                if (tryConnectServer(lastServerIp, lastServerPort)) {
                    Log.d(TAG, "上次服务器连接成功");
                    if (logger != null) {
                        logger.log(TAG, "上次服务器连接成功");
                    }
                    updateProgress("已连接到服务器: " + lastServerIp);
                    notifyConnected(lastServerIp, lastServerPort);
                    return;
                }
                
                Log.d(TAG, "上次服务器连接失败，开始搜索新服务器");
                if (logger != null) {
                    logger.log(TAG, "上次服务器连接失败，开始搜索新服务器");
                }
                updateProgress("上次服务器不可用，正在搜索新服务器...");
                
                // 步骤2: 搜索新服务器
                searchNewServers();
            }
        });
    }
    
    /**
     * 尝试连接服务器
     */
    private boolean tryConnectServer(String ip, int port) {
        // 临时更新配置
        String originalIp = configManager.getServerIp();
        int originalPort = configManager.getServerPort();
        
        configManager.setServerIp(ip);
        configManager.setServerPort(port);
        
        try {
            // 发送健康检查请求
            final boolean[] result = {false};
            final Object lock = new Object();
            
            stockService.checkHealth(new StockService.DataCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean healthy) {
                    result[0] = healthy;
                    synchronized (lock) {
                        lock.notify();
                    }
                }
                
                @Override
                public void onFailure(String error) {
                    result[0] = false;
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            });
            
            // 等待结果
            synchronized (lock) {
                lock.wait(CONNECT_TIMEOUT);
            }
            
            if (!result[0]) {
                // 恢复原配置
                configManager.setServerIp(originalIp);
                configManager.setServerPort(originalPort);
            }
            
            return result[0];
            
        } catch (Exception e) {
            Log.e(TAG, "连接服务器异常: " + e.getMessage());
            // 恢复原配置
            configManager.setServerIp(originalIp);
            configManager.setServerPort(originalPort);
            return false;
        }
    }
    
    /**
     * 搜索新服务器
     * 降级策略：mDNS → UDP 广播 → 提示手动输入
     */
    private void searchNewServers() {
        DebugLogger logger = DebugLogger.getInstance();
        
        // 检查 API 版本，低版本直接跳过 mDNS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // API 16+: 先尝试 mDNS
            if (logger != null) {
                logger.log(TAG, "开始 mDNS 服务器发现 (API " + Build.VERSION.SDK_INT + " >= 16)");
            }
            searchViaMDNS();
        } else {
            // API 15 及以下: mDNS 不可用，直接使用 UDP
            if (logger != null) {
                logger.log(TAG, "API " + Build.VERSION.SDK_INT + " < 16，跳过 mDNS，直接使用 UDP 发现");
            }
            searchViaUDP();
        }
    }
    
    /**
     * 通过 mDNS 搜索服务器
     */
    private void searchViaMDNS() {
        DebugLogger logger = DebugLogger.getInstance();
        if (logger != null) {
            logger.log(TAG, "开始 mDNS 服务器发现，超时: " + DISCOVERY_TIMEOUT + "ms");
        }
        
        mdnsDiscovery.startDiscovery(DISCOVERY_TIMEOUT, new MDNSDiscovery.DiscoveryCallback() {
            @Override
            public void onServerFound(MDNSDiscovery.DiscoveredServer server) {
                String msg = "[mDNS] 发现服务器: " + server.getHost() + ":" + server.getPort();
                Log.d(TAG, msg);
                if (logger != null) {
                    logger.log(TAG, msg);
                }
                updateProgress("发现服务器: " + server.getHost());
            }
            
            @Override
            public void onDiscoveryComplete(List<MDNSDiscovery.DiscoveredServer> servers) {
                String msg = "[mDNS] 搜索完成，发现 " + servers.size() + " 台服务器";
                Log.d(TAG, msg);
                if (logger != null) {
                    logger.log(TAG, msg);
                    for (MDNSDiscovery.DiscoveredServer s : servers) {
                        logger.log(TAG, "  - " + s.getHost() + ":" + s.getPort());
                    }
                }
                
                if (!servers.isEmpty()) {
                    handleDiscoveredServers(servers);
                } else {
                    // mDNS 未发现，降级到 UDP
                    if (logger != null) {
                        logger.log(TAG, "mDNS 未发现服务器，降级到 UDP 广播发现");
                    }
                    searchViaUDP();
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "[mDNS] 搜索服务器失败: " + error);
                if (logger != null) {
                    logger.error(TAG, "mDNS 失败: " + error + "，降级到 UDP 广播发现");
                }
                // mDNS 失败，降级到 UDP
                searchViaUDP();
            }
        });
    }
    
    /**
     * 通过 UDP 广播搜索服务器（降级方案）
     */
    private void searchViaUDP() {
        DebugLogger logger = DebugLogger.getInstance();
        if (logger != null) {
            logger.log(TAG, "开始 UDP 广播服务器发现，超时: " + DISCOVERY_TIMEOUT + "ms");
        }
        updateProgress("正在通过 UDP 搜索服务器...");
        
        udpDiscovery.setDebugLogCallback(new ServerDiscovery.DebugLogCallback() {
            @Override
            public void onDebugLog(String log) {
                DebugLogger dbg = DebugLogger.getInstance();
                if (dbg != null) {
                    dbg.log(TAG, "[UDP] " + log);
                }
            }
        });
        
        udpDiscovery.startDiscovery(DISCOVERY_TIMEOUT, new ServerDiscovery.DiscoveryCallback() {
            @Override
            public void onServerFound(ServerDiscovery.DiscoveredServer server) {
                String msg = "[UDP] 发现服务器: " + server.getHost() + ":" + server.getPort();
                Log.d(TAG, msg);
                DebugLogger dbg = DebugLogger.getInstance();
                if (dbg != null) {
                    dbg.log(TAG, msg);
                }
                updateProgress("发现服务器: " + server.getHost());
            }
            
            @Override
            public void onDiscoveryComplete(List<ServerDiscovery.DiscoveredServer> servers) {
                String msg = "[UDP] 搜索完成，发现 " + servers.size() + " 台服务器";
                Log.d(TAG, msg);
                DebugLogger dbg = DebugLogger.getInstance();
                if (dbg != null) {
                    dbg.log(TAG, msg);
                    for (ServerDiscovery.DiscoveredServer s : servers) {
                        dbg.log(TAG, "  - " + s.getHost() + ":" + s.getPort());
                    }
                }
                
                if (!servers.isEmpty()) {
                    for (ServerDiscovery.DiscoveredServer s : servers) {
                        String host = s.getAddress();
                        int port = s.getHttpPort();
                        if (host != null && port > 0) {
                            configManager.setServerIp(host);
                            configManager.setServerPort(port);
                            String connectMsg = "通过 UDP 发现并连接到: " + host + ":" + port;
                            updateProgress(connectMsg);
                            if (dbg != null) {
                                dbg.log(TAG, connectMsg);
                            }
                            notifyConnected(host, port);
                            return;
                        }
                    }
                    notifyConnectionFailed("UDP 发现的服务器地址无效");
                } else {
                    // 所有发现方式都失败
                    if (dbg != null) {
                        dbg.error(TAG, "mDNS 和 UDP 均未发现服务器，请在设置中手动输入服务器地址");
                    }
                    notifyConnectionFailed("未找到服务器（mDNS 和 UDP 均失败）。请在设置中手动输入服务器 IP 地址和端口。");
                }
            }
        });
    }
    
    /**
     * 处理 mDNS 发现的服务器列表
     */
    private void handleDiscoveredServers(List<MDNSDiscovery.DiscoveredServer> servers) {
        DebugLogger logger = DebugLogger.getInstance();
        
        if (servers.isEmpty()) {
            updateProgress("未找到服务器");
            if (logger != null) {
                logger.error(TAG, "未找到可用的服务器");
            }
            notifyConnectionFailed("未找到可用的服务器");
        } else if (servers.size() == 1) {
            // 只有一台服务器，自动连接
            MDNSDiscovery.DiscoveredServer server = servers.get(0);
            String connectMsg = "自动连接到: " + server.getHost() + ":" + server.getPort();
            updateProgress(connectMsg);
            if (logger != null) {
                logger.log(TAG, connectMsg);
            }
            
            configManager.setServerIp(server.getHost());
            configManager.setServerPort(server.getPort());
            
            notifyConnected(server.getHost(), server.getPort());
        } else {
            // 多台服务器，需要用户选择
            updateProgress("发现多台服务器，请选择");
            if (logger != null) {
                logger.log(TAG, "发现多台服务器，需要用户选择");
            }
            notifyNeedSelectServer(servers);
        }
    }
    
    /**
     * 用户选择服务器后连接
     */
    public void connectToServer(MDNSDiscovery.DiscoveredServer server) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                updateProgress("连接到: " + server.getHost());
                
                if (tryConnectServer(server.getHost(), server.getPort())) {
                    configManager.setServerIp(server.getHost());
                    configManager.setServerPort(server.getPort());
                    notifyConnected(server.getHost(), server.getPort());
                } else {
                    notifyConnectionFailed("无法连接到服务器: " + server.getHost());
                }
            }
        });
    }
    
    /**
     * 更新进度
     */
    private void updateProgress(String message) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onProgressUpdate(message);
                }
            });
        }
    }
    
    /**
     * 通知连接成功
     */
    private void notifyConnected(String ip, int port) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onConnected(ip, port);
                }
            });
        }
    }
    
    /**
     * 通知需要选择服务器
     */
    private void notifyNeedSelectServer(List<MDNSDiscovery.DiscoveredServer> servers) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onNeedSelectServer(servers);
                }
            });
        }
    }
    
    /**
     * 通知连接失败
     */
    private void notifyConnectionFailed(String error) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onConnectionFailed(error);
                }
            });
        }
    }
    
    /**
     * 关闭资源
     */
    public void shutdown() {
        if (mdnsDiscovery != null) {
            mdnsDiscovery.shutdown();
        }
        if (udpDiscovery != null) {
            udpDiscovery.shutdown();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}