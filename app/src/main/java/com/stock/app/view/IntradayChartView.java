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
 * 价格线和成交量柱在同一个坐标系中显示
 * - X轴：时间轴（共用）
 * - 左Y轴：价格
 * - 右Y轴：成交量（柱状图）
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
    private Paint currentPricePaint;
    private Paint currentPriceTextPaint;
    private Paint volumePaint;
    private Paint volumeTextPaint;

    private float paddingLeft = 50f;   // 左侧留空间给价格标签
    private float paddingRight = 50f;  // 右侧留空间给成交量标签
    private float paddingTop = 30f;
    private float paddingBottom = 25f;
    
    // 成交量柱高度比例（占图表高度的20%）
    private float volumeHeightRatio = 0.20f;
    
    // 固定时间范围（分钟数）
    private static final int MORNING_MINUTES = 120;
    private static final int AFTERNOON_MINUTES = 120;
    private static final int TOTAL_MINUTES = MORNING_MINUTES + AFTERNOON_MINUTES;

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
        pricePaint = new Paint();
        pricePaint.setColor(Color.parseColor("#0d6efd"));
        pricePaint.setStrokeWidth(2f);
        pricePaint.setStyle(Paint.Style.STROKE);
        pricePaint.setAntiAlias(true);

        avgPaint = new Paint();
        avgPaint.setColor(Color.parseColor("#ff9800"));
        avgPaint.setStrokeWidth(1.5f);
        avgPaint.setStyle(Paint.Style.STROKE);
        avgPaint.setAntiAlias(true);

        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#dee2e6"));
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#6c757d"));
        textPaint.setTextSize(14f);
        textPaint.setAntiAlias(true);

        upPaint = new Paint();
        upPaint.setColor(Color.parseColor("#20c997"));
        upPaint.setAlpha(30);
        upPaint.setStyle(Paint.Style.FILL);
        upPaint.setAntiAlias(true);

        downPaint = new Paint();
        downPaint.setColor(Color.parseColor("#dc3545"));
        downPaint.setAlpha(30);
        downPaint.setStyle(Paint.Style.FILL);
        downPaint.setAntiAlias(true);

        baseLinePaint = new Paint();
        baseLinePaint.setColor(Color.parseColor("#6c757d"));
        baseLinePaint.setStrokeWidth(1f);
        baseLinePaint.setStyle(Paint.Style.STROKE);
        baseLinePaint.setAntiAlias(true);

        currentPricePaint = new Paint();
        currentPricePaint.setColor(Color.parseColor("#dc3545"));
        currentPricePaint.setStrokeWidth(1.5f);
        currentPricePaint.setStyle(Paint.Style.STROKE);
        currentPricePaint.setAntiAlias(true);

        currentPriceTextPaint = new Paint();
        currentPriceTextPaint.setColor(Color.parseColor("#dc3545"));
        currentPriceTextPaint.setTextSize(14f);
        currentPriceTextPaint.setAntiAlias(true);

        volumePaint = new Paint();
        volumePaint.setStyle(Paint.Style.FILL);
        volumePaint.setAntiAlias(true);

        volumeTextPaint = new Paint();
        volumeTextPaint.setColor(Color.parseColor("#6c757d"));
        volumeTextPaint.setTextSize(12f);
        volumeTextPaint.setAntiAlias(true);
        volumeTextPaint.setTextAlign(Paint.Align.RIGHT);
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

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;

        // 在同一个坐标系中绘制价格和成交量
        drawCombinedChart(canvas, chartWidth, chartHeight);
        drawTimeLabels(canvas, chartWidth);
    }

    private void drawEmptyState(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        Paint emptyPaint = new Paint();
        emptyPaint.setColor(Color.parseColor("#adb5bd"));
        emptyPaint.setTextSize(16f);
        emptyPaint.setAntiAlias(true);
        emptyPaint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText("暂无分时数据", width / 2f, height / 2f, emptyPaint);
    }

    private int timeToMinuteIndex(String time) {
        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            
            if (hour == 9) return minute - 30;
            else if (hour == 10) return 30 + minute;
            else if (hour == 11 && minute <= 30) return 90 + minute;
            else if (hour == 13) return MORNING_MINUTES + minute;
            else if (hour == 14) return MORNING_MINUTES + 60 + minute;
            else if (hour == 15 && minute == 0) return TOTAL_MINUTES;
            
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private float minuteIndexToX(int minuteIndex, float chartWidth) {
        return paddingLeft + (minuteIndex / (float) TOTAL_MINUTES) * chartWidth;
    }

    /**
     * 在同一个坐标系中绘制价格线和成交量柱
     */
    private void drawCombinedChart(Canvas canvas, float chartWidth, float chartHeight) {
        List<IntradayPoint> points = data.getData();
        double preClose = data.getPreClose();
        double maxPrice = data.getMaxPrice();
        double minPrice = data.getMinPrice();
        double maxVolume = data.getMaxVolume();

        if (maxVolume == 0) maxVolume = 1;

        // 计算价格范围（以昨收价为中心，上下对称）
        double maxDiff = Math.max(maxPrice - preClose, preClose - minPrice);
        double priceTop = preClose + maxDiff;
        double priceBottom = preClose - maxDiff;

        if (maxDiff == 0) {
            priceTop = preClose + 0.1;
            priceBottom = preClose - 0.1;
        }

        double priceRange = priceTop - priceBottom;

        // 绘制网格线和价格标签（左侧Y轴）
        drawPriceGridAndLabels(canvas, chartWidth, chartHeight, preClose, priceTop, priceBottom);

        // 绘制成交量标签（右侧Y轴）
        drawVolumeLabels(canvas, chartHeight, maxVolume);

        // 绘制成交量柱（在底部，使用同一X轴）
        drawVolumeBars(canvas, chartWidth, chartHeight, points, maxVolume, preClose);

        // 绘制价格线（在上部，使用同一X轴）
        drawPriceLine(canvas, chartWidth, chartHeight, points, preClose, priceTop, priceBottom);
        
        // 绘制均价线
        drawAvgLine(canvas, chartWidth, chartHeight, points, preClose, priceTop, priceBottom);
        
        // 绘制当前价格线
        drawCurrentPriceLine(canvas, chartWidth, chartHeight, points, preClose, priceTop, priceBottom);
    }

    private void drawPriceGridAndLabels(Canvas canvas, float chartWidth, float chartHeight,
                                        double preClose, double priceTop, double priceBottom) {
        // 绘制水平网格线
        for (int i = 0; i <= 4; i++) {
            float y = paddingTop + (chartHeight / 4) * i;
            canvas.drawLine(paddingLeft, y, paddingLeft + chartWidth, y, gridPaint);
        }

        // 左侧Y轴：价格标签
        textPaint.setTextAlign(Paint.Align.LEFT);
        
        String topPrice = String.format("%.2f", priceTop);
        canvas.drawText(topPrice, 5f, paddingTop + textPaint.getTextSize(), textPaint);
        
        String midPrice = String.format("%.2f", preClose);
        canvas.drawText(midPrice, 5f, paddingTop + chartHeight / 2 + textPaint.getTextSize()/2, textPaint);
        
        String bottomPrice = String.format("%.2f", priceBottom);
        canvas.drawText(bottomPrice, 5f, paddingTop + chartHeight - 5f, textPaint);

        // 涨跌幅标签（左侧）
        double maxChangePct = (priceTop - preClose) / preClose * 100;
        canvas.drawText(String.format("+%.2f%%", maxChangePct), 5f, paddingTop + chartHeight * 0.25f, textPaint);
        canvas.drawText(String.format("%.2f%%", -maxChangePct), 5f, paddingTop + chartHeight * 0.75f, textPaint);

        // 昨收基准线
        float baseY = paddingTop + chartHeight / 2;
        canvas.drawLine(paddingLeft, baseY, paddingLeft + chartWidth, baseY, baseLinePaint);
    }

    private void drawVolumeLabels(Canvas canvas, float chartHeight, double maxVolume) {
        // 右侧Y轴：成交量标签
        volumeTextPaint.setTextAlign(Paint.Align.RIGHT);
        int width = getWidth();
        
        // 成交量最大值
        String maxVolStr = formatVolume(maxVolume);
        canvas.drawText(maxVolStr, width - 5f, paddingTop + volumeTextPaint.getTextSize(), volumeTextPaint);
        
        // 成交量一半
        String halfVolStr = formatVolume(maxVolume / 2);
        canvas.drawText(halfVolStr, width - 5f, paddingTop + chartHeight * (1 - volumeHeightRatio/2), volumeTextPaint);
    }

    private String formatVolume(double volume) {
        if (volume >= 10000) {
            return String.format("%.0f万", volume / 10000);
        } else {
            return String.format("%.0f", volume);
        }
    }

    private void drawVolumeBars(Canvas canvas, float chartWidth, float chartHeight,
                                List<IntradayPoint> points, double maxVolume, double preClose) {
        float volumeHeight = chartHeight * volumeHeightRatio;
        float volumeBottom = paddingTop + chartHeight;  // 成交量柱底部在图表底部
        
        float barWidth = chartWidth / TOTAL_MINUTES * 0.8f;

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            
            int minuteIndex = timeToMinuteIndex(p.getTime());
            if (minuteIndex < 0) continue;
            
            double currentPrice = p.getPrice();
            double prevPrice = (i > 0) ? points.get(i - 1).getPrice() : preClose;
            
            // 涨红跌绿
            if (currentPrice >= prevPrice) {
                volumePaint.setColor(Color.parseColor("#dc3545"));  // 红色
                volumePaint.setAlpha(120);
            } else {
                volumePaint.setColor(Color.parseColor("#20c997"));  // 绿色
                volumePaint.setAlpha(120);
            }

            float x = minuteIndexToX(minuteIndex, chartWidth);
            float barHeight = (float) (p.getVolume() / maxVolume * volumeHeight);
            float top = volumeBottom - barHeight;

            canvas.drawRect(x - barWidth/2, top, x + barWidth/2, volumeBottom, volumePaint);
        }
    }

    private void drawPriceLine(Canvas canvas, float chartWidth, float chartHeight,
                               List<IntradayPoint> points, double preClose, double priceTop, double priceBottom) {
        if (points.isEmpty()) return;

        double priceRange = priceTop - priceBottom;
        // 价格区域高度（扣除成交量区域）
        float priceHeight = chartHeight * (1 - volumeHeightRatio);

        Path pricePath = new Path();
        Path fillPath = new Path();
        boolean pathStarted = false;

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            
            int minuteIndex = timeToMinuteIndex(p.getTime());
            if (minuteIndex < 0) continue;
            
            float x = minuteIndexToX(minuteIndex, chartWidth);
            // 价格Y坐标：基于价格区域计算
            float y = paddingTop + (float) ((priceTop - p.getPrice()) / priceRange * priceHeight);

            if (!pathStarted) {
                pricePath.moveTo(x, y);
                fillPath.moveTo(x, paddingTop + priceHeight / 2);
                fillPath.lineTo(x, y);
                pathStarted = true;
            } else {
                pricePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        if (!pathStarted) return;

        // 完成填充路径（到昨收价位置）
        fillPath.lineTo(paddingLeft + chartWidth, paddingTop + priceHeight / 2);
        fillPath.lineTo(paddingLeft, paddingTop + priceHeight / 2);
        fillPath.close();

        double lastPrice = points.get(points.size() - 1).getPrice();
        Paint fillPaint = lastPrice >= preClose ? downPaint : upPaint;

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(pricePath, pricePaint);
    }

    private void drawAvgLine(Canvas canvas, float chartWidth, float chartHeight,
                             List<IntradayPoint> points, double preClose, double priceTop, double priceBottom) {
        if (points.isEmpty()) return;

        double priceRange = priceTop - priceBottom;
        float priceHeight = chartHeight * (1 - volumeHeightRatio);

        Path avgPath = new Path();
        boolean pathStarted = false;

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            
            int minuteIndex = timeToMinuteIndex(p.getTime());
            if (minuteIndex < 0) continue;
            
            float x = minuteIndexToX(minuteIndex, chartWidth);
            float y = paddingTop + (float) ((priceTop - p.getAvgPrice()) / priceRange * priceHeight);

            if (!pathStarted) {
                avgPath.moveTo(x, y);
                pathStarted = true;
            } else {
                avgPath.lineTo(x, y);
            }
        }

        if (pathStarted) {
            canvas.drawPath(avgPath, avgPaint);
        }
    }

    private void drawCurrentPriceLine(Canvas canvas, float chartWidth, float chartHeight,
                                      List<IntradayPoint> points, double preClose, double priceTop, double priceBottom) {
        if (points.isEmpty()) return;

        IntradayPoint lastPoint = points.get(points.size() - 1);
        double currentPrice = lastPoint.getPrice();
        double priceRange = priceTop - priceBottom;
        float priceHeight = chartHeight * (1 - volumeHeightRatio);

        float currentY = paddingTop + (float) ((priceTop - currentPrice) / priceRange * priceHeight);

        // 当前价格水平线（只画价格区域）
        canvas.drawLine(paddingLeft, currentY, paddingLeft + chartWidth, currentY, currentPricePaint);

        // 当前价格标签（右侧）
        String priceText = String.format("%.2f", currentPrice);
        currentPriceTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(priceText, paddingLeft + chartWidth + 5f, currentY + currentPriceTextPaint.getTextSize()/2, currentPriceTextPaint);
    }

    private void drawTimeLabels(Canvas canvas, float chartWidth) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.parseColor("#6c757d"));

        float labelY = getHeight() - 8f;
        
        canvas.drawText("09:30", paddingLeft, labelY, textPaint);
        canvas.drawText("11:30/13:00", minuteIndexToX(MORNING_MINUTES, chartWidth), labelY, textPaint);
        canvas.drawText("15:00", paddingLeft + chartWidth, labelY, textPaint);
    }
}