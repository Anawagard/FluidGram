package org.telegram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.graphics.RenderEffect;
import android.view.View;

/**
 * FluidGram glass surfaces. The ambient scene is drawn once per invalidation:
 * no continuously running animations, timers or off-screen blur sampling.
 * RenderEffect blurs only the ambient layer, never text or interactive controls.
 */
public final class FluidGlass {
    private FluidGlass() {}

    public static Drawable surface(Context context, boolean prominent) {
        float density = context.getResources().getDisplayMetrics().density;
        boolean highlights = context.getSharedPreferences("fluidgram_labs", Context.MODE_PRIVATE)
                .getBoolean("neon_labs", true);
        Drawable panel = new Panel(density, prominent, highlights);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), panel, null);
    }

    public static void setAmbientBlur(View backdrop, boolean enabled) {
        if (Build.VERSION.SDK_INT >= 31) {
            // A static GPU effect on the decorative layer; keep the UI/text crisp.
            backdrop.setRenderEffect(enabled
                    ? RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
                    : null);
        }
    }

    public static final class Backdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public Backdrop(Context context) {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            paint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{0xFF03080F, 0xFF101E2C, 0xFF03060B},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            // ColorOS-inspired reflected ambient light; no continuous redraw.
            float radius = Math.max(w, h) * .63f;
            paint.setShader(new RadialGradient(w * .12f, h * .17f, radius,
                    new int[]{0x745DADBC, 0x20507791, 0x00102130},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(w * .12f, h * .17f, radius, paint);
            paint.setShader(new RadialGradient(w * .95f, h * .72f, radius * .78f,
                    new int[]{0x485E789B, 0x15445E7B, 0x00102130},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(w * .95f, h * .72f, radius * .78f, paint);
            paint.setShader(null);
        }
    }

    private static final class Panel extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final boolean prominent;
        private final boolean highlights;
        private int alpha = 255;
        Panel(float density, boolean prominent, boolean highlights) {
            this.density = density;
            this.prominent = prominent;
            this.highlights = highlights;
        }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.isEmpty()) return;
            RectF rect = new RectF(b.left, b.top, b.right, b.bottom);
            float radius = (prominent ? 28f : 22f) * density;
            float h = rect.height();
            float x = rect.left, y = rect.top;
            paint.setAlpha(alpha);
            // Translucent tinted surface: the blurred ambient scene stays visible.
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(x, y, rect.right, rect.bottom,
                    prominent
                        ? new int[]{0xB17E9AAA, 0x995A6C7C, 0xB1354658}
                        : new int[]{0x8F9DA9B7, 0x6F657789, 0xA92A3B4B},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, paint);
            // Specular highlight and a fine, asymmetric glass rim.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setShader(new LinearGradient(x, y, rect.right, rect.bottom,
                    highlights ? new int[]{0xD9FFFFFF, 0x75E4F5FF, 0x1BFFFFFF, 0x65B2D4E5}
                               : new int[]{0x70FFFFFF, 0x3EE4F5FF, 0x0BFFFFFF, 0x306080A0},
                    null, Shader.TileMode.CLAMP));
            RectF inset = new RectF(rect);
            inset.inset(density * .5f, density * .5f);
            canvas.drawRoundRect(inset, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, y, 0, y + Math.max(1f, h * .42f),
                    highlights ? new int[]{0x60FFFFFF, 0x0CFFFFFF, 0x00FFFFFF}
                               : new int[]{0x18FFFFFF, 0x04FFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            Path glint = new Path();
            glint.moveTo(x + radius, y + density * 2f);
            glint.lineTo(rect.right - radius, y + density * 2f);
            glint.quadTo(rect.right - density * 4f, y + density * 2f,
                    rect.right - density * 4f, y + radius);
            glint.lineTo(x + density * 4f, y + radius);
            glint.quadTo(x + density * 4f, y + density * 2f,
                    x + radius, y + density * 2f);
            canvas.drawPath(glint, paint);
            paint.setShader(null);
        }
        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
