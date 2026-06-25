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
 * 
 * 成交量叠加在价格图底部
 * 时间轴固定：上午 9:30-11:30 (120分钟)，下午 13:00-15:00 (120分钟)
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

    private float padding = 40f;
    private float textHeight = 20f;
    private float volumeHeightRatio = 0.25f;  // 成交量占底部25%
    
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
        textPaint.setTextSize(16f);
        textPaint.setAntiAlias(true);

        upPaint = new Paint();
        upPaint.setColor(Color.parseColor("#20c997"));  // 绿色
        upPaint.setAlpha(30);
        upPaint.setStyle(Paint.Style.FILL);
        upPaint.setAntiAlias(true);

        downPaint = new Paint();
        downPaint.setColor(Color.parseColor("#dc3545"));  // 红色
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
        currentPriceTextPaint.setTextSize(16f);
        currentPriceTextPaint.setAntiAlias(true);
        currentPriceTextPaint.setTextAlign(Paint.Align.RIGHT);

        volumePaint = new Paint();
        volumePaint.setStyle(Paint.Style.FILL);
        volumePaint.setAntiAlias(true);
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
        float chartHeight = height - padding - textHeight;
        float volumeHeight = chartHeight * volumeHeightRatio;
        float priceHeight = chartHeight - volumeHeight;

        // 绘制价格图（上部）
        drawPriceChart(canvas, chartWidth, priceHeight);
        
        // 绘制成交量（叠加在底部）
        drawVolumeChart(canvas, chartWidth, priceHeight, volumeHeight);
        
        // 绘制时间标签
        drawTimeLabels(canvas, chartWidth, height);
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
        return padding + (minuteIndex / (float) TOTAL_MINUTES) * chartWidth;
    }

    private void drawPriceChart(Canvas canvas, float chartWidth, float priceHeight) {
        List<IntradayPoint> points = data.getData();
        double preClose = data.getPreClose();
        double maxPrice = data.getMaxPrice();
        double minPrice = data.getMinPrice();

        double maxDiff = Math.max(maxPrice - preClose, preClose - minPrice);
        double priceTop = preClose + maxDiff;
        double priceBottom = preClose - maxDiff;

        if (maxDiff == 0) {
            priceTop = preClose + 0.1;
            priceBottom = preClose - 0.1;
        }

        drawPriceGrid(canvas, chartWidth, priceHeight, preClose, priceTop, priceBottom);

        float baseY = padding + priceHeight / 2;
        canvas.drawLine(padding, baseY, padding + chartWidth, baseY, baseLinePaint);

        drawPriceLine(canvas, chartWidth, priceHeight, points, preClose, priceTop, priceBottom);
        drawAvgLine(canvas, chartWidth, priceHeight, points, preClose, priceTop, priceBottom);
        drawCurrentPriceLine(canvas, chartWidth, priceHeight, points, preClose, priceTop, priceBottom);
    }

    private void drawPriceGrid(Canvas canvas, float chartWidth, float priceHeight,
                               double preClose, double priceTop, double priceBottom) {
        // 绘制水平网格线
        for (int i = 0; i <= 3; i++) {
            float y = padding + (priceHeight / 3) * i;
            canvas.drawLine(padding, y, padding + chartWidth, y, gridPaint);
        }

        textPaint.setTextAlign(Paint.Align.LEFT);
        
        String topPrice = String.format("%.2f", priceTop);
        canvas.drawText(topPrice, 5f, padding + 5f + textPaint.getTextSize()/2, textPaint);
        
        String midPrice = String.format("%.2f", preClose);
        canvas.drawText(midPrice, 5f, padding + priceHeight / 2 + textPaint.getTextSize()/2, textPaint);
        
        String bottomPrice = String.format("%.2f", priceBottom);
        canvas.drawText(bottomPrice, 5f, padding + priceHeight - 5f + textPaint.getTextSize()/2, textPaint);

        double maxChangePct = (priceTop - preClose) / preClose * 100;
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format("+%.2f%%", maxChangePct), getWidth() - 5f, padding + 5f + textPaint.getTextSize()/2, textPaint);
        canvas.drawText(String.format("%.2f%%", -maxChangePct), getWidth() - 5f, padding + priceHeight - 5f + textPaint.getTextSize()/2, textPaint);
    }

    private void drawPriceLine(Canvas canvas, float chartWidth, float priceHeight,
                               List<IntradayPoint> points, double preClose, double priceTop, double priceBottom) {
        if (points.isEmpty()) return;

        double priceRange = priceTop - priceBottom;

        Path pricePath = new Path();
        Path fillPath = new Path();
        boolean pathStarted = false;

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            
            int minuteIndex = timeToMinuteIndex(p.getTime());
            if (minuteIndex < 0) continue;
            
            float x = minuteIndexToX(minuteIndex, chartWidth);
            float y = padding + (float) ((priceTop - p.getPrice()) / priceRange * priceHeight);

            if (!pathStarted) {
                pricePath.moveTo(x, y);
                fillPath.moveTo(x, padding + priceHeight / 2);
                fillPath.lineTo(x, y);
                pathStarted = true;
            } else {
                pricePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        if (!pathStarted) return;

        fillPath.lineTo(padding + chartWidth, padding + priceHeight / 2);
        fillPath.lineTo(padding, padding + priceHeight / 2);
        fillPath.close();

        double lastPrice = points.get(points.size() - 1).getPrice();
        Paint fillPaint = lastPrice >= preClose ? downPaint : upPaint;

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(pricePath, pricePaint);
    }

    private void drawAvgLine(Canvas canvas, float chartWidth, float priceHeight,
                             List<IntradayPoint> points, double preClose, double priceTop, double priceBottom) {
        if (points.isEmpty()) return;

        double priceRange = priceTop - priceBottom;

        Path avgPath = new Path();
        boolean pathStarted = false;

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            
            int minuteIndex = timeToMinuteIndex(p.getTime());
            if (minuteIndex < 0) continue;
            
            float x = minuteIndexToX(minuteIndex, chartWidth);
            float y = padding + (float) ((priceTop - p.getAvgPrice()) / priceRange * priceHeight);

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

    private void drawCurrentPriceLine(Canvas canvas, float chartWidth, float priceHeight,
                                      List<IntradayPoint> points, double preClose, double priceTop, double priceBottom) {
        if (points.isEmpty()) return;

        IntradayPoint lastPoint = points.get(points.size() - 1);
        double currentPrice = lastPoint.getPrice();
        double priceRange = priceTop - priceBottom;

        float currentY = padding + (float) ((priceTop - currentPrice) / priceRange * priceHeight);

        canvas.drawLine(padding, currentY, padding + chartWidth, currentY, currentPricePaint);

        String priceText = String.format("%.2f", currentPrice);
        canvas.drawText(priceText, getWidth() - 5f, currentY + currentPriceTextPaint.getTextSize()/2, currentPriceTextPaint);
    }

    private void drawVolumeChart(Canvas canvas, float chartWidth, float priceHeight, float volumeHeight) {
        List<IntradayPoint> points = data.getData();
        double maxVolume = data.getMaxVolume();

        if (maxVolume == 0) maxVolume = 1;

        float volumeTop = padding + priceHeight;
        float volumeBottom = volumeTop + volumeHeight;

        double preClose = data.getPreClose();
        
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
                volumePaint.setAlpha(150);
            } else {
                volumePaint.setColor(Color.parseColor("#20c997"));  // 绿色
                volumePaint.setAlpha(150);
            }

            float x = minuteIndexToX(minuteIndex, chartWidth);
            float barHeight = (float) (p.getVolume() / maxVolume * volumeHeight);
            float top = volumeBottom - barHeight;

            canvas.drawRect(x - barWidth/2, top, x + barWidth/2, volumeBottom, volumePaint);
        }
    }

    private void drawTimeLabels(Canvas canvas, float chartWidth, float height) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.parseColor("#6c757d"));

        float labelY = height - 5f;
        
        canvas.drawText("09:30", padding, labelY, textPaint);
        canvas.drawText("11:30/13:00", minuteIndexToX(MORNING_MINUTES, chartWidth), labelY, textPaint);
        canvas.drawText("15:00", padding + chartWidth, labelY, textPaint);
    }
}