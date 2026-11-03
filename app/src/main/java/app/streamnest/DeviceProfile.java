package app.streamnest;

import android.app.ActivityManager;
import android.content.Context;

final class DeviceProfile {
  static boolean projector(Context c) {
    return c.getSharedPreferences("performance", 0).getString("mode", "auto").equals("lite")
        || (c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_TYPE_MASK)
            == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
  }

  static boolean lite(Context c) {
    String mode = c.getSharedPreferences("performance", 0).getString("mode", "auto");
    if (!mode.equals("auto")) return mode.equals("lite");
    ActivityManager a = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
    a.getMemoryInfo(m);
    return a.isLowRamDevice() || m.totalMem <= 1536L * 1024 * 1024;
  }
}
