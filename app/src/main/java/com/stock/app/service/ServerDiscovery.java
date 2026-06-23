package com.stock.app.service;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UDP 广播服务发现
 * 
 * 监听局域网中股票行情服务器的广播消息，自动发现服务器
 * 
 * 广播消息格式：
 * {
 *   "service": "stock-server",
 *   "instance": "hostname",
 *   "http_port": 8080,
 *   "address": "192.168.x.x",
 *   "timestamp": 1234567890
 * }
 */
public class ServerDiscovery {
    private static final String TAG = "ServerDiscovery";
    
    // UDP 广播端口（与服务器端一致）
    private static final int DISCOVERY_PORT = 8081;
    
    // 发现超时时间
    private static final int DISCOVERY_TIMEOUT = 30000; // 30秒
    
    // WiFi 锁，确保 WiFi 在发现过程中保持活跃
    private WifiManager.MulticastLock multicastLock;
    
    private Context context;
    private ExecutorService executorService;
    private Handler mainHandler;
    private DatagramSocket socket;
    private boolean running = false;
    
    // 发现的服务器列表
    private List<DiscoveredServer> discoveredServers = new ArrayList<>();
    
    // 调试日志回调
    private DebugLogCallback debugLogCallback;
    
    /**
     * 发现的服务器信息
     */
    public static class DiscoveredServer {
        private String instance;
        private String address;
        private int httpPort;
        private long timestamp;
        
        public String getInstance() { return instance; }
        public String getAddress() { return address; }
        public int getHttpPort() { return httpPort; }
        public long getTimestamp() { return timestamp; }
        
        public String getHttpUrl() {
            return "http://" + address + ":" + httpPort;
        }
        
        @Override
        public String toString() {
            return instance + " (" + address + ":" + httpPort + ")";
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
    
    public ServerDiscovery(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // 获取 WiFi 锁，允许接收广播
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("stock-discovery");
            logDebug("WiFi 管理器已获取");
        } else {
            logDebug("警告: WiFi 管理器不可用");
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
        logDebug("[服务发现] 开始启动...");
        logDebug("[服务发现] 监听端口: " + DISCOVERY_PORT);
        logDebug("[服务发现] 超时时间: " + timeout + " ms");
        
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 获取 WiFi 锁
                    if (multicastLock != null) {
                        logDebug("[服务发现] 获取 MulticastLock...");
                        multicastLock.acquire();
                        logDebug("[服务发现] MulticastLock 已获取: " + multicastLock.isHeld());
                    } else {
                        logDebug("[服务发现] 错误: MulticastLock 不可用");
                    }
                    
                    // 创建 UDP socket
                    logDebug("[服务发现] 创建 UDP Socket...");
                    socket = new DatagramSocket(DISCOVERY_PORT);
                    socket.setSoTimeout(1000); // 1秒超时，便于循环检查
                    socket.setReuseAddress(true);
                    logDebug("[服务发现] UDP Socket 已创建");
                    logDebug("[服务发现] 本地端口: " + socket.getLocalPort());
                    logDebug("[服务发现] Socket 超时: 1000 ms");
                    
                    running = true;
                    discoveredServers.clear();
                    
                    logDebug("[服务发现] 开始监听广播...");
                    
                    // 监听广播消息
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    
                    long startTime = System.currentTimeMillis();
                    int receiveCount = 0;
                    
                    while (running && (System.currentTimeMillis() - startTime) < timeout) {
                        try {
                            socket.receive(packet);
                            receiveCount++;
                            
                            String message = new String(packet.getData(), 0, packet.getLength());
                            String sourceIp = packet.getAddress().getHostAddress();
                            int sourcePort = packet.getPort();
                            
                            logDebug("[服务发现] 收到数据包 #" + receiveCount);
                            logDebug("[服务发现]   来源: " + sourceIp + ":" + sourcePort);
                            logDebug("[服务发现]   内容: " + message);
                            
                            // 解析 JSON
                            JSONObject json = new JSONObject(message);
                            String service = json.optString("service", "");
                            
                            logDebug("[服务发现]   service: " + service);
                            
                            if ("stock-server".equals(service)) {
                                DiscoveredServer server = new DiscoveredServer();
                                server.instance = json.optString("instance", "");
                                server.address = json.optString("address", "");
                                server.httpPort = json.optInt("http_port", 8080);
                                server.timestamp = json.optLong("timestamp", 0);
                                
                                logDebug("[服务发现] 发现股票服务器!");
                                logDebug("[服务发现]   实例: " + server.instance);
                                logDebug("[服务发现]   地址: " + server.address);
                                logDebug("[服务发现]   HTTP端口: " + server.httpPort);
                                
                                // 检查是否已存在
                                boolean exists = false;
                                for (DiscoveredServer s : discoveredServers) {
                                    if (s.address.equals(server.address)) {
                                        exists = true;
                                        break;
                                    }
                                }
                                
                                if (!exists) {
                                    discoveredServers.add(server);
                                    logDebug("[服务发现] 添加到服务器列表");
                                    
                                    // 回调通知
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onServerFound(server);
                                        }
                                    });
                                } else {
                                    logDebug("[服务发现] 服务器已存在，跳过");
                                }
                            } else {
                                logDebug("[服务发现] 不是股票服务器，忽略");
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            // 每秒超时，继续等待
                            long elapsed = System.currentTimeMillis() - startTime;
                            if (elapsed % 5000 < 1000) { // 每5秒打印一次状态
                                logDebug("[服务发现] 等待中... (" + elapsed/1000 + "s / " + timeout/1000 + "s)");
                            }
                        }
                    }
                    
                    long totalTime = System.currentTimeMillis() - startTime;
                    logDebug("[服务发现] 监听结束");
                    logDebug("[服务发现] 总时间: " + totalTime + " ms");
                    logDebug("[服务发现] 收到数据包: " + receiveCount + " 个");
                    logDebug("[服务发现] 发现服务器: " + discoveredServers.size() + " 个");
                    
                    // 完成发现
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDiscoveryComplete(discoveredServers);
                        }
                    });
                    
                } catch (Exception e) {
                    logDebug("[服务发现] 错误: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    Log.e(TAG, "发现服务器失败: " + e.getMessage(), e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e.getMessage());
                        }
                    });
                } finally {
                    stop();
                    
                    // 释放 WiFi 锁
                    if (multicastLock != null && multicastLock.isHeld()) {
                        logDebug("[服务发现] 释放 MulticastLock");
                        multicastLock.release();
                    }
                    logDebug("[服务发现] 清理完成");
                }
            }
        });
    }
    
    /**
     * 停止发现
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logDebug("[服务发现] Socket 已关闭");
        }
        Log.i(TAG, "停止监听 UDP 广播");
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        stop();
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
}