package georgii.sytnik.thothtasks.ui.schedule;

import georgii.sytnik.thothtasks.R;

import java.util.Locale;

import android.content.Context;
import java.util.Locale;

import android.graphics.Canvas;
import java.util.Locale;

import android.graphics.Paint;
import java.util.Locale;

import android.graphics.RectF;
import java.util.Locale;

import android.util.AttributeSet;
import java.util.Locale;

import android.view.View;

import java.util.Locale;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SplitDayTimelineView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockMutedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockTravelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();
    private final List<DayBlock> blocks = new ArrayList<>();

    // geometry in dp (converted in draw)
    private float gutterDp = 40f;      // hour label gutter (left and middle)
    private float midGapDp = 6f;       // gap between columns
    private float paddingDp = 6f;
    private float radiusDp = 10f;

    public SplitDayTimelineView(Context context) {
        super(context);
        init();
    }

    public SplitDayTimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SplitDayTimelineView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setColor(0xFFCCCCCC);
        gridPaint.setStrokeWidth(dp(1));

        labelPaint.setColor(0xFF555555);
        labelPaint.setTextSize(dp(12));

        blockPaint.setColor(0xFF3F51B5);
        blockMutedPaint.setColor(0xFF9E9E9E);

        blockTextPaint.setColor(0xFFFFFFFF);
        blockTextPaint.setTextSize(dp(12));

        blockTravelPaint.setColor(0xFF2E7D32);
    }

    /** Blocks are in full-day minutes [0..1440]. */
    public void setBlocks(List<DayBlock> newBlocks) {
        blocks.clear();
        if (newBlocks != null) blocks.addAll(newBlocks);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // IMPORTANT:
        // We do NOT force a fixed height here.
        // Parent (ConstraintLayout) gives us the exact height available.
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        float top = getPaddingTop();
        float left = getPaddingLeft();
        float right = w - getPaddingRight();
        float bottom = h - getPaddingBottom();

        // available height for 12 hours (two columns represent 24h)
        float innerH = Math.max(0, bottom - top);

        // ✅ Dynamic hour height: always fit 12 rows
        float hourHeightPx = innerH / 12f;

        float gutterPx = dp(gutterDp);
        float midGapPx = dp(midGapDp);
        float paddingPx = dp(paddingDp);
        float radiusPx = dp(radiusDp);

        // Layout: [gutter][leftCol][midGap][gutter][rightCol]
        float totalGutters = gutterPx + midGapPx + gutterPx;
        float colW = (right - left - totalGutters) / 2f;
        if (colW < dp(40)) return; // too narrow

        float leftColX0 = left + gutterPx;
        float leftColX1 = leftColX0 + colW;

        float rightGutterX0 = leftColX1 + midGapPx;
        float rightColX0 = rightGutterX0 + gutterPx;
        float rightColX1 = rightColX0 + colW;

        // 1) Grid + labels (0..11 left, 12..23 right)
        for (int i = 0; i <= 12; i++) {
            float y = top + i * hourHeightPx;

            // grid lines
            canvas.drawLine(leftColX0, y, leftColX1, y, gridPaint);
            canvas.drawLine(rightColX0, y, rightColX1, y, gridPaint);

            if (i < 12) {
                // put label inside the row (avoid clipping at bottom)
                float labelY = y + Math.min(dp(14), hourHeightPx * 0.35f);

                String l = String.format(Locale.getDefault(), "%02d", i);
                canvas.drawText(l, left + dp(6), labelY, labelPaint);

                String r = String.format("%02d", 12 + i);
                canvas.drawText(r, rightGutterX0 + dp(6), labelY, labelPaint);
            }
        }

        // 2) Blocks split by noon if needed
        for (DayBlock b : blocks) {
            if (b.endMin <= b.startMin) continue;

            // Left half: minutes [0..720]
            int aStart = clamp(b.startMin, 0, 720);
            int aEnd = clamp(b.endMin, 0, 720);
            if (aEnd > aStart) {
                drawBlock(canvas, b, aStart, aEnd, leftColX0, leftColX1, top, hourHeightPx, paddingPx, radiusPx);
            }

            // Right half: minutes [720..1440] mapped to [0..720]
            int bStart = clamp(b.startMin, 720, 1440);
            int bEnd = clamp(b.endMin, 720, 1440);
            if (bEnd > bStart) {
                drawBlock(canvas, b, bStart - 720, bEnd - 720, rightColX0, rightColX1, top, hourHeightPx, paddingPx, radiusPx);
            }
        }
    }

    private void drawBlock(Canvas canvas, DayBlock original,
                           int startInHalfMin, int endInHalfMin,
                           float x0, float x1, float top,
                           float hourHeightPx, float paddingPx, float radiusPx) {

        float y1 = top + (startInHalfMin / 60f) * hourHeightPx;
        float y2 = top + (endInHalfMin / 60f) * hourHeightPx;

        rect.set(
                x0 + paddingPx,
                y1 + dp(2),
                x1 - paddingPx,
                y2 - dp(2)
        );

        Paint p;
        if (original.isTravel) p = blockTravelPaint;
        else p = original.muted ? blockMutedPaint : blockPaint;
        canvas.drawRoundRect(rect, radiusPx, radiusPx, p);

        String time = minutesToText(original.startMin) + getResources().getString(R.string.time_range_sep) + minutesToText(original.endMin);
        float tx = rect.left + dp(8);
        float ty = rect.top + dp(16);

        canvas.drawText(time, tx, ty, blockTextPaint);

// 2ª línea: título
        if (rect.height() > dp(32)) {
            canvas.drawText(original.title, tx, ty + dp(16), blockTextPaint);
        }

// 3ª línea: place
        if (rect.height() > dp(48)) {
            String place = original.placeText != null ? original.placeText : "";
            if (!place.isEmpty()) {
                canvas.drawText(place, tx, ty + dp(32), blockTextPaint);
            }
        }
    }

    private String minutesToText(int min) {
        int h = min / 60;
        int m = min % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}