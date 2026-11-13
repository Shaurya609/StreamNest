package app.streamnest;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.concurrent.*;

public class SubtitleSettingsActivity extends Activity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private OpenSubtitles api;
  private EditText key, username, password;
  private TextView status, help;
  private Button login, create, guest;
  private Spinner provider;
  private final Handler ui = new Handler(Looper.getMainLooper());

  public void onCreate(Bundle state) {
    super.onCreate(state);
    api = new OpenSubtitles(this);
    LinearLayout root = new LinearLayout(this);
    root.setPadding(32, 32, 32, 24);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xff0b101b);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(root);
    setContentView(scroll);
    TextView title = label("OpenSubtitles");
    title.setTextSize(26);
    root.addView(title);
    root.addView(label("Subtitle service"));
    provider = new Spinner(this);
    provider.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"OpenSubtitles.org — desktop service", "OpenSubtitles.com — REST API"}));
    provider.setSelection(api.orgMode() ? 0 : 1);
    root.addView(provider);
    help = label("");
    help.setTextColor(0xffa8b5cb);
    root.addView(help);
    create = new Button(this);
    create.setText("Get a .com API key");
    root.addView(create);
    create.setOnClickListener(
        v ->
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW, Uri.parse("https://www.opensubtitles.com/en/consumers"))));
    key = field(root, "Application API key", true);
    username = field(root, "Username", false);
    password = field(root, "Password", true);
    status = label("");
    root.addView(status);
    login = new Button(this);
    login.setText("Sign in");
    root.addView(login);
    login.setOnClickListener(v -> signIn());
    guest = new Button(this);
    guest.setText("Use .org without account");
    root.addView(guest);
    guest.setOnClickListener(
        v -> {
          guest.setEnabled(false);
          login.setEnabled(false);
          provider.setEnabled(false);
          status.setText("Connecting without account…");
          worker.execute(
              () -> {
                try {
                  new OrgSubtitles(this).anonymous();
                  ui.post(
                      () -> {
                        if (!isDestroyed()) {
                          refreshProvider();
                          guest.setEnabled(true);
                          login.setEnabled(true);
                          provider.setEnabled(true);
                        }
                      });
                } catch (Exception e) {
            Diagnostics.failure("subtitle.failed","subtitles","fetch",e);
                  ui.post(
                      () -> {
                        if (!isDestroyed()) {
                          status.setText(e.getMessage());
                          guest.setEnabled(true);
                          login.setEnabled(true);
                          provider.setEnabled(true);
                        }
                      });
                }
              });
        });
    CheckBox auto = new CheckBox(this);
    auto.setText("Automatically add subtitles when playback starts");
    auto.setTextColor(-1);
    auto.setChecked(getSharedPreferences("subtitles", 0).getBoolean("auto", true));
    root.addView(auto);
    auto.setOnCheckedChangeListener(
        (v, c) -> getSharedPreferences("subtitles", 0).edit().putBoolean("auto", c).apply());
    root.addView(label("Preferred subtitle language"));
    Spinner languages = new Spinner(this);
    languages.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"English", "Hindi"}));
    languages.setSelection(
        getSharedPreferences("subtitles", 0).getString("language", "en").equals("hi") ? 1 : 0);
    root.addView(languages);
    languages.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(android.widget.AdapterView<?> p) {}

          public void onItemSelected(android.widget.AdapterView<?> p, View v, int n, long id) {
            getSharedPreferences("subtitles", 0)
                .edit()
                .putString("language", n == 1 ? "hi" : "en")
                .apply();
          }
        });
    Button logout = new Button(this);
    logout.setText("Sign out");
    root.addView(logout);
    logout.setOnClickListener(
        v -> {
          login.setEnabled(false);
          worker.execute(
              () -> {
                try {
                  api.logout();
                  ui.post(
                      () -> {
                        if (!isDestroyed()) {
                          status.setText("Signed out");
                          login.setEnabled(true);
                        }
                      });
                } catch (Exception e) {
            Diagnostics.failure("subtitle.failed","subtitles","fetch",e);
                  ui.post(
                      () -> {
                        if (!isDestroyed()) {
                          status.setText("Could not clear subtitle session");
                          login.setEnabled(true);
                        }
                      });
                }
              });
        });
    Button done = new Button(this);
    done.setText("Done");
    root.addView(done);
    done.setOnClickListener(v -> finish());
    provider.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(android.widget.AdapterView<?> p) {}

          public void onItemSelected(android.widget.AdapterView<?> p, View v, int n, long id) {
            getSharedPreferences("subtitles", 0)
                .edit()
                .putString("provider", n == 0 ? "org" : "com")
                .apply();
            refreshProvider();
          }
        });
    refreshProvider();
  }

  private void refreshProvider() {
    boolean org = api.orgMode();
    key.setVisibility(org ? View.GONE : View.VISIBLE);
    create.setVisibility(org ? View.GONE : View.VISIBLE);
    guest.setVisibility(org ? View.VISIBLE : View.GONE);
    key.setText("");
    password.setText("");
    username.setHint(org ? "OpenSubtitles.org username" : "OpenSubtitles.com username");
    username.setText(
        getSharedPreferences("subtitles", 0).getString(org ? "username_org" : "username_com", ""));
    help.setText(
        org
            ? "Uses the installed desktop client's legacy .org login request format. Sign in with"
                  + " your .org username. Website login, subtitle search and subtitle download are"
                  + " separate checks. If account login fails, try Use without account. Anonymous"
                  + " access reconnects at launch and renews when needed; service limits still"
                  + " apply. Your password is not saved; sessions are stored encrypted."
            : "Create a consumer named StreamNest in your OpenSubtitles.com profile, paste its API"
                + " key here, then sign in with your .com account. The API key and session are"
                + " stored encrypted. Your password is not saved.");
    status.setText(
        org && api.signedIn() && new OrgSubtitles(this).anonymousMode()
            ? "Anonymous .org mode - reconnects automatically"
            : api.signedIn()
                ? "Signed in to OpenSubtitles." + (org ? "org" : "com")
                : "Not signed in");
    key.setHint(
        !org && api.configured()
            ? "API key saved — leave blank to keep it"
            : "Application API key");
  }

  private TextView label(String text) {
    TextView t = new TextView(this);
    t.setText(text);
    t.setTextColor(-1);
    return t;
  }

  private EditText field(LinearLayout root, String hint, boolean secret) {
    EditText input = new EditText(this);
    input.setTextColor(-1);
    input.setHintTextColor(0xffa8b5cb);
    input.setHint(hint);
    input.setSingleLine();
    input.setSaveEnabled(false);
    input.setInputType(
        secret
            ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    root.addView(input);
    return input;
  }

  private void signIn() {
    String enteredKey = key.getText().toString().trim(),
        user = username.getText().toString().trim(),
        pass = password.getText().toString();
    boolean org = api.orgMode();
    if (user.isEmpty() || pass.isEmpty()) {
      status.setText("Enter your username and password");
      return;
    }
    password.setText("");
    login.setEnabled(false);
    provider.setEnabled(false);
    status.setText("Signing in…");
    worker.execute(
        () -> {
          try {
            if (!org && !enteredKey.isEmpty()) api.saveKey(enteredKey);
            api.login(user, pass);
            getSharedPreferences("subtitles", 0)
                .edit()
                .putString(org ? "username_org" : "username_com", user)
                .apply();
            ui.post(
                () -> {
                  if (!isDestroyed()) {
                    key.setText("");
                    status.setText(
                        "Signed in — automatic subtitles enabled when available for your account");
                    login.setEnabled(true);
                    provider.setEnabled(true);
                  }
                });
          } catch (Exception e) {
            Diagnostics.failure("subtitle.failed","subtitles","fetch",e);
            ui.post(
                () -> {
                  if (!isDestroyed()) {
                    status.setText(e.getMessage());
                    login.setEnabled(true);
                    provider.setEnabled(true);
                  }
                });
          }
        });
  }

  protected void onDestroy() {
    worker.shutdownNow();
    ui.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
