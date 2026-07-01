package com.stock.app.service;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.stock.app.util.DebugLogger;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * mDNS 服务发现（推荐方案）
 * 
 * 使用 Android NSD API 发现 mDNS 服务
 * mDNS 不会被 Android 硬件过滤器阻止，比 UDP 广播更可靠
 * 
 * 服务类型: _stock-server._tcp
 */
public class MDNSDiscovery {
    private static final String TAG = "MDNSDiscovery";
    
    // mDNS 服务类型（与服务器端一致）
    private static final String SERVICE_TYPE = "_stock-server._tcp.";
    
    private Context context;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private Handler mainHandler;
    private ExecutorService executorService;
    
    // 发现的服务器列表
    private List<DiscoveredServer> discoveredServers = new ArrayList<>();
    
    // 调试日志回调
    private DebugLogCallback debugLogCallback;
    
    // 是否正在发现
    private boolean discovering = false;
    
    /**
     * 发现的服务器信息
     */
    public static class DiscoveredServer {
        private String serviceName;
        private String host;
        private int port;
        private InetAddress address;
        
        public String getServiceName() { return serviceName; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public InetAddress getAddress() { return address; }
        
        public String getHttpUrl() {
            return "http://" + host + ":" + port;
        }
        
        @Override
        public String toString() {
            return serviceName + " (" + host + ":" + port + ")";
        }
    }
    
    /**
     * 发现回调接口
     */
    public interface DiscoveryCallback {
        void onServerFound(DiscoveredServer server);
        void onDiscoveryComplete(List<DiscoveredServer> servers);
        void onError(String error);
    }
    
    /**
     * 调试日志回调接口
     */
    public interface DebugLogCallback {
        void onDebugLog(String log);
    }
    
    public MDNSDiscovery(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newSingleThreadExecutor();
        
        // 获取 NSD Manager
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsdManager != null) {
            logDebug("[mDNS] NSD Manager 已获取");
        } else {
            logDebug("[mDNS] 错误: NSD Manager 不可用");
        }
    }
    
    /**
     * 设置调试日志回调
     */
    public void setDebugLogCallback(DebugLogCallback callback) {
        this.debugLogCallback = callback;
    }
    
    /**
     * 输出调试日志
     */
    private void logDebug(String message) {
        Log.d(TAG, message);
        
        // 同时输出到 DebugLogger
        DebugLogger logger = DebugLogger.getInstance();
        if (logger != null) {
            logger.log(TAG, message);
        }
        
        // 兼容旧的回调方式
        if (debugLogCallback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    debugLogCallback.onDebugLog(message);
                }
            });
        }
    }
    
    /**
     * 开始发现服务器
     * @param timeout 超时时间（毫秒）
     * @param callback 回调
     */
    public void startDiscovery(int timeout, final DiscoveryCallback callback) {
        logDebug("[mDNS] 开始启动...");
        logDebug("[mDNS] 服务类型: " + SERVICE_TYPE);
        logDebug("[mDNS] 超时时间: " + timeout + " ms");
        
        if (nsdManager == null) {
            logDebug("[mDNS] 错误: NSD Manager 不可用");
            callback.onError("NSD Manager 不可用");
            return;
        }
        
        discoveredServers.clear();
        discovering = true;
        
        // 创建发现监听器
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                logDebug("[mDNS] 发现启动失败: " + errorCode);
                discovering = false;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("发现启动失败: " + errorCode);
                    }
                });
            }
            
            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                logDebug("[mDNS] 发现停止失败: " + errorCode);
            }
            
            @Override
            public void onDiscoveryStarted(String serviceType) {
                logDebug("[mDNS] 发现已启动");
                logDebug("[mDNS] 正在搜索服务类型: " + serviceType);
            }
            
            @Override
            public void onDiscoveryStopped(String serviceType) {
                logDebug("[mDNS] 发现已停止");
                discovering = false;
                
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onDiscoveryComplete(discoveredServers);
                    }
                });
            }
            
            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                String serviceName = serviceInfo.getServiceName();
                String serviceType = serviceInfo.getServiceType();
                
                logDebug("[mDNS] 发现服务: " + serviceName);
                logDebug("[mDNS]   类型: " + serviceType);
                
                // 解析服务获取详细信息
                resolveService(serviceInfo, callback);
            }
            
            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                String serviceName = serviceInfo.getServiceName();
                logDebug("[mDNS] 服务丢失: " + serviceName);
                
                // 从列表中移除
                for (int i = 0; i < discoveredServers.size(); i++) {
                    if (discoveredServers.get(i).serviceName.equals(serviceName)) {
                        discoveredServers.remove(i);
                        break;
                    }
                }
            }
        };
        
        // 开始发现
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
            logDebug("[mDNS] NSD discoverServices 已调用");
            
            // 设置超时停止
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (discovering) {
                        stopDiscovery();
                    }
                }
            }, timeout);
            
        } catch (Exception e) {
            logDebug("[mDNS] 启动发现异常: " + e.getMessage());
            callback.onError("启动发现异常: " + e.getMessage());
        }
    }
    
    /**
     * 解析服务获取详细信息
     */
    private void resolveService(NsdServiceInfo serviceInfo, final DiscoveryCallback callback) {
        logDebug("[mDNS] 解析服务: " + serviceInfo.getServiceName());
        
        NsdManager.ResolveListener resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                logDebug("[mDNS] 解析失败: " + serviceInfo.getServiceName() + ", 错误: " + errorCode);
            }
            
            @Override
            public void onServiceResolved(NsdServiceInfo resolvedServiceInfo) {
                String serviceName = resolvedServiceInfo.getServiceName();
                InetAddress host = resolvedServiceInfo.getHost();
                int port = resolvedServiceInfo.getPort();
                String hostAddress = host.getHostAddress();
                
                logDebug("[mDNS] 服务已解析: " + serviceName);
                logDebug("[mDNS]   主机: " + hostAddress);
                logDebug("[mDNS]   端口: " + port);
                
                // 创建服务器信息
                DiscoveredServer server = new DiscoveredServer();
                server.serviceName = serviceName;
                server.host = hostAddress;
                server.port = port;
                server.address = host;
                
                // 检查是否已存在
                boolean exists = false;
                for (DiscoveredServer s : discoveredServers) {
                    if (s.host.equals(server.host)) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    discoveredServers.add(server);
                    logDebug("[mDNS] 添加到服务器列表");
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onServerFound(server);
                        }
                    });
                } else {
                    logDebug("[mDNS] 服务器已存在，跳过");
                }
            }
        };
        
        try {
            nsdManager.resolveService(serviceInfo, resolveListener);
        } catch (Exception e) {
            logDebug("[mDNS] 解析服务异常: " + e.getMessage());
        }
    }
    
    /**
     * 停止发现
     */
    public void stopDiscovery() {
        if (discovering && nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
                logDebug("[mDNS] 已请求停止发现");
            } catch (Exception e) {
                logDebug("[mDNS] 停止发现异常: " + e.getMessage());
            }
        }
        discovering = false;
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        stopDiscovery();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    /**
     * 获取已发现的服务器列表
     */
    public List<DiscoveredServer> getDiscoveredServers() {
        return new ArrayList<>(discoveredServers);
    }
    
    /**
     * 是否正在发现
     */
    public boolean isDiscovering() {
        return discovering;
    }
}