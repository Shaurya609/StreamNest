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
  Button createGroup;
  TextView groupHeading, devices;
  EditText groupName, deviceName;
  final Handler ui = new Handler(Looper.getMainLooper());

  int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density);
  }

  Button button(LinearLayout root, String label, Runnable action) {
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
    return b;
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
    groupHeading = title;
    title.setText("Resume across devices");
    title.setTextSize(24);
    root.addView(title);
    TextView explanation = new TextView(this);
    explanation.setText(
        "Enter the eight-character code shown on your other device. Watching progress is encrypted before syncing. No"
            + " StreamNest account is needed.");
    root.addView(explanation);
    code = new EditText(this);
    code.setHint("Eight-character pairing code");
    code.setSingleLine(true);
    code.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    root.addView(code);
    try {
      code.setText("");
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
    groupName = new EditText(this);
    groupName.setHint("Sync group name");groupName.setSingleLine(true);
    groupName.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
    root.addView(groupName);
    deviceName = new EditText(this);deviceName.setHint("This device name");deviceName.setSingleLine(true);
    deviceName.setText(SyncGroup.deviceName(this));
    deviceName.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
    root.addView(deviceName);
    devices = new TextView(this);root.addView(devices);
    button(root,"Save group name",()->saveGroup(false));
    button(root,"Save device name",()->saveGroup(true));
    createGroup = button(
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
                        if(!WatchSync.code(this).isEmpty())throw new Exception("Disconnect before creating another group");
                        String name=groupName.getText().toString().trim();
                        if(name.isEmpty())throw new Exception("Enter a group name");
                        WatchSync.connect(this,WatchSyncCrypto.create());
                        saveGroup(false);
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
    button(root, "Show pairing code", () -> {
      status.setText("Creating pairing code…");
      WatchSync.worker.execute(() -> {
        try {
          String recovery = WatchSync.code(this);
          WatchSyncCrypto.parse(recovery);
          String shortCode = PairingTicket.create();
          PairingTicket.call(this,shortCode,"PUT",PairingTicket.seal(shortCode,recovery),
              WatchSyncCrypto.auth(recovery));
          ui.post(() -> {
            code.setText(shortCode.substring(0,4)+" "+shortCode.substring(4));
            error = "Expires in 5 minutes · usable once";
            String displayed = code.getText().toString();
            ui.postDelayed(() -> {
              if (displayed.contentEquals(code.getText())) {
                code.setText("");
                error = "Pairing code expired. Select Show pairing code again.";
                drawQr();
              }
            }, 300000);
            drawQr();
          });
        } catch(Exception exception) {
          ui.post(() -> { error = exception.getMessage(); status.setText(error); });
        }
      });
    });
    button(root, "Show recovery key", () -> {
      try { code.setText(WatchSync.code(this)); drawQr(); }
      catch(Exception exception) { error = exception.getMessage(); }
    });
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
        "Group and device names are visible to the service owner; watch history stays encrypted. Pairing codes expire after five minutes and work once. Keep the recovery key private. A phone camera can scan"
            + " the QR code when StreamNest is installed. Local progress works offline; inactive"
            + " cloud backups are cleared after 180 days.");
    root.addView(note);
    drawQr();
    WatchSync.request(this,true);
    ui.post(refresh);
  }

  void saveGroup(boolean deviceOnly) {
    String name=(deviceOnly?deviceName:groupName).getText().toString().trim();
    if(name.isEmpty()){error="Enter a name";return;}
    if(deviceOnly)SyncGroup.prefs(this).edit().putString("deviceName",name).apply();
    SyncGroup.worker.execute(()->{try{SyncGroup.update(this,deviceOnly?null:name);ui.post(()->error=null);}catch(Exception e){ui.post(()->error=e.getMessage());}});
  }

  void connect() {
    error = null;
    String entered = code.getText().toString();
    try {
      if (entered.replaceAll("[\\s-]","").length() == 8) PairingTicket.normalize(entered);
      else WatchSyncCrypto.parse(entered);
    } catch(Exception exception) {
      error = exception.getMessage();
      status.setText(error);
      return;
    }

    status.setText("Connecting…");
    WatchSync.worker.execute(() -> {
      try {
        String recovery = entered;
        if (entered.replaceAll("[\\s-]","").length() == 8)
          recovery = PairingTicket.open(entered,
              PairingTicket.call(this,entered,"POST",null,null).getString("payload"));
        WatchSync.connect(this,recovery);
        ui.post(() -> { error = null; code.setText(""); drawQr(); });
      } catch(Exception exception) {
        ui.post(() -> { error = exception.getMessage(); status.setText(error); });
      }
    });
  }

  void drawQr() {
    try {
      String value = code.getText().toString();
      if (value.replaceAll("[\\s-]","").length() == 8) PairingTicket.normalize(value);
      else WatchSyncCrypto.parse(value);
      BitMatrix bits =
          new MultiFormatWriter()
              .encode((value.replaceAll("[\\s-]","").length() == 8
                      ? WatchSync.endpoint(this) + "/pair#" + android.net.Uri.encode(value)
                      : "streamnest-watch://pair/" + android.net.Uri.encode(value)), BarcodeFormat.QR_CODE, 360, 360);
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
          try {
            boolean connected=!WatchSync.code(WatchSyncActivity.this).isEmpty();
            createGroup.setVisibility(connected?android.view.View.GONE:android.view.View.VISIBLE);
            org.json.JSONObject group=SyncGroup.current;
            groupHeading.setText(connected?"Sync group: "+(group==null?"Sync group":group.optString("name")):"Resume across devices");
            if(group!=null&&connected){
              if(!groupName.hasFocus())groupName.setText(group.optString("name"));
              org.json.JSONArray rows=group.optJSONArray("devices");
              StringBuilder names=new StringBuilder();
              if(rows!=null)for(int i=0;i<rows.length();i++){if(i>0)names.append(", ");names.append(rows.getJSONObject(i).optString("name"));}
              devices.setText((rows==null?0:rows.length())+" devices · "+names);
            }else devices.setText(connected?SyncGroup.error:"");
          }catch(Exception ignored){}
          ui.postDelayed(this, 1000);
        }
      };

  protected void onDestroy() {
    ui.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
