package georgii.sytnik.thothtasks.ui.schedule;

import static georgii.sytnik.thothtasks.util.TimeText.minutesToText;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import georgii.sytnik.thothtasks.R;

public class SplitDayTimelineView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockMutedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockTravelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();
    private final List<DayBlock> blocks = new ArrayList<>();

    private final float gutterDp = 40f;
    private final float midGapDp = 6f;
    private final float paddingDp = 6f;
    private final float radiusDp = 10f;

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

    public void setBlocks(List<DayBlock> newBlocks) {
        blocks.clear();
        if (newBlocks != null) blocks.addAll(newBlocks);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
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

        float innerH = Math.max(0, bottom - top);

        float hourHeightPx = innerH / 12f;

        float gutterPx = dp(gutterDp);
        float midGapPx = dp(midGapDp);
        float paddingPx = dp(paddingDp);
        float radiusPx = dp(radiusDp);

        float totalGutters = gutterPx + midGapPx + gutterPx;
        float colW = (right - left - totalGutters) / 2f;
        if (colW < dp(40)) return;

        float leftColX0 = left + gutterPx;
        float leftColX1 = leftColX0 + colW;

        float rightGutterX0 = leftColX1 + midGapPx;
        float rightColX0 = rightGutterX0 + gutterPx;
        float rightColX1 = rightColX0 + colW;

        for (int i = 0; i <= 12; i++) {
            float y = top + i * hourHeightPx;

            canvas.drawLine(leftColX0, y, leftColX1, y, gridPaint);
            canvas.drawLine(rightColX0, y, rightColX1, y, gridPaint);

            if (i < 12) {
                float labelY = y + Math.min(dp(14), hourHeightPx * 0.35f);

                String l = String.format(Locale.getDefault(), "%02d", i);
                canvas.drawText(l, left + dp(6), labelY, labelPaint);

                String r = String.format("%02d", 12 + i);
                canvas.drawText(r, rightGutterX0 + dp(6), labelY, labelPaint);
            }
        }

        for (DayBlock b : blocks) {
            if (b.endMin <= b.startMin) continue;

            int aStart = clamp(b.startMin, 0, 720);
            int aEnd = clamp(b.endMin, 0, 720);
            if (aEnd > aStart) {
                drawBlock(canvas, b, aStart, aEnd, leftColX0, leftColX1, top, hourHeightPx, paddingPx, radiusPx);
            }

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

        if (rect.height() > dp(32)) {
            canvas.drawText(original.title, tx, ty + dp(16), blockTextPaint);
        }

        if (rect.height() > dp(48)) {
            String place = original.placeText != null ? original.placeText : "";
            if (!place.isEmpty()) {
                canvas.drawText(place, tx, ty + dp(32), blockTextPaint);
            }
        }
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}