package app.streamnest;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Progress is visual only; the player owns countdown and seek decisions. */
final class SkipButtonBackground extends Drawable {
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final float density;
  float progress;
  boolean automatic;
  ValueAnimator animation;

  SkipButtonBackground(float density) { this.density = density; }

  void progress(float value, boolean automatic, boolean reset) {
    this.automatic = automatic;
    if (animation != null) animation.cancel();
    if (reset) progress = 0;
    if (!automatic) {
      progress = value;
      invalidateSelf();
      return;
    }
    animation = ValueAnimator.ofFloat(progress, value);
    animation.setDuration(500);
    animation.setInterpolator(new android.view.animation.LinearInterpolator());
    animation.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidateSelf(); });
    animation.start();
  }

  void freeze(boolean reset) {
    automatic = true;
    if (animation != null) animation.cancel();
    if (reset) progress = 0;
    invalidateSelf();
  }

  @Override public void draw(Canvas canvas) {
    Rect bounds = getBounds();
    RectF box = new RectF(bounds.left + density, bounds.top + density,
        bounds.right - density, bounds.bottom - density);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xee20232b);
    canvas.drawRoundRect(box, 10*density, 10*density, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(density);
    paint.setColor(0xffdddddd);
    canvas.drawRoundRect(box, 10*density, 10*density, paint);
    if (!automatic) return;
    float left = bounds.left + 16*density, right = bounds.right - 16*density;
    float y = bounds.bottom - 10*density;
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeWidth(4*density);
    paint.setColor(0xff55585e);
    canvas.drawLine(left,y,right,y,paint);
    paint.setColor(Color.WHITE);
    canvas.drawLine(left,y,left+(right-left)*Math.max(0,Math.min(1,progress)),y,paint);
    paint.setStrokeCap(Paint.Cap.BUTT);
  }
  @Override public void setAlpha(int alpha) {}
  @Override public void setColorFilter(ColorFilter filter) {}
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
