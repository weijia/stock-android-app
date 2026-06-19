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

## 文档

- [产品需求文档 (PRD)](docs/PRD.md)
- [技术设计文档](docs/DESIGN.md)

## 数据源

应用连接到股票价格 HTTP 服务，API 接口包括：

- `/api/realtime/{code}` - 实时行情
- `/api/kline/{code}?days=30` - K 线数据
- `/api/health` - 健康检查

## 构建

```bash
./gradlew assembleRelease
```

## 许可证

MIT License