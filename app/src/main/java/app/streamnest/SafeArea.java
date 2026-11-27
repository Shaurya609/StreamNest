package app.streamnest;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Protect browsing/settings controls from system bars and cutouts; the fullscreen player opts out. */
final class SafeArea {
  static void apply(Activity activity) {
    apply(activity.getWindow());
  }

  static void apply(Window window) {
    View content = window.findViewById(android.R.id.content);
    WindowCompat.setDecorFitsSystemWindows(window, false);
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    content.setBackgroundColor(0xff17181b);
    WindowCompat.getInsetsController(window, content).setAppearanceLightStatusBars(false);
    WindowCompat.getInsetsController(window, content).setAppearanceLightNavigationBars(false);
    final int left=content.getPaddingLeft(), top=content.getPaddingTop(), right=content.getPaddingRight(), bottom=content.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
      Insets safe=windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
          | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
      view.setPadding(left+safe.left, top+safe.top, right+safe.right, bottom+safe.bottom);
      return WindowInsetsCompat.CONSUMED;
    });
    ViewCompat.requestApplyInsets(content);
  }
}
