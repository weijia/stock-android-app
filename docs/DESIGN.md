# 股票实时行情 Android 应用 - 技术设计文档

## 1. 技术架构概览

### 1.1 架构设计原则
- **最小依赖原则**：仅使用 Android SDK 原生 API，避免第三方库依赖
- **向后兼容原则**：所有代码必须兼容 API Level 14 (Android 4.0)
- **简单架构原则**：采用传统的 Activity + View 结构，不引入复杂框架
- **内存优化原则**：避免大对象缓存，及时释放资源

### 1.2 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    Android Application                   │
├─────────────────────────────────────────────────────────┤
│  MainActivity   │  SettingsActivity   │  ChartView      │
│  (主界面)       │  (设置页)           │  (自定义图表)    │
├─────────────────────────────────────────────────────────┤
│  StockService   │  HttpClient         │  JsonParser     │
│  (数据服务)     │  (网络请求)         │  (JSON解析)     │
├─────────────────────────────────────────────────────────┤
│  SharedPreferences │  Handler+Timer                     │
│  (配置存储)       │  (定时刷新)                        │
├─────────────────────────────────────────────────────────┤
│                    HTTP Server API                       │
│              (股票价格 HTTP 服务接口)                    │
└─────────────────────────────────────────────────────────┘
```

## 2. 项目结构

```
StockApp/
├── app/src/main/java/com/stock/app/
│   ├── MainActivity.java        # 主界面
│   ├── SettingsActivity.java    # 设置界面
│   ├── model/
│   │   ├── StockData.java       # 股票数据模型
│   │   └── KLineData.java       # K线数据模型
│   ├── service/
│   │   ├── StockService.java    # 数据获取服务
│   │   └── RefreshScheduler.java # 定时刷新调度
│   ├── network/
│   │   ├── HttpClient.java      # HTTP请求封装
│   │   └── ApiResponse.java     # API响应封装
│   ├── parser/
│   │   └── JsonParser.java      # JSON解析器
│   ├── view/
│   │   ├── PriceChartView.java  # 价格走势图
│   │   └── VolumeChartView.java # 成交量图
│   └── util/
│   │   ├── ConfigManager.java   # 配置管理
│   │   └── FormatUtil.java      # 格式化工具
├── res/layout/
│   ├── activity_main.xml
│   └── activity_settings.xml
└── AndroidManifest.xml
```

## 3. 核心类设计

### 3.1 数据模型

**StockData.java - 实时行情数据**
```java
public class StockData {
    private String code;        // 股票代码
    private String name;        // 股票名称
    private double price;       // 当前价格
    private double lastClose;   // 昨收价
    private double open;        // 开盘价
    private double high;        // 最高价
    private double low;         // 最低价
    private double changeAmt;   // 涨跌额
    private double changePct;   // 涨跌幅
    private double volume;      // 成交量（手）
    private double amount;      // 成交额（万元）
    private String timestamp;   // 时间戳
}
```

**KLineData.java - K线数据**
```java
public class KLineData {
    private String date;        // 日期 YYYY-MM-DD
    private double open;        // 开盘价
    private double high;        // 最高价
    private double low;         // 最低价
    private double close;       // 收盘价
    private double volume;      // 成交量
}
```

### 3.2 网络请求模块

使用 HttpURLConnection（API Level 1 就已存在，完全兼容）：
- 连接超时：10 秒
- 读取超时：10 秒
- 网络请求必须在子线程执行

### 3.3 JSON 解析模块

使用 Android 内置 JSONObject（API Level 1 就已存在）：
- 解析 `/api/realtime` 返回的实时行情数据
- 解析 `/api/kline` 返回的 K 线数据数组

### 3.4 图表绘制模块

**PriceChartView.java - 价格走势图**
- 继承 View，使用 Canvas 自绘
- 绘制网格线、折线、坐标轴标签
- 支持触摸查看具体数值

**VolumeChartView.java - 成交量柱状图**
- 继承 View，使用 Canvas 自绘
- 绘制柱状图，X 轴与价格图对齐

### 3.5 定时刷新模块

**RefreshScheduler.java**
- 使用 Handler + Runnable 实现 60 秒定时刷新
- 刷新任务在子线程执行网络请求
- 通过 Handler 回到主线程更新 UI

### 3.6 配置管理模块

**ConfigManager.java**
- 使用 SharedPreferences 存储服务器配置
- 默认服务器地址：localhost
- 默认端口：8080

## 4. API 接口对接

### 4.1 接口 URL 构建
| 接口 | URL 格式 | 示例 |
|-----|---------|------|
| 实时行情 | `{baseUrl}/api/realtime/{code}` | `http://192.168.1.100:8080/api/realtime/000001` |
| K线数据 | `{baseUrl}/api/kline/{code}?days=30` | `http://192.168.1.100:8080/api/kline/000001?days=30` |
| 健康检查 | `{baseUrl}/api/health` | `http://192.168.1.100:8080/api/health` |

### 4.2 错误处理策略
| 错误类型 | 处理方式 |
|---------|---------|
| 网络超时 | 显示"网络超时，请稍后重试"，不中断定时刷新 |
| 服务器错误 (500) | 显示"服务器异常"，记录日志 |
| 股票不存在 (404) | 显示"股票代码不存在" |
| JSON 解析失败 | 显示"数据格式错误"，记录原始响应 |

## 5. 兼容性设计

### 5.1 API Level 14 兼容要点
- GridLayout：API 14 引入，可直接使用
- SharedPreferences：API 1 就已存在，完全兼容
- HttpURLConnection：API 1 就已存在，完全兼容
- Handler：API 1 就已存在，完全兼容
- JSONObject：API 1 就已存在，完全兼容
- Canvas 绘图：API 1 就已存在，完全兼容

### 5.2 build.gradle 配置
```gradle
android {
    compileSdkVersion 33
    defaultConfig {
        applicationId "com.stock.app"
        minSdkVersion 14        // 最低支持 Android 4.0
        targetSdkVersion 33     // 目标最新版本
        versionCode 1
        versionName "1.0.0"
    }
}
dependencies {
    // 无第三方依赖
}
```

## 6. 性能优化

### 6.1 内存优化策略
- K 线数据最多保留 30 天，避免大数组
- Activity onDestroy 时停止定时任务，释放 Handler
- Handler 使用弱引用或静态内部类避免内存泄漏
- 使用矢量图标或小尺寸 PNG

### 6.2 网络优化策略
- 连接超时 10 秒，读取超时 10 秒
- 网络失败不重试，等待下次定时刷新
- 同时只允许一个网络请求，避免重复请求

## 7. 测试计划

### 7.1 功能测试
| 测试项 | 测试内容 |
|-------|---------|
| 股票查询 | 输入有效/无效代码，验证查询结果 |
| 数据展示 | 验证价格、涨跌幅、成交量等字段正确显示 |
| 图表绘制 | 验证折线图和柱状图数据准确、渲染正确 |
| 自动刷新 | 验证 60 秒刷新间隔，刷新后数据更新 |
| 配置保存 | 修改服务器配置后重启应用，验证配置保留 |

### 7.2 兼容性测试
| 测试设备 | Android 版本 | 测试重点 |
|---------|-------------|---------|
| 模拟器 | Android 4.0 (API 14) | 最低版本兼容性 |
| 模拟器 | Android 5.0 (API 21) | 中间版本兼容性 |
| 真机 | Android 8.0+ (API 26+) | 主链版本兼容性 |

## 8. 发布计划

### 8.1 APK 打包
```bash
./gradlew assembleRelease
# APK 输出位置: app/build/outputs/apk/release/app-release.apk
```

### 8.2 GitHub Release 发布
- 创建 GitHub Release
- 上传 APK 文件
- 编写 Release Notes（版本号、功能列表、已知问题）