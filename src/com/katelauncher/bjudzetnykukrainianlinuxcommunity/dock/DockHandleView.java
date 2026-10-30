package com.gnome.launcher.dock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.gnome.launcher.core.Ui;

import java.util.Collections;

/**
 * Смужка-ручка на краї екрана, поки док сховано. Свайп до центру (або тап) повертає док.
 * Область виключена із системного жесту «Назад», щоб свайп не перехоплювався.
 */
public class DockHandleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private final SwipeTracker tracker = new SwipeTracker();
    private final int slop;
    private DockController ctl;
    private int side = DockSide.LEFT;
    private float downRaw;
    private boolean dragging;

    public DockHandleView(Context c) {
        super(c);
        paint.setColor(0x66FFFFFF);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    public void setController(DockController c) { ctl = c; }
    public void setSide(int s) { side = s; invalidate(); }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, r - l, b - t)));
    }

    @Override
    protected void onDraw(Canvas c) {
        Context ctx = getContext();
        float w = getWidth(), h = getHeight();
        float e = Ui.dp(ctx, 3), th = Ui.dp(ctx, 5), len = Ui.dp(ctx, 28);
        switch (side) {
            case DockSide.RIGHT: pill.set(w - e - th, h / 2f - len, w - e, h / 2f + len); break;
            case DockSide.TOP: pill.set(w / 2f - len, e, w / 2f + len, e + th); break;
            case DockSide.BOTTOM: pill.set(w / 2f - len, h - e - th, w / 2f + len, h - e); break;
            default: pill.set(e, h / 2f - len, e + th, h / 2f + len);
        }
        float r = Ui.dp(ctx, 3);
        c.drawRoundRect(pill, r, r, paint);
    }

    private float raw(MotionEvent e) { return DockSide.horizontal(side) ? e.getRawY() : e.getRawX(); }

    /** Додатне значення — рух у бік «показати». */
    private float toward(float rawNow) { return (rawNow - downRaw) * -DockSide.hideDir(side); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int dir = -DockSide.hideDir(side);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRaw = raw(e);
                dragging = false;
                tracker.start(downRaw * dir, e.getEventTime());
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = toward(raw(e));
                tracker.move(raw(e) * dir, e.getEventTime());
                if (!dragging && dx > slop) dragging = true;
                if (dragging && ctl != null) ctl.dragTo(dx / ctl.size());
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (ctl != null) {
                    if (dragging) ctl.release(tracker.velocity());
                    else ctl.animateTo(true);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (ctl != null && dragging) ctl.release(0);
                return true;
        }
        return super.onTouchEvent(e);
    }
}
