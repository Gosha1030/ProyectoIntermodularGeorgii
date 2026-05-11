package georgii.sytnik.thothtasks.ui.schedule;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DayTimelineView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockMutedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();

    private float hourHeightPx;     // height per hour
    private float leftGutterPx;     // for hour labels
    private float paddingPx;
    private float blockRadiusPx;

    private final List<DayBlock> blocks = new ArrayList<>();

    public DayTimelineView(Context context) {
        super(context);
        init();
    }

    public DayTimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DayTimelineView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        hourHeightPx = dp(56);     // looks close to your screenshot
        leftGutterPx = dp(42);
        paddingPx = dp(8);
        blockRadiusPx = dp(10);

        gridPaint.setColor(0xFFCCCCCC);
        gridPaint.setStrokeWidth(dp(1));

        textPaint.setColor(0xFF555555);
        textPaint.setTextSize(dp(12));

        blockPaint.setColor(0xFF3F51B5); // blue-ish
        blockMutedPaint.setColor(0xFF9E9E9E); // grey for muted

        blockTextPaint.setColor(0xFFFFFFFF);
        blockTextPaint.setTextSize(dp(12));
    }

    public void setBlocks(List<DayBlock> newBlocks) {
        blocks.clear();
        if (newBlocks != null) blocks.addAll(newBlocks);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // total height = 24 hours * hourHeight
        int desiredH = (int) (24 * hourHeightPx + getPaddingTop() + getPaddingBottom());
        int w = MeasureSpec.getSize(widthMeasureSpec);

        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int h;
        if (hMode == MeasureSpec.EXACTLY) h = MeasureSpec.getSize(heightMeasureSpec);
        else h = desiredH;

        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float top = getPaddingTop();
        float left = getPaddingLeft();
        float contentLeft = left + leftGutterPx;
        float contentRight = w - getPaddingRight();
        float contentWidth = contentRight - contentLeft;

        // 1) Draw hour grid and labels
        for (int hour = 0; hour <= 24; hour++) {
            float y = top + hour * hourHeightPx;
            canvas.drawLine(contentLeft, y, contentRight, y, gridPaint);

            if (hour < 24) {
                String label = String.format("%02d", hour);
                // align center of hour row
                float ty = y + dp(14);
                canvas.drawText(label, left + dp(6), ty, textPaint);
            }
        }

        // 2) Draw blocks
        for (DayBlock b : blocks) {
            float y1 = top + (b.startMin / 60f) * hourHeightPx;
            float y2 = top + (b.endMin / 60f) * hourHeightPx;

            // clamp
            y1 = Math.max(top, y1);
            y2 = Math.min(top + 24 * hourHeightPx, y2);

            rect.set(
                    contentLeft + paddingPx,
                    y1 + dp(2),
                    contentLeft + contentWidth - paddingPx,
                    y2 - dp(2)
            );

            Paint p = b.muted ? blockMutedPaint : blockPaint;
            canvas.drawRoundRect(rect, blockRadiusPx, blockRadiusPx, p);

            // label with exact time range
            String time = minutesToText(b.startMin) + "–" + minutesToText(b.endMin);
            String title = b.title;

            float tx = rect.left + dp(8);
            float ty = rect.top + dp(16);

            canvas.drawText(time, tx, ty, blockTextPaint);
            // second line if enough height
            if (rect.height() > dp(30)) {
                canvas.drawText(title, tx, ty + dp(16), blockTextPaint);
            } else {
                // if small, show only title (or only time) — we keep time priority
            }
        }
    }

    private String minutesToText(int min) {
        int h = min / 60;
        int m = min % 60;
        return String.format("%02d:%02d", h, m);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}