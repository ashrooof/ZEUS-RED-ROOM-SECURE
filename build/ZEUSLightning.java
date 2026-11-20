package grant.bt.contact.transfer;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public final class ZEUSLightning {
    private ZEUSLightning() {}

    public static void attach(Activity activity) {
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) content;
        LightningView overlay = new LightningView(activity);
        attachTree(root, overlay);
        root.addView(overlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View search = activity.findViewById(0x7f080092);
        if (search != null) {
            ValueAnimator pulse = ValueAnimator.ofFloat(1f, 1.085f);
            pulse.setDuration(950);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.addUpdateListener(a -> {
                float scale = (Float) a.getAnimatedValue();
                search.setScaleX(scale);
                search.setScaleY(scale);
            });
            pulse.start();
        }
    }

    private static void attachTree(View view, final LightningView overlay) {
        if (view == overlay) return;
        view.setOnTouchListener((target, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int[] a = new int[2], b = new int[2];
                target.getLocationOnScreen(a);
                overlay.getLocationOnScreen(b);
                overlay.pulse(a[0] + event.getX() - b[0], a[1] + event.getY() - b[1]);
            }
            return false;
        });
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            group.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                public void onChildViewAdded(View parent, View child) { attachTree(child, overlay); }
                public void onChildViewRemoved(View parent, View child) {}
            });
            for (int i = 0; i < group.getChildCount(); i++) attachTree(group.getChildAt(i), overlay);
        }
    }

    private static final class LightningView extends View {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x, y, progress = 1f;
        private ValueAnimator animator;

        LightningView(Activity activity) {
            super(activity);
            setWillNotDraw(false);
            setClickable(false);
            setFocusable(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            glow.setColor(Color.rgb(255, 20, 65));
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeWidth(4f);
            glow.setShadowLayer(18f, 0f, 0f, Color.rgb(255, 0, 45));
            core.setColor(Color.rgb(255, 115, 140));
            core.setStyle(Paint.Style.STROKE);
            core.setStrokeWidth(1.8f);
        }

        void pulse(float px, float py) {
            x = px; y = py; progress = 0f;
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(680);
            animator.addUpdateListener(a -> { progress = (Float) a.getAnimatedValue(); invalidate(); });
            animator.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (progress >= 1f) return;
            int save = canvas.save();
            float alpha = 1f - progress;
            float radius = 18f + 115f * progress;
            glow.setAlpha((int)(220f * alpha));
            core.setAlpha((int)(245f * alpha));
            canvas.drawCircle(x, y, radius, glow);
            canvas.drawCircle(x, y, radius, core);
            canvas.drawCircle(x, y, radius * .72f, core);
            for (int branch = 0; branch < 8; branch++) {
                double angle = Math.PI * 2d * branch / 8d;
                float dx = (float)Math.cos(angle), dy = (float)Math.sin(angle);
                float length = radius * 1.28f;
                Path bolt = new Path();
                bolt.moveTo(x + dx * radius * .30f, y + dy * radius * .30f);
                for (int step = 1; step <= 5; step++) {
                    float distance = radius * .30f + length * step / 5f;
                    float side = (step % 2 == 0 ? 1f : -1f) * (7f + 5f * progress);
                    bolt.lineTo(x + dx * distance - dy * side, y + dy * distance + dx * side);
                }
                canvas.drawPath(bolt, glow);
                canvas.drawPath(bolt, core);
            }
            canvas.restoreToCount(save);
        }
    }
}
