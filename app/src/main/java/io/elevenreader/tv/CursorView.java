package io.elevenreader.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Draws a mouse-style arrow pointer on top of the WebView. Purely visual; never takes input. */
public class CursorView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float size;
    private float x;
    private float y;
    private boolean pressed;

    public CursorView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        size = 22 * density;
        fill.setColor(Color.WHITE);
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(Color.BLACK);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * density);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setFocusable(false);
        setClickable(false);
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        invalidate();
    }

    public void setClicking(boolean pressed) {
        this.pressed = pressed;
        fill.setColor(pressed ? Color.rgb(120, 180, 255) : Color.WHITE);
        invalidate();
    }

    public float getCursorX() {
        return x;
    }

    public float getCursorY() {
        return y;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = size;
        arrow.reset();
        arrow.moveTo(x, y);
        arrow.lineTo(x, y + s);
        arrow.lineTo(x + s * 0.28f, y + s * 0.75f);
        arrow.lineTo(x + s * 0.48f, y + s * 1.15f);
        arrow.lineTo(x + s * 0.62f, y + s * 1.08f);
        arrow.lineTo(x + s * 0.43f, y + s * 0.70f);
        arrow.lineTo(x + s * 0.75f, y + s * 0.70f);
        arrow.close();
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, stroke);
    }
}
