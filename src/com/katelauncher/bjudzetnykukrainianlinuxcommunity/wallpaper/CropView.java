package com.katelauncher.bjudzetnykukrainianlinuxcommunity.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.katelauncher.bjudzetnykukrainianlinuxcommunity.core.Ui;

/** Вікно обрізки: рухати одним пальцем, масштабувати двома. Рамка має пропорції екрана. */
public class CropView extends View {
    private Bitmap bmp;
    private final Matrix m = new Matrix();
    private final Matrix inv = new Matrix();
    private final RectF frame = new RectF();
    private final RectF tmp = new RectF();
    private final float[] vals = new float[9];
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dim = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector sgd;
    private float aspect = 0.6f, minScale = 1f, lastX, lastY;
    private int active = -1;

    public CropView(Context c) {
        super(c);
        dim.setColor(0xB3000000);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(c, 2));
        line.setColor(0xFFFFFFFF);
        sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoom(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
    }

    public void setFrameAspect(float a) {
        aspect = a;
        computeFrame();
        resetFit();
    }

    public void setBitmap(Bitmap b) {
        bmp = b;
        resetFit();
    }

    public Bitmap getBitmap() { return bmp; }

    public void rotate90() {
        if (bmp == null) return;
        Matrix r = new Matrix();
        r.postRotate(90);
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), r, true);
        resetFit();
    }

    /** Частки (l,t,r,b) вихідного зображення, що потрапили в рамку. */
    public RectF getCropFractions() {
        m.invert(inv);
        tmp.set(frame);
        inv.mapRect(tmp);
        float w = bmp.getWidth(), h = bmp.getHeight();
        return new RectF(c01(tmp.left / w), c01(tmp.top / h), c01(tmp.right / w), c01(tmp.bottom / h));
    }

    private static float c01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private void computeFrame() {
        if (getWidth() == 0 || getHeight() == 0) return;
        float margin = Ui.dp(getContext(), 16);
        float aw = getWidth() - 2 * margin, ah = getHeight() - 2 * margin;
        float fw, fh;
        if (aw / ah > aspect) { fh = ah; fw = fh * aspect; } else { fw = aw; fh = fw / aspect; }
        frame.set((getWidth() - fw) / 2f, (getHeight() - fh) / 2f, (getWidth() + fw) / 2f, (getHeight() + fh) / 2f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        computeFrame();
        resetFit();
    }

    private void resetFit() {
        if (bmp == null || frame.isEmpty()) return;
        minScale = Math.max(frame.width() / bmp.getWidth(), frame.height() / bmp.getHeight());
        m.reset();
        m.postScale(minScale, minScale);
        m.postTranslate(frame.centerX() - bmp.getWidth() * minScale / 2f, frame.centerY() - bmp.getHeight() * minScale / 2f);
        invalidate();
    }

    private float currentScale() {
        m.getValues(vals);
        return vals[Matrix.MSCALE_X];
    }

    private void zoom(float factor, float fx, float fy) {
        float cur = currentScale();
        float target = Math.max(minScale, Math.min(cur * factor, minScale * 8f));
        float f = target / cur;
        m.postScale(f, f, fx, fy);
        clamp();
        invalidate();
    }

    private void clamp() {
        if (bmp == null) return;
        tmp.set(0, 0, bmp.getWidth(), bmp.getHeight());
        m.mapRect(tmp);
        float dx = 0, dy = 0;
        if (tmp.left > frame.left) dx = frame.left - tmp.left;
        else if (tmp.right < frame.right) dx = frame.right - tmp.right;
        if (tmp.top > frame.top) dy = frame.top - tmp.top;
        else if (tmp.bottom < frame.bottom) dy = frame.bottom - tmp.bottom;
        m.postTranslate(dx, dy);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null) return;
        c.drawBitmap(bmp, m, bitmapPaint);
        float w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, frame.top, dim);
        c.drawRect(0, frame.bottom, w, h, dim);
        c.drawRect(0, frame.top, frame.left, frame.bottom, dim);
        c.drawRect(frame.right, frame.top, w, frame.bottom, dim);
        c.drawRect(frame, line);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        sgd.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                active = e.getPointerId(0);
                lastX = e.getX();
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_MOVE: {
                int i = e.findPointerIndex(active);
                if (i >= 0) {
                    float x = e.getX(i), y = e.getY(i);
                    if (!sgd.isInProgress()) {
                        m.postTranslate(x - lastX, y - lastY);
                        clamp();
                        invalidate();
                    }
                    lastX = x;
                    lastY = y;
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                if (e.getPointerId(idx) == active) {
                    int ni = idx == 0 ? 1 : 0;
                    active = e.getPointerId(ni);
                    lastX = e.getX(ni);
                    lastY = e.getY(ni);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                active = -1;
                break;
        }
        return true;
    }
}
