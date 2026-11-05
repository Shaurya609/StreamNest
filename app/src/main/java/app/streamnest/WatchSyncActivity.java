package app.streamnest;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;

public class WatchSyncActivity extends Activity {
  EditText code;
  TextView status;
  ImageView qr;
  String error;
  final Handler ui = new Handler(Looper.getMainLooper());

  int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density);
  }

  void button(LinearLayout root, String label, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    root.addView(b);
    b.setOnClickListener(
        v -> {
          try {
            error = null;
            action.run();
          } catch (Exception e) {
            error = e.getMessage();
            status.setText(error);
          }
        });
  }

  public void onCreate(Bundle state) {
    super.onCreate(state);
    ScrollView scroll = new ScrollView(this);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(20), dp(20), dp(20), dp(20));
    scroll.addView(root);
    setContentView(scroll);
    TextView title = new TextView(this);
    title.setText("Resume across devices");
    title.setTextSize(24);
    root.addView(title);
    TextView explanation = new TextView(this);
    explanation.setText(
        "Pair devices with the same private code. Watching progress is encrypted before syncing. No"
            + " StreamNest account is needed.");
    root.addView(explanation);
    code = new EditText(this);
    code.setHint("Pairing code from another device");
    code.setSingleLine(true);
    code.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    root.addView(code);
    try {
      code.setText(WatchSync.code(this));
    } catch (Exception ignored) {
    }
    if (getIntent().getData() != null
        && "streamnest-watch".equals(getIntent().getData().getScheme())
        && "pair".equals(getIntent().getData().getHost())) {
      String candidate = getIntent().getData().getLastPathSegment();
      if (candidate != null) code.setText(candidate);
    }
    status = new TextView(this);
    root.addView(status);
    qr = new ImageView(this);
    root.addView(qr, new LinearLayout.LayoutParams(dp(240), dp(240)));
    button(
        root,
        "Create a sync group",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Create private sync group?")
                .setMessage(
                    "This device's watch positions will be shared with devices using the new code.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Create",
                    (d, w) -> {
                      try {
                        code.setText(WatchSyncCrypto.create());
                        connect();
                      } catch (Exception e) {
                        error = e.getMessage();
                        status.setText(error);
                      }
                    })
                .show());
    button(
        root,
        "Connect with this code",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Connect paired devices?")
                .setMessage(
                    "Confirm this code came from your other device. Watch positions will be merged"
                        + " with this private group.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Connect", (d, w) -> connect())
                .show());
    button(root, "Sync now", () -> WatchSync.request(this, true));
    button(
        root,
        "Disconnect this device",
        () -> {
          try {
            WatchSync.disconnect(this);
            code.setText("");
            drawQr();
          } catch (Exception e) {
            error = e.getMessage();
            status.setText(error);
          }
        });
    TextView note = new TextView(this);
    note.setText(
        "Keep the code private and save a copy for pairing future devices. A phone camera can scan"
            + " the QR code when StreamNest is installed. Local progress works offline; inactive"
            + " cloud backups are cleared after 180 days.");
    root.addView(note);
    drawQr();
    ui.post(refresh);
  }

  void connect() {
    try {
      error = null;
      WatchSync.connect(this, code.getText().toString());
      code.setText(WatchSync.code(this));
      drawQr();
    } catch (Exception e) {
      error = e.getMessage();
      status.setText(error);
    }
  }

  void drawQr() {
    try {
      String value = code.getText().toString();
      WatchSyncCrypto.parse(value);
      BitMatrix bits =
          new MultiFormatWriter()
              .encode("streamnest-watch://pair/" + value, BarcodeFormat.QR_CODE, 360, 360);
      Bitmap bitmap = Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888);
      for (int y = 0; y < 360; y++)
        for (int x = 0; x < 360; x++)
          bitmap.setPixel(x, y, bits.get(x, y) ? 0xff000000 : 0xffffffff);
      qr.setImageBitmap(bitmap);
      qr.setVisibility(android.view.View.VISIBLE);
    } catch (Exception e) {
      qr.setVisibility(android.view.View.GONE);
    }
  }

  final Runnable refresh =
      new Runnable() {
        public void run() {
          status.setText(error == null ? WatchSync.status : error);
          ui.postDelayed(this, 1000);
        }
      };

  protected void onDestroy() {
    ui.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
