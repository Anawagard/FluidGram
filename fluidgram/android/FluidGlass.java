package org.telegram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.graphics.RenderEffect;
import android.view.View;

/**
 * Glass with cached shaders: no shader/path allocation per draw, no animation
 * loop, and no redundant GPU blur changes on battery broadcasts.
 */
public final class FluidGlass {
    private FluidGlass() {}

    public static Drawable surface(Context context, boolean prominent) {
        float density = context.getResources().getDisplayMetrics().density;
        boolean highlights = context.getSharedPreferences("fluidgram_labs", Context.MODE_PRIVATE)
                .getBoolean("neon_labs", true);
        // Accessibility / low-compositing option: fully opaque panels and no shaders.
        // This is intentionally opt-in and only affects FluidGram Labs surfaces.
        boolean opaque = context.getSharedPreferences("fluidgram_labs", Context.MODE_PRIVATE)
                .getBoolean("reduce_glass_transparency", false);
        if (opaque) {
            GradientDrawable solid = new GradientDrawable();
            solid.setColor(prominent ? 0xFF253B4B : 0xFF1B2D3C);
            solid.setCornerRadius((prominent ? 28f : 22f) * density);
            solid.setStroke(Math.max(1, Math.round(density)), 0xFF64879A);
            return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), solid, null);
        }
        Drawable panel = new Panel(density, prominent, highlights);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), panel, null);
    }

    public static void setAmbientBlur(View backdrop, boolean enabled) {
        if (backdrop instanceof Backdrop) {
            ((Backdrop) backdrop).setBlurEnabled(enabled);
        } else if (Build.VERSION.SDK_INT >= 31) {
            backdrop.setRenderEffect(enabled
                    ? RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
                    : null);
        }
    }

    public static final class Backdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader baseShader, firstGlow, secondGlow;
        private float glowRadius;
        private boolean blurEnabled;

        public Backdrop(Context context) {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        void setBlurEnabled(boolean enabled) {
            if (Build.VERSION.SDK_INT < 31) return;
            if (blurEnabled == enabled) return;
            blurEnabled = enabled;
            setRenderEffect(enabled
                    ? RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
                    : null);
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0 || h <= 0) {
                baseShader = firstGlow = secondGlow = null;
                return;
            }
            baseShader = new LinearGradient(0, 0, w, h,
                    new int[]{0xFF03080F, 0xFF101E2C, 0xFF03060B},
                    null, Shader.TileMode.CLAMP);
            glowRadius = Math.max(w, h) * .63f;
            firstGlow = new RadialGradient(w * .12f, h * .17f, glowRadius,
                    new int[]{0x745DADBC, 0x20507791, 0x00102130},
                    null, Shader.TileMode.CLAMP);
            secondGlow = new RadialGradient(w * .95f, h * .72f, glowRadius * .78f,
                    new int[]{0x485E789B, 0x15445E7B, 0x00102130},
                    null, Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || baseShader == null) return;
            paint.setShader(baseShader);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(firstGlow);
            canvas.drawCircle(w * .12f, h * .17f, glowRadius, paint);
            paint.setShader(secondGlow);
            canvas.drawCircle(w * .95f, h * .72f, glowRadius * .78f, paint);
            paint.setShader(null);
        }
    }

    private static final class Panel extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF inset = new RectF();
        private final Path glint = new Path();
        private final float density;
        private final boolean prominent;
        private final boolean highlights;
        private Shader fillShader, rimShader, sheenShader;
        private float radius;
        private int alpha = 255;

        Panel(float density, boolean prominent, boolean highlights) {
            this.density = density;
            this.prominent = prominent;
            this.highlights = highlights;
        }
        @Override protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            rect.set(bounds);
            if (rect.isEmpty()) {
                fillShader = rimShader = sheenShader = null;
                glint.reset();
                return;
            }
            radius = (prominent ? 28f : 22f) * density;
            float x = rect.left, y = rect.top;
            fillShader = new LinearGradient(x, y, rect.right, rect.bottom,
                    prominent
                        ? new int[]{0xB17E9AAA, 0x995A6C7C, 0xB1354658}
                        : new int[]{0x8F9DA9B7, 0x6F657789, 0xA92A3B4B},
                    null, Shader.TileMode.CLAMP);
            rimShader = new LinearGradient(x, y, rect.right, rect.bottom,
                    highlights ? new int[]{0xD9FFFFFF, 0x75E4F5FF, 0x1BFFFFFF, 0x65B2D4E5}
                               : new int[]{0x70FFFFFF, 0x3EE4F5FF, 0x0BFFFFFF, 0x306080A0},
                    null, Shader.TileMode.CLAMP);
            sheenShader = new LinearGradient(0, y, 0, y + Math.max(1f, rect.height() * .42f),
                    highlights ? new int[]{0x60FFFFFF, 0x0CFFFFFF, 0x00FFFFFF}
                               : new int[]{0x18FFFFFF, 0x04FFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP);
            inset.set(rect);
            inset.inset(density * .5f, density * .5f);
            glint.reset();
            glint.moveTo(x + radius, y + density * 2f);
            glint.lineTo(rect.right - radius, y + density * 2f);
            glint.quadTo(rect.right - density * 4f, y + density * 2f,
                    rect.right - density * 4f, y + radius);
            glint.lineTo(x + density * 4f, y + radius);
            glint.quadTo(x + density * 4f, y + density * 2f,
                    x + radius, y + density * 2f);
        }
        @Override public void draw(Canvas canvas) {
            if (rect.isEmpty() || fillShader == null) return;
            paint.setAlpha(alpha);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(fillShader);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setShader(rimShader);
            canvas.drawRoundRect(inset, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(sheenShader);
            canvas.drawPath(glint, paint);
            paint.setShader(null);
        }
        @Override public void setAlpha(int value) {
            if (alpha != value) { alpha = value; invalidateSelf(); }
        }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
