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
    
    public ServerDiscovery(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // 获取 WiFi 锁，允许接收广播
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("stock-discovery");
        }
    }
    
    /**
     * 开始发现服务器
     * @param timeout 超时时间（毫秒）
     * @param callback 回调
     */
    public void startDiscovery(int timeout, final DiscoveryCallback callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 获取 WiFi 锁
                    if (multicastLock != null) {
                        multicastLock.acquire();
                    }
                    
                    // 创建 UDP socket
                    socket = new DatagramSocket(DISCOVERY_PORT);
                    socket.setSoTimeout(timeout);
                    socket.setReuseAddress(true);
                    
                    running = true;
                    discoveredServers.clear();
                    
                    Log.i(TAG, "开始监听 UDP 广播: 端口 " + DISCOVERY_PORT);
                    
                    // 监听广播消息
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    
                    long startTime = System.currentTimeMillis();
                    
                    while (running && (System.currentTimeMillis() - startTime) < timeout) {
                        try {
                            socket.receive(packet);
                            
                            String message = new String(packet.getData(), 0, packet.getLength());
                            Log.d(TAG, "收到广播消息: " + message);
                            
                            // 解析 JSON
                            JSONObject json = new JSONObject(message);
                            String service = json.optString("service", "");
                            
                            if ("stock-server".equals(service)) {
                                DiscoveredServer server = new DiscoveredServer();
                                server.instance = json.optString("instance", "");
                                server.address = json.optString("address", "");
                                server.httpPort = json.optInt("http_port", 8080);
                                server.timestamp = json.optLong("timestamp", 0);
                                
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
                                    Log.i(TAG, "发现服务器: " + server);
                                    
                                    // 回调通知
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onServerFound(server);
                                        }
                                    });
                                }
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            // 超时，继续等待
                        }
                    }
                    
                    // 完成发现
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDiscoveryComplete(discoveredServers);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "发现服务器失败: " + e.getMessage());
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
                        multicastLock.release();
                    }
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