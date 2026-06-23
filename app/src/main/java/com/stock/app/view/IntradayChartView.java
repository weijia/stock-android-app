package com.stock.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.stock.app.model.IntradayData;
import com.stock.app.model.IntradayPoint;

import java.util.List;

/**
 * 分时图自定义视图
 * 显示当天或上一交易日的分时走势
 */
public class IntradayChartView extends View {
    private IntradayData data;
    private Paint pricePaint;
    private Paint avgPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint upPaint;
    private Paint downPaint;
    private Paint baseLinePaint;

    private float padding = 40f;
    private float textHeight = 20f;
    private float chartHeightRatio = 0.7f;  // 价格图占70%，成交量图占30%

    public IntradayChartView(Context context) {
        super(context);
        initPaint();
    }

    public IntradayChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint();
    }

    public IntradayChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaint();
    }

    private void initPaint() {
        // 价格线画笔
        pricePaint = new Paint();
        pricePaint.setColor(Color.parseColor("#0d6efd"));
        pricePaint.setStrokeWidth(2f);
        pricePaint.setStyle(Paint.Style.STROKE);
        pricePaint.setAntiAlias(true);

        // 均价线画笔
        avgPaint = new Paint();
        avgPaint.setColor(Color.parseColor("#ff9800"));
        avgPaint.setStrokeWidth(1.5f);
        avgPaint.setStyle(Paint.Style.STROKE);
        avgPaint.setAntiAlias(true);

        // 网格画笔
        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#dee2e6"));
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);

        // 文字画笔（恢复原来大小）
        textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#6c757d"));
        textPaint.setTextSize(12f);  // 恢复原来的 12f
        textPaint.setAntiAlias(true);

        // 上涨区域填充
        upPaint = new Paint();
        upPaint.setColor(Color.parseColor("#20c997"));
        upPaint.setAlpha(30);
        upPaint.setStyle(Paint.Style.FILL);
        upPaint.setAntiAlias(true);

        // 下跌区域填充
        downPaint = new Paint();
        downPaint.setColor(Color.parseColor("#dc3545"));
        downPaint.setAlpha(30);
        downPaint.setStyle(Paint.Style.FILL);
        downPaint.setAntiAlias(true);

        // 昨收基准线
        baseLinePaint = new Paint();
        baseLinePaint.setColor(Color.parseColor("#6c757d"));
        baseLinePaint.setStrokeWidth(1f);
        baseLinePaint.setStyle(Paint.Style.STROKE);
        baseLinePaint.setAntiAlias(true);
    }

    public void setData(IntradayData data) {
        this.data = data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (data == null || data.getData() == null || data.getData().isEmpty()) {
            drawEmptyState(canvas);
            return;
        }

        int width = getWidth();
        int height = getHeight();

        float chartWidth = width - padding * 2;
        float priceChartHeight = (height - padding - textHeight) * chartHeightRatio;
        float volumeChartHeight = (height - padding - textHeight) * (1 - chartHeightRatio);

        // 绘制价格区域
        drawPriceChart(canvas, chartWidth, priceChartHeight);

        // 绘制成交量区域
        drawVolumeChart(canvas, chartWidth, priceChartHeight, volumeChartHeight);

        // 绘制时间轴标签
        drawTimeLabels(canvas, chartWidth, height);
    }

    private void drawEmptyState(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        Paint emptyPaint = new Paint();
        emptyPaint.setColor(Color.parseColor("#adb5bd"));
        emptyPaint.setTextSize(14f);  // 恢复原来的大小
        emptyPaint.setAntiAlias(true);
        emptyPaint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText("暂无分时数据", width / 2f, height / 2f, emptyPaint);
    }

    private void drawPriceChart(Canvas canvas, float chartWidth, float priceChartHeight) {
        List<IntradayPoint> points = data.getData();
        double preClose = data.getPreClose();
        double maxPrice = data.getMaxPrice();
        double minPrice = data.getMinPrice();

        // 计算价格范围（以昨收价为中心，上下对称）
        double maxDiff = Math.max(maxPrice - preClose, preClose - minPrice);
        double priceTop = preClose + maxDiff;
        double priceBottom = preClose - maxDiff;

        if (maxDiff == 0) {
            priceTop = preClose + 0.1;
            priceBottom = preClose - 0.1;
        }

        // 绘制网格
        drawPriceGrid(canvas, chartWidth, priceChartHeight, preClose, priceTop, priceBottom);

        // 绘制昨收基准线
        float baseY = padding + priceChartHeight / 2;
        canvas.drawLine(padding, baseY, padding + chartWidth, baseY, baseLinePaint);

        // 绘制价格曲线
        drawPriceLine(canvas, chartWidth, priceChartHeight, points, preClose, priceTop, priceBottom);

        // 绘制均价线
        drawAvgLine(canvas, chartWidth, priceChartHeight, points, preClose, priceTop, priceBottom);
    }

    private void drawPriceGrid(Canvas canvas, float chartWidth, float priceChartHeight,
                               double preClose, double priceTop, double priceBottom) {
        // 绘制水平网格线（3条）
        for (int i = 0; i <= 3; i++) {
            float y = padding + (priceChartHeight / 3) * i;
            canvas.drawLine(padding, y, padding + chartWidth, y, gridPaint);
        }

        // 绘制价格标签
        textPaint.setTextAlign(Paint.Align.LEFT);
        
        // 最高价
        String topPrice = String.format("%.2f", priceTop);
        canvas.drawText(topPrice, 5f, padding + 5f, textPaint);
        
        // 昨收价（中间）
        String midPrice = String.format("%.2f", preClose);
        canvas.drawText(midPrice, 5f, padding + priceChartHeight / 2, textPaint);
        
        // 最低价
        String bottomPrice = String.format("%.2f", priceBottom);
        canvas.drawText(bottomPrice, 5f, padding + priceChartHeight - 5f, textPaint);

        // 涨跌幅标签
        double maxChangePct = (priceTop - preClose) / preClose * 100;
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format("+%.2f%%", maxChangePct), getWidth() - 5f, padding + 5f, textPaint);
        canvas.drawText(String.format("%.2f%%", -maxChangePct), getWidth() - 5f, padding + priceChartHeight - 5f, textPaint);
    }

    private void drawPriceLine(Canvas canvas, float chartWidth, float priceChartHeight,
                               List<IntradayPoint> points, double preClose, double priceTop, double priceBottom) {
        if (points.isEmpty()) return;

        float xStep = chartWidth / (points.size() - 1);
        double priceRange = priceTop - priceBottom;

        Path pricePath = new Path();
        Path fillPath = new Path();

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            float x = padding + i * xStep;
            float y = padding + (float) ((priceTop - p.getPrice()) / priceRange * priceChartHeight);

            if (i == 0) {
                pricePath.moveTo(x, y);
                fillPath.moveTo(x, padding + priceChartHeight / 2);  // 从昨收价开始
                fillPath.lineTo(x, y);
            } else {
                pricePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        // 完成填充路径（回到昨收价）
        fillPath.lineTo(padding + chartWidth, padding + priceChartHeight / 2);
        fillPath.lineTo(padding, padding + priceChartHeight / 2);
        fillPath.close();

        // 根据涨跌选择填充颜色
        double lastPrice = points.get(points.size() - 1).getPrice();
        Paint fillPaint = lastPrice >= preClose ? upPaint : downPaint;

        // 绘制填充区域
        canvas.drawPath(fillPath, fillPaint);

        // 绘制价格曲线
        canvas.drawPath(pricePath, pricePaint);
    }

    private void drawAvgLine(Canvas canvas, float chartWidth, float priceChartHeight,
                             List<IntradayPoint> points, double preClose, double priceTop, double priceBottom) {
        if (points.isEmpty()) return;

        float xStep = chartWidth / (points.size() - 1);
        double priceRange = priceTop - priceBottom;

        Path avgPath = new Path();

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            float x = padding + i * xStep;
            float y = padding + (float) ((priceTop - p.getAvgPrice()) / priceRange * priceChartHeight);

            if (i == 0) {
                avgPath.moveTo(x, y);
            } else {
                avgPath.lineTo(x, y);
            }
        }

        canvas.drawPath(avgPath, avgPaint);
    }

    private void drawVolumeChart(Canvas canvas, float chartWidth, float priceChartHeight,
                                  float volumeChartHeight) {
        List<IntradayPoint> points = data.getData();
        double maxVolume = data.getMaxVolume();

        if (maxVolume == 0) maxVolume = 1;

        float volumeTop = padding + priceChartHeight + 10f;
        float volumeBottom = volumeTop + volumeChartHeight;

        // 绘制成交量网格
        canvas.drawLine(padding, volumeTop, padding + chartWidth, volumeTop, gridPaint);
        canvas.drawLine(padding, volumeBottom, padding + chartWidth, volumeBottom, gridPaint);

        // 绘制成交量柱状图
        float barWidth = chartWidth / points.size() * 0.8f;
        float gap = chartWidth / points.size() * 0.2f;

        double preClose = data.getPreClose();

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            
            // 根据价格涨跌选择颜色
            Paint barPaint;
            if (p.getPrice() >= preClose) {
                barPaint = new Paint(upPaint);
                barPaint.setAlpha(180);
            } else {
                barPaint = new Paint(downPaint);
                barPaint.setAlpha(180);
            }

            float left = padding + i * (barWidth + gap);
            float barHeight = (float) (p.getVolume() / maxVolume * volumeChartHeight);
            float top = volumeBottom - barHeight;
            float right = left + barWidth;

            canvas.drawRect(left, top, right, volumeBottom, barPaint);
        }
    }

    private void drawTimeLabels(Canvas canvas, float chartWidth, float height) {
        List<IntradayPoint> points = data.getData();

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.parseColor("#6c757d"));

        // 显示关键时间点：开盘、午休开始、午休结束、收盘
        String[] keyTimes = {"09:30", "11:30", "13:00", "15:00"};
        
        // 在底部显示时间标签
        float labelY = height - 5f;
        
        // 开盘时间（第一个点）
        if (points.size() > 0) {
            canvas.drawText("09:30", padding, labelY, textPaint);
        }
        
        // 收盘时间（最后一个点）
        if (points.size() > 1) {
            canvas.drawText("15:00", padding + chartWidth, labelY, textPaint);
        }
        
        // 中间时间点
        if (points.size() > 120) {
            canvas.drawText("11:30", padding + chartWidth * 0.33f, labelY, textPaint);
            canvas.drawText("13:00", padding + chartWidth * 0.5f, labelY, textPaint);
        }
    }
}