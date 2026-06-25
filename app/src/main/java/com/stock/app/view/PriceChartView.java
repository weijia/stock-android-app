package com.stock.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.stock.app.model.KLineData;

import java.util.ArrayList;
import java.util.List;

/**
 * 价格走势图自定义视图
 * 使用 Canvas 绘制折线图，兼容 API Level 14
 */
public class PriceChartView extends View {
    private List<KLineData> data;
    private List<Double> ma5;  // 5日均线数据
    private Paint linePaint;
    private Paint ma5Paint;    // 5日均线画笔
    private Paint textPaint;
    private Paint gridPaint;
    private Paint fillPaint;

    private float padding = 40f;
    private float textHeight = 20f;

    public PriceChartView(Context context) {
        super(context);
        initPaint();
    }

    public PriceChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint();
    }

    public PriceChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaint();
    }

    private void initPaint() {
        // 折线画笔
        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#0d6efd"));
        linePaint.setStrokeWidth(2f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        // 5日均线画笔（黄色）
        ma5Paint = new Paint();
        ma5Paint.setColor(Color.parseColor("#ffc107"));
        ma5Paint.setStrokeWidth(1.5f);
        ma5Paint.setStyle(Paint.Style.STROKE);
        ma5Paint.setAntiAlias(true);

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

        // 填充画笔（渐变效果）
        fillPaint = new Paint();
        fillPaint.setColor(Color.parseColor("#0d6efd"));
        fillPaint.setAlpha(30);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);
    }

    /**
     * 设置数据并计算5日均线
     * @param data K线数据列表
     */
    public void setData(List<KLineData> data) {
        this.data = data;
        calculateMA5();
        invalidate(); // 触发重绘
    }

    /**
     * 计算5日均线
     */
    private void calculateMA5() {
        if (data == null || data.size() < 5) {
            ma5 = new ArrayList<Double>();
            return;
        }

        ma5 = new ArrayList<Double>();
        
        // 前4天没有5日均线
        for (int i = 0; i < 4; i++) {
            ma5.add(null);
        }
        
        // 从第5天开始计算
        for (int i = 4; i < data.size(); i++) {
            double sum = 0;
            for (int j = i - 4; j <= i; j++) {
                sum += data.get(j).getClose();
            }
            ma5.add(sum / 5);
        }
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

        // 计算数据范围
        float minPrice = Float.MAX_VALUE;
        float maxPrice = Float.MIN_VALUE;
        for (KLineData k : data) {
            if ((float) k.getClose() < minPrice) minPrice = (float) k.getClose();
            if ((float) k.getClose() > maxPrice) maxPrice = (float) k.getClose();
        }

        // 防止价格范围为0
        if (maxPrice == minPrice) {
            maxPrice = minPrice + 1;
        }

        // 绘制网格线
        drawGrid(canvas, chartWidth, chartHeight);

        // 绘制折线和填充区域
        drawLineChart(canvas, chartWidth, chartHeight, minPrice, maxPrice);

        // 绘制5日均线
        drawMA5Line(canvas, chartWidth, chartHeight, minPrice, maxPrice);

        // 绘制坐标轴标签
        drawAxisLabels(canvas, chartWidth, chartHeight, minPrice, maxPrice);
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
        // 绘制水平网格线（3条）
        for (int i = 0; i <= 3; i++) {
            float y = padding + (chartHeight / 3) * i;
            canvas.drawLine(padding, y, padding + chartWidth, y, gridPaint);
        }

        // 绘制垂直网格线（5条）
        for (int i = 0; i <= 5; i++) {
            float x = padding + (chartWidth / 5) * i;
            canvas.drawLine(x, padding, x, padding + chartHeight, gridPaint);
        }
    }

    private void drawLineChart(Canvas canvas, float chartWidth, float chartHeight,
                               float minPrice, float maxPrice) {
        if (data.size() < 2) return;

        float xStep = chartWidth / (data.size() - 1);
        float yRange = maxPrice - minPrice;

        Path linePath = new Path();
        Path fillPath = new Path();

        for (int i = 0; i < data.size(); i++) {
            float x = padding + i * xStep;
            float y = padding + chartHeight - ((float) data.get(i).getClose() - minPrice) / yRange * chartHeight;

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, padding + chartHeight);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        // 完成填充路径
        fillPath.lineTo(padding + (data.size() - 1) * xStep, padding + chartHeight);
        fillPath.lineTo(padding, padding + chartHeight);
        fillPath.close();

        // 绘制填充区域
        canvas.drawPath(fillPath, fillPaint);

        // 绘制折线
        canvas.drawPath(linePath, linePaint);
    }

    /**
     * 绘制5日均线
     */
    private void drawMA5Line(Canvas canvas, float chartWidth, float chartHeight,
                             float minPrice, float maxPrice) {
        if (ma5 == null || ma5.size() < 5 || data.size() < 2) return;

        float xStep = chartWidth / (data.size() - 1);
        float yRange = maxPrice - minPrice;

        Path ma5Path = new Path();
        boolean pathStarted = false;

        for (int i = 4; i < data.size(); i++) {
            Double ma5Value = ma5.get(i);
            if (ma5Value == null) continue;

            float x = padding + i * xStep;
            float y = padding + chartHeight - (float) (ma5Value - minPrice) / yRange * chartHeight;

            if (!pathStarted) {
                ma5Path.moveTo(x, y);
                pathStarted = true;
            } else {
                ma5Path.lineTo(x, y);
            }
        }

        if (pathStarted) {
            canvas.drawPath(ma5Path, ma5Paint);
        }

        // 绘制MA5标签
        if (ma5.size() > 0 && ma5.get(ma5.size() - 1) != null) {
            double lastMa5 = ma5.get(ma5.size() - 1);
            textPaint.setColor(Color.parseColor("#ffc107"));
            textPaint.setTextAlign(Paint.Align.RIGHT);
            String ma5Str = String.format("MA5:%.2f", lastMa5);
            float lastY = padding + chartHeight - (float) (lastMa5 - minPrice) / yRange * chartHeight;
            canvas.drawText(ma5Str, padding + chartWidth + 3f, lastY, textPaint);
            // 恢复文字颜色
            textPaint.setColor(Color.parseColor("#6c757d"));
        }
    }

    private void drawAxisLabels(Canvas canvas, float chartWidth, float chartHeight,
                                float minPrice, float maxPrice) {
        textPaint.setTextAlign(Paint.Align.LEFT);

        // 绘制最高价标签（顶部）
        String maxPriceStr = String.format("%.2f", maxPrice);
        canvas.drawText(maxPriceStr, 5f, padding + 5f, textPaint);

        // 绘制最低价标签（底部）
        String minPriceStr = String.format("%.2f", minPrice);
        canvas.drawText(minPriceStr, 5f, padding + chartHeight - 5f, textPaint);

        // 绘制中间价格标签
        float midPrice = (maxPrice + minPrice) / 2;
        String midPriceStr = String.format("%.2f", midPrice);
        canvas.drawText(midPriceStr, 5f, padding + chartHeight / 2, textPaint);

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
}