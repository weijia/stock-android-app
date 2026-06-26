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

    private float paddingLeft = 55f;   // 左侧留空间给价格标签
    private float paddingRight = 55f;  // 右侧留空间给成交量标签
    private float paddingTop = 25f;
    private float paddingBottom = 25f;
    
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
     * 单Y轴刻度：同一Y坐标范围，左侧价格标签，右侧成交量标签
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

        // 绘制共享的网格线和Y轴标签
        drawSharedGridAndLabels(canvas, chartWidth, chartHeight, preClose, priceTop, priceBottom, maxVolume);

        // 绘制成交量柱（从底部向上，按比例映射到整个图表高度）
        drawVolumeBars(canvas, chartWidth, chartHeight, points, maxVolume, preClose);

        // 绘制价格线（按比例映射到整个图表高度）
        drawPriceLine(canvas, chartWidth, chartHeight, points, preClose, priceTop, priceBottom);
        
        // 绘制均价线
        drawAvgLine(canvas, chartWidth, chartHeight, points, preClose, priceTop, priceBottom);
        
        // 绘制当前价格线
        drawCurrentPriceLine(canvas, chartWidth, chartHeight, points, preClose, priceTop, priceBottom);
    }

    /**
     * 绘制共享的网格线和Y轴标签
     * 单Y轴刻度线，左侧价格标签，右侧成交量标签
     */
    private void drawSharedGridAndLabels(Canvas canvas, float chartWidth, float chartHeight,
                                         double preClose, double priceTop, double priceBottom, double maxVolume) {
        int width = getWidth();
        
        // 绘制水平网格线（5条线）
        for (int i = 0; i <= 4; i++) {
            float y = paddingTop + (chartHeight / 4) * i;
            canvas.drawLine(paddingLeft, y, paddingLeft + chartWidth, y, gridPaint);
        }

        // 昨收基准线（中间位置）
        float baseY = paddingTop + chartHeight / 2;
        canvas.drawLine(paddingLeft, baseY, paddingLeft + chartWidth, baseY, baseLinePaint);

        // 左侧Y轴：价格标签和对应的涨跌幅标签
        textPaint.setTextAlign(Paint.Align.LEFT);
        
        // 价格映射：顶部=priceTop, 中间=preClose, 底部=priceBottom
        double priceRange = priceTop - priceBottom;
        
        // 顶部：最高价及其涨跌幅（显示在同一位置）
        String topPrice = String.format("%.2f", priceTop);
        double topChangePct = (priceTop - preClose) / preClose * 100;
        String topChange = String.format("+%.2f%%", topChangePct);
        canvas.drawText(topPrice, 3f, paddingTop + 5f, textPaint);
        canvas.drawText(topChange, 3f, paddingTop + 18f, textPaint);  // 价格下方
        
        // 25%位置：显示该位置对应的涨跌幅
        double priceAt25 = priceTop - priceRange * 0.25;
        double changeAt25 = (priceAt25 - preClose) / preClose * 100;
        String change25Str = String.format("%.2f%%", changeAt25);
        canvas.drawText(change25Str, 3f, paddingTop + chartHeight * 0.25f, textPaint);
        
        // 中间：昨收价（基准）
        String midPrice = String.format("%.2f", preClose);
        canvas.drawText(midPrice, 3f, baseY + 5f, textPaint);
        
        // 75%位置：显示该位置对应的涨跌幅
        double priceAt75 = priceTop - priceRange * 0.75;
        double changeAt75 = (priceAt75 - preClose) / preClose * 100;
        String change75Str = String.format("%.2f%%", changeAt75);
        canvas.drawText(change75Str, 3f, paddingTop + chartHeight * 0.75f, textPaint);
        
        // 底部：最低价及其涨跌幅（显示在同一位置）
        String bottomPrice = String.format("%.2f", priceBottom);
        double bottomChangePct = (priceBottom - preClose) / preClose * 100;
        String bottomChange = String.format("%.2f%%", bottomChangePct);
        canvas.drawText(bottomPrice, 3f, paddingTop + chartHeight - 3f, textPaint);

        // 右侧Y轴：成交量标签
        volumeTextPaint.setTextAlign(Paint.Align.RIGHT);
        
        String maxVolStr = formatVolume(maxVolume);
        canvas.drawText(maxVolStr, width - 3f, paddingTop + 5f, volumeTextPaint);
        
        String halfVolStr = formatVolume(maxVolume / 2);
        canvas.drawText(halfVolStr, width - 3f, paddingTop + chartHeight * 0.25f, volumeTextPaint);
        
        canvas.drawText("0", width - 3f, paddingTop + chartHeight - 3f, volumeTextPaint);
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
        float volumeBottom = paddingTop + chartHeight;  // 成交量柱底部在图表底部
        
        float barWidth = chartWidth / TOTAL_MINUTES * 0.7f;

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            
            int minuteIndex = timeToMinuteIndex(p.getTime());
            if (minuteIndex < 0) continue;
            
            double currentPrice = p.getPrice();
            double prevPrice = (i > 0) ? points.get(i - 1).getPrice() : preClose;
            
            // 涨红跌绿
            if (currentPrice >= prevPrice) {
                volumePaint.setColor(Color.parseColor("#dc3545"));  // 红色
                volumePaint.setAlpha(100);
            } else {
                volumePaint.setColor(Color.parseColor("#20c997"));  // 绿色
                volumePaint.setAlpha(100);
            }

            float x = minuteIndexToX(minuteIndex, chartWidth);
            // 成交量柱高度：按比例映射到整个图表高度
            float barHeight = (float) (p.getVolume() / maxVolume * chartHeight);
            float top = volumeBottom - barHeight;

            canvas.drawRect(x - barWidth/2, top, x + barWidth/2, volumeBottom, volumePaint);
        }
    }

    private void drawPriceLine(Canvas canvas, float chartWidth, float chartHeight,
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
            // 价格Y坐标：按比例映射到整个图表高度
            float y = paddingTop + (float) ((priceTop - p.getPrice()) / priceRange * chartHeight);

            if (!pathStarted) {
                pricePath.moveTo(x, y);
                fillPath.moveTo(x, paddingTop + chartHeight / 2);
                fillPath.lineTo(x, y);
                pathStarted = true;
            } else {
                pricePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        if (!pathStarted) return;

        // 完成填充路径（到昨收价位置）
        fillPath.lineTo(paddingLeft + chartWidth, paddingTop + chartHeight / 2);
        fillPath.lineTo(paddingLeft, paddingTop + chartHeight / 2);
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

        Path avgPath = new Path();
        boolean pathStarted = false;

        for (int i = 0; i < points.size(); i++) {
            IntradayPoint p = points.get(i);
            
            int minuteIndex = timeToMinuteIndex(p.getTime());
            if (minuteIndex < 0) continue;
            
            float x = minuteIndexToX(minuteIndex, chartWidth);
            float y = paddingTop + (float) ((priceTop - p.getAvgPrice()) / priceRange * chartHeight);

            if (!pathStarted) {
                avgPath.moveTo(x, y);
                pathStarted = true;
            } else {
                avgPath.lineTo(x, y);
            }
        }

        if (pathStarted) {
            canvas.drawPath(avgPath, avgPaint);
            
            // 绘制均价线的当前值标签
            IntradayPoint lastPoint = points.get(points.size() - 1);
            double avgPrice = lastPoint.getAvgPrice();
            if (avgPrice > 0) {
                float avgY = paddingTop + (float) ((priceTop - avgPrice) / priceRange * chartHeight);
                
                // 均价线标签（使用均价画笔的颜色）
                Paint avgLabelPaint = new Paint();
                avgLabelPaint.setColor(Color.parseColor("#ff9800"));
                avgLabelPaint.setTextSize(12f);
                avgLabelPaint.setAntiAlias(true);
                avgLabelPaint.setTextAlign(Paint.Align.LEFT);
                
                String avgText = "均" + String.format("%.2f", avgPrice);
                canvas.drawText(avgText, paddingLeft + chartWidth + 3f, avgY + 5f, avgLabelPaint);
            }
        }
    }

    private void drawCurrentPriceLine(Canvas canvas, float chartWidth, float chartHeight,
                                      List<IntradayPoint> points, double preClose, double priceTop, double priceBottom) {
        if (points.isEmpty()) return;

        IntradayPoint lastPoint = points.get(points.size() - 1);
        double currentPrice = lastPoint.getPrice();
        double priceRange = priceTop - priceBottom;

        float currentY = paddingTop + (float) ((priceTop - currentPrice) / priceRange * chartHeight);

        // 当前价格水平线
        canvas.drawLine(paddingLeft, currentY, paddingLeft + chartWidth, currentY, currentPricePaint);

        // 当前价格标签（右侧）
        String priceText = String.format("%.2f", currentPrice);
        currentPriceTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(priceText, paddingLeft + chartWidth + 3f, currentY + 5f, currentPriceTextPaint);
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