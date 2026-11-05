package app.streamnest;

import android.app.*;
import android.content.Intent;
import android.graphics.Rect;
import android.os.*;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Opens the real Settings row; never changes the user's saved pairing or accounts. */
final class WatchSyncUiRegression {
  static Bundle run(PlaybackRegression p) throws Exception {
    MainActivity main = null;
    WatchSyncActivity screen = null;
    String expectedPairingUri = WatchSync.endpoint(p.getTargetContext()) + "/pair#K7MP%204RXT";
    boolean pending = WatchSync.pending;
    WatchSync.pending = true;
    Instrumentation.ActivityMonitor monitor =
        p.addMonitor(WatchSyncActivity.class.getName(), null, false);
    try {
      main = (MainActivity) p.startActivitySync(
          new Intent(p.getTargetContext(), MainActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      MainActivity home = main;
      p.runOnMainSync(home::settings);
      boolean clicked = false;
      for (int attempt = 0; attempt < 30 && !clicked; attempt++) {
        AccessibilityNodeInfo root = p.getUiAutomation().getRootInActiveWindow();
        if (root != null) {
          for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Resume & sync devices")) {
            if ("Resume & sync devices".contentEquals(node.getText())) {
              clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
              if (!clicked && node.getParent() != null)
                clicked = node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
              if (clicked) break;
            }
          }
        }
        if (!clicked) SystemClock.sleep(100);
      }
      if (!clicked) throw new AssertionError("Settings sync row was not clickable");
      screen = (WatchSyncActivity) p.waitForMonitorWithTimeout(monitor, 5000);
      if (screen == null) throw new AssertionError("Sync row did not open its screen");
      WatchSyncActivity sync = screen;
      p.runOnMainSync(() -> {
        Rect rect = new Rect();
        if (!sync.code.isShown() || !sync.code.getGlobalVisibleRect(rect) || rect.height() < 20)
          throw new AssertionError("Pairing input is not visible");
        sync.code.setText("invalid-code");
        sync.connect();
        if (sync.error == null || sync.status.getText().length() == 0)
          throw new AssertionError("Invalid pairing code failed silently");
        try { sync.code.setText(WatchSyncCrypto.create()); }
        catch (Exception e) { throw new AssertionError(e); }
        sync.drawQr();
        if (sync.qr.getVisibility() != View.VISIBLE || sync.qr.getDrawable() == null)
          throw new AssertionError("Pairing QR did not render");
        sync.code.setText("K7MP 4RXT");
        sync.drawQr();
        android.graphics.Bitmap bitmap =
            ((android.graphics.drawable.BitmapDrawable) sync.qr.getDrawable()).getBitmap();
        int[] pixels = new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        try {
          com.google.zxing.Result decoded = new com.google.zxing.MultiFormatReader().decode(
              new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                new com.google.zxing.RGBLuminanceSource(bitmap.getWidth(),bitmap.getHeight(),pixels))));
          if (!decoded.getText().equals(expectedPairingUri))
            throw new AssertionError("QR did not contain the temporary pairing link");
        } catch(com.google.zxing.NotFoundException exception) {
          throw new AssertionError("QR cannot be decoded",exception);
        }
        sync.code.setText("");
        sync.drawQr();
        if (sync.qr.getVisibility() != View.GONE)
          throw new AssertionError("Empty pairing code retained its QR");
      });
      Bundle result = new Bundle();
      result.putString("result", "PASS: real Settings row opened visible sync input; invalid code reported; valid QR rendered and cleared; saved pairing untouched");
      return result;
    } finally {
      if (screen != null) p.runOnMainSync(screen::finish);
      if (main != null) p.runOnMainSync(main::finish);
      p.removeMonitor(monitor);
      WatchSync.pending = pending;
    }
  }
}
