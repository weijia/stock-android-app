# 股票实时行情 Android 应用

一个轻量级的 Android 应用，用于实时监控股票价格走势。

## 功能特性

- 股票代码输入与查询
- 实时价格展示（当前价、涨跌幅、成交量等）
- 价格走势折线图
- 成交量柱状图
- 每分钟自动刷新
- 服务器地址配置

## 技术特点

- **纯 Java 实现**：不使用 Kotlin
- **最小依赖**：仅使用 Android SDK 原生 API
- **向后兼容**：支持 Android 4.0 (API Level 14) 及以上
- **轻量级**：无第三方图表库，使用 Canvas 自绘

## 项目结构

```
app/src/main/java/com/stock/app/
├── MainActivity.java        # 主界面
├── SettingsActivity.java    # 设置界面
├── model/
│   ├── StockData.java       # 股票数据模型
│   └── KLineData.java       # K线数据模型
├── network/
│   ├── HttpClient.java      # HTTP请求封装
│   └── ApiResponse.java     # API响应封装
├── parser/
│   └── JsonParser.java      # JSON解析器
├── service/
│   ├── StockService.java    # 数据获取服务
│   └── RefreshScheduler.java # 定时刷新调度
├── view/
│   ├── PriceChartView.java  # 价格走势图
│   └── VolumeChartView.java # 成交量图
└── util/
    ├── ConfigManager.java   # 配置管理
    └── FormatUtil.java      # 格式化工具
```

## 文档

- [产品需求文档 (PRD)](docs/PRD.md)
- [技术设计文档](docs/DESIGN.md)

## 数据源

应用连接到股票价格 HTTP 服务，API 接口包括：

- `/api/realtime/{code}` - 实时行情
- `/api/kline/{code}?days=30` - K 线数据
- `/api/health` - 健康检查

## 构建

### 环境要求

- Android SDK 33
- Gradle 7.5
- JDK 8+

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/weijia/stock-android-app.git

# 进入项目目录
cd stock-android-app

# 构建 Release APK
./gradlew assembleRelease

# APK 输出位置
# app/build/outputs/apk/release/app-release.apk
```

## 使用说明

1. 安装 APK 到 Android 设备
2. 打开应用，点击"设置"配置服务器地址和端口
3. 返回主界面，输入6位股票代码（如 000001）
4. 点击"查询"按钮获取实时行情
5. 应用将每60秒自动刷新数据

## 许可证

MIT License