package com.stock.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.stock.app.model.KLineData;

import java.util.List;

/**
 * 成交量柱状图自定义视图
 * 使用 Canvas 绘制柱状图，兼容 API Level 14
 */
public class VolumeChartView extends View {
    private List<KLineData> data;
    private Paint barPaint;
    private Paint barUpPaint;
    private Paint barDownPaint;
    private Paint textPaint;
    private Paint gridPaint;

    private float padding = 40f;
    private float textHeight = 20f;

    public VolumeChartView(Context context) {
        super(context);
        initPaint();
    }

    public VolumeChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint();
    }

    public VolumeChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaint();
    }

    private void initPaint() {
        // 柱状图画笔（上涨）
        barUpPaint = new Paint();
        barUpPaint.setColor(Color.parseColor("#20c997"));
        barUpPaint.setStyle(Paint.Style.FILL);
        barUpPaint.setAntiAlias(true);

        // 柱状图画笔（下跌）
        barDownPaint = new Paint();
        barDownPaint.setColor(Color.parseColor("#dc3545"));
        barDownPaint.setStyle(Paint.Style.FILL);
        barDownPaint.setAntiAlias(true);

        // 默认柱状图画笔
        barPaint = barUpPaint;

        // 文字画笔
        textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#6c757d"));
        textPaint.setTextSize(12f);
        textPaint.setAntiAlias(true);

        // 网格画笔
        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#dee2e6"));
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);
    }

    /**
     * 设置数据
     * @param data K线数据列表
     */
    public void setData(List<KLineData> data) {
        this.data = data;
        invalidate(); // 触发重绘
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (data == null || data.isEmpty()) {
            drawEmptyState(canvas);
            return;
        }

        int width = getWidth();
        int height = getHeight();

        // 计算绘图区域
        float chartWidth = width - padding * 2;
        float chartHeight = height - padding - textHeight;

        // 计算最大成交量
        double maxVolume = 0;
        for (KLineData k : data) {
            if (k.getVolume() > maxVolume) maxVolume = k.getVolume();
        }

        if (maxVolume == 0) maxVolume = 1;

        // 绘制网格线
        drawGrid(canvas, chartWidth, chartHeight);

        // 绘制柱状图
        drawBarChart(canvas, chartWidth, chartHeight, maxVolume);

        // 绘制坐标轴标签
        drawAxisLabels(canvas, chartWidth, chartHeight, maxVolume);
    }

    private void drawEmptyState(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        Paint emptyPaint = new Paint();
        emptyPaint.setColor(Color.parseColor("#adb5bd"));
        emptyPaint.setTextSize(14f);
        emptyPaint.setAntiAlias(true);
        emptyPaint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText("暂无数据", width / 2f, height / 2f, emptyPaint);
    }

    private void drawGrid(Canvas canvas, float chartWidth, float chartHeight) {
        // 绘制水平网格线（2条）
        for (int i = 0; i <= 2; i++) {
            float y = padding + (chartHeight / 2) * i;
            canvas.drawLine(padding, y, padding + chartWidth, y, gridPaint);
        }
    }

    private void drawBarChart(Canvas canvas, float chartWidth, float chartHeight,
                              double maxVolume) {
        float barWidth = chartWidth / data.size() * 0.8f;
        float gap = chartWidth / data.size() * 0.2f;

        for (int i = 0; i < data.size(); i++) {
            KLineData kline = data.get(i);

            // 根据涨跌选择颜色
            Paint currentBarPaint;
            if (i > 0) {
                // 与前一天比较
                if (kline.getClose() >= data.get(i - 1).getClose()) {
                    currentBarPaint = barUpPaint;
                } else {
                    currentBarPaint = barDownPaint;
                }
            } else if (i == 0 && data.size() > 1) {
                // 第一根柱子，与第二天比较开盘价
                if (kline.getClose() >= kline.getOpen()) {
                    currentBarPaint = barUpPaint;
                } else {
                    currentBarPaint = barDownPaint;
                }
            } else {
                // 只有一根柱子，默认颜色
                currentBarPaint = barUpPaint;
            }

            float left = padding + i * (barWidth + gap);
            float barHeight = (float) (kline.getVolume() / maxVolume * chartHeight);
            float top = padding + chartHeight - barHeight;
            float right = left + barWidth;

            canvas.drawRect(left, top, right, padding + chartHeight, currentBarPaint);
        }
    }

    private void drawAxisLabels(Canvas canvas, float chartWidth, float chartHeight,
                                double maxVolume) {
        textPaint.setTextAlign(Paint.Align.LEFT);

        // 绘制最大成交量标签（顶部）
        String maxVolumeStr = formatVolume(maxVolume);
        canvas.drawText(maxVolumeStr, 5f, padding + 5f, textPaint);

        // 绘制日期标签（底部）
        textPaint.setTextAlign(Paint.Align.CENTER);
        if (data.size() > 0) {
            // 第一个日期
            canvas.drawText(data.get(0).getShortDate(), padding, padding + chartHeight + textHeight, textPaint);
            // 最后一个日期
            canvas.drawText(data.get(data.size() - 1).getShortDate(),
                    padding + chartWidth, padding + chartHeight + textHeight, textPaint);
        }
    }

    /**
     * 格式化成交量显示
     */
    private String formatVolume(double volume) {
        if (volume >= 100000000) {
            return String.format("%.2f亿", volume / 100000000);
        } else if (volume >= 10000) {
            return String.format("%.2f万", volume / 10000);
        } else {
            return String.format("%.0f", volume);
        }
    }
}