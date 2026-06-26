package com.stock.app.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP 客户端封装
 * 使用 HttpURLConnection 实现，兼容 API Level 14
 * 支持 GET/POST/DELETE 和自定义 Header
 */
public class HttpClient {
    private static final int TIMEOUT = 10000; // 10秒超时

    /**
     * 执行 GET 请求
     */
    public String get(String url) throws IOException {
        return request(url, "GET", null, null, null);
    }

    /**
     * 执行带自定义 Header 的 GET 请求
     */
    public String getWithHeader(String url, String headerName, String headerValue) throws IOException {
        return request(url, "GET", null, headerName, headerValue);
    }

    /**
     * 执行 POST 请求
     */
    public String post(String url, String body) throws IOException {
        return request(url, "POST", body, null, null);
    }

    /**
     * 执行带自定义 Header 的 POST 请求
     */
    public String postWithHeader(String url, String body, String headerName, String headerValue) throws IOException {
        return request(url, "POST", body, headerName, headerValue);
    }

    /**
     * 执行 DELETE 请求
     */
    public String delete(String url) throws IOException {
        return request(url, "DELETE", null, null, null);
    }

    /**
     * 执行带自定义 Header 的 DELETE 请求
     */
    public String deleteWithHeader(String url, String headerName, String headerValue) throws IOException {
        return request(url, "DELETE", null, headerName, headerValue);
    }

    /**
     * 通用 HTTP 请求方法
     */
    private String request(String url, String method, String body,
                           String headerName, String headerValue) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setDoInput(true);

            // 添加自定义 Header
            if (headerName != null && headerValue != null) {
                connection.setRequestProperty(headerName, headerValue);
            }

            // POST 请求需要发送请求体
            if ("POST".equals(method) && body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                OutputStream os = connection.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.flush();
                os.close();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream in = connection.getInputStream();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                throw new IOException("HTTP error: " + responseCode);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 检查网络是否可用
     */
    public boolean isAvailable(String url) {
        try {
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }
}