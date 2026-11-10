package app.streamnest;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.videolan.libvlc.*;
import org.videolan.libvlc.util.VLCVideoLayout;

public class PlayerActivity extends Activity {
  LibVLC vlc;
  MediaPlayer player;
  VLCVideoLayout video;
  TextView status, time, playingTitle;
  org.json.JSONObject episodeQueue;
  List<org.json.JSONObject> episodeRows = new ArrayList<>();
  ImageButton previousEpisode, nextEpisode;
  Button skipSegment;
  SkipButtonBackground skipBackground;
  String skipKey = "";
  long skipRemaining = 5000, skipClock;
  long pendingSeek = -1;
  boolean skipCancelled, skipAutoEnabled;
  TorrentEngine cacheCommitted;
  List<SkipSegments.Segment> segments = new ArrayList<>();
  SkipSegments.Segment activeSegment;
  boolean changingEpisode, endedHandled, pendingAuto, skipRequested;
  int playbackVersion;
  long lastPosition, lastLength, lastProgressSave;
  long savedPlaybackPosition = -1;
  String savedPlaybackIdentity = "";
  final java.util.concurrent.ExecutorService episodeWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  SeekBar seek;
  Handler ui = new Handler(Looper.getMainLooper());
  boolean opened, dragging;
  boolean viewsAttached, resumeOnReturn;
  FrameLayout root;
  View topControls, bottomControls;
  boolean controlsVisible = true;
  final Runnable hideControls =
      () -> {
        if (player != null && player.isPlaying() && !dragging && hasWindowFocus())
          setControlsVisible(false);
      };
  String source, key, episodeMessage;
  ImageButton pause;
  TextView subtitleStatus;
  final java.util.concurrent.ExecutorService subtitleWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  boolean subtitleBusy;
  List<Catalog.Source> alternatives = new ArrayList<>();
  int attempt;
  long attemptStarted, resumeAfterSwitch, lastProgressAt, lastReceived;
  boolean mediaSet, playedVideo, sourceFailed, userPaused;
  int startupRetries;
  long mediaOpenedAt, startupPosition;
  boolean softwareDecoder;
  int videoOutputs;
  ImageButton nextSource;

  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (DeviceProfile.lite(this)) {
      synchronized (SeriesIndex.class) {
        SeriesIndex.sessions.clear();
        SeriesIndex.snapshot = null;
      }
    }
    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    source = getIntent().getStringExtra("source");
    if (source == null) {
      finish();
      return;
    }
    key = positionKey();
    WatchSync.request(this, true);
    alternatives = AlternateEpisodes.decode(getIntent().getStringExtra("alternatives"));
    attemptStarted = SystemClock.elapsedRealtime();
    lastProgressAt = attemptStarted;
    lastReceived = 0;
    root = new FrameLayout(this);
    root.setBackgroundColor(android.graphics.Color.BLACK);
    setContentView(root);
    immersive();
    video = new VLCVideoLayout(this);
    root.addView(video, new FrameLayout.LayoutParams(-1, -1));
    video.setFocusableInTouchMode(true);
    video.setOnClickListener(v -> setControlsVisible(!controlsVisible));
    LinearLayout top = new LinearLayout(this);
    top.setPadding(16, 12, 16, 12);
    top.setBackgroundColor(0xb3080c14);
    topControls = top;
    root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    LinearLayout bottom = new LinearLayout(this);
    bottom.setOrientation(LinearLayout.VERTICAL);
    bottom.setPadding(12, 8, 12, 8);
    bottom.setBackgroundColor(0xc0080c14);
    bottomControls = bottom;
    root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    TextView title = new TextView(this);
    playingTitle = title;
    title.setText(getIntent().getStringExtra("title"));
    title.setTextColor(-1);
    title.setTextSize(16);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    top.addView(title);
    status = new TextView(this);
    status.setTextColor(0xffa8b5cb);
    status.setText("Starting…");
    bottom.addView(status);
    time = new TextView(this);
    time.setTextColor(-1);
    bottom.addView(time);
    subtitleStatus = new TextView(this);
    subtitleStatus.setTextColor(0xff8fc8ff);
    bottom.addView(subtitleStatus);
    seek = new SeekBar(this);
    seek.setMax(10000);
    bottom.addView(seek);
    LinearLayout buttons = new LinearLayout(this);
    bottom.addView(buttons);
    previousEpisode =
        button(
            R.drawable.ic_previous_episode,
            "Previous episode",
            buttons,
            () -> changeEpisode(-1, false));
    previousEpisode.setVisibility(View.GONE);
    button(
        R.drawable.ic_rewind,
        "Back 10 seconds",
        buttons,
        () -> {
          if (player != null) seekTo(Math.max(0, player.getTime() - 10000));
        });
    pause =
        button(
            R.drawable.ic_pause,
            "Pause",
            buttons,
            () -> {
              if (player.isPlaying()) {
                userPaused = true;
                player.pause();
                pause.setImageResource(R.drawable.ic_play);
                pause.setContentDescription("Play");
              } else {
                userPaused = false;
                player.play();
                pause.setImageResource(R.drawable.ic_pause);
                pause.setContentDescription("Pause");
              }
            });
    button(
        R.drawable.ic_forward,
        "Forward 10 seconds",
        buttons,
        () -> {
          if (player != null)
            seekTo(Math.min(player.getLength(), player.getTime() + 10000));
        });
    nextEpisode =
        button(R.drawable.ic_next_episode, "Next episode", buttons, () -> changeEpisode(1, false));
    nextEpisode.setVisibility(View.GONE);
    skipSegment = new Button(this);
    skipSegment.setText("Skip Intro");
    skipSegment.setTextColor(-1);
    skipSegment.setAllCaps(false);
    skipSegment.setMinWidth((int)(200*getResources().getDisplayMetrics().density));
    skipSegment.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_skip_segment,0,0,0);
    skipSegment.setCompoundDrawablePadding((int)(8*getResources().getDisplayMetrics().density));
    skipSegment.setPadding((int)(16*getResources().getDisplayMetrics().density),8,
        (int)(16*getResources().getDisplayMetrics().density),14);
    skipBackground = new SkipButtonBackground(getResources().getDisplayMetrics().density);
    skipSegment.setBackground(skipBackground);
    int skipSize = (int) (56 * getResources().getDisplayMetrics().density);
    FrameLayout.LayoutParams skipParams =
        new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, skipSize, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    skipParams.rightMargin = 24;
    root.addView(skipSegment, skipParams);
    skipSegment.setVisibility(View.GONE);

    skipSegment.setOnClickListener(
        v -> {
          if (activeSegment != null) {
            seekTo(activeSegment.end);
            updateSegment(activeSegment.end);
          }
          setControlsVisible(true);
        });
    button(R.drawable.ic_subtitles, "Subtitles", buttons, this::subtitleMenu);
    nextSource =
        button(R.drawable.ic_retry, "Try another source", buttons, () -> switchSource(false));
    nextSource.setVisibility(alternatives.size() > 1 ? View.VISIBLE : View.GONE);
    button(R.drawable.ic_stop, "Stop", buttons, this::finish);
    vlc =
        new LibVLC(
            this,
            new ArrayList<>(
                Arrays.asList(
                    "--network-caching=" + (DeviceProfile.lite(this) ? 1000 : 2000),
                    "--http-reconnect")));
    player = new MediaPlayer(vlc);
    player.attachViews(video, null, true, true);
    viewsAttached = true;
    player.setEventListener(
        e -> {
          if (e.type == MediaPlayer.Event.Opening
              || e.type == MediaPlayer.Event.Playing
              || e.type == MediaPlayer.Event.EncounteredError)
            android.util.Log.d("StreamNest", "VLC event " + e.type);
          if (e.type == MediaPlayer.Event.Vout) videoOutputs = e.getVoutCount();
          if (e.type == MediaPlayer.Event.EncounteredError) {
            if (mediaSet) sourceFailed = true;
            setControlsVisible(true);
            status.setText("Playback failed. Stop and retry, or choose another source.");
          }
          if (e.type == MediaPlayer.Event.EndReached) {
            persistProgress(true);
            episodeEnded();
          }
          if (e.type == MediaPlayer.Event.TimeChanged && pendingSeek >= 0
              && Math.abs(player.getTime() - pendingSeek) < 2000) {
            pendingSeek = -1;
            persistProgress(false);
            WatchSync.request(this, true);
          }
          if (e.type == MediaPlayer.Event.Playing) scheduleHide();
          if (e.type == MediaPlayer.Event.Paused) { persistProgress(false); WatchSync.request(this, true); }
          if (e.type == MediaPlayer.Event.Paused || e.type == MediaPlayer.Event.EndReached)
            setControlsVisible(true);
          if (e.type == MediaPlayer.Event.Playing && !opened) {
            opened = true;
            if (getIntent().hasExtra("subtitlesEnabled")
                && !getIntent().getBooleanExtra("subtitlesEnabled", true)) player.setSpuTrack(-1);
            long p =
                resumeAfterSwitch > 0
                    ? resumeAfterSwitch
                    : getIntent().getBooleanExtra("startFromBeginning", false)
                        ? 0
                        : WatchProgress.read(
                            this,
                            WatchProgress.identity(getIntent(), key),
                            getPreferences(0).getLong(key, 0));
            startupPosition = p;
            if (p > 0) player.setTime(p);
            getIntent().putExtra("startFromBeginning", false);
            if (!getIntent().getBooleanExtra("embeddedFirst", false)
                && !(getIntent().getStringExtra("imdb") != null
                    && getIntent().getStringExtra("imdb").startsWith("nyaa:"))
                && getIntent()
                    .getBooleanExtra(
                        "subtitlesEnabled",
                        getSharedPreferences("subtitles", 0).getBoolean("auto", true))
                && new OpenSubtitles(this).signedIn()) findSubtitles(true);
          }
        });
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {
            dragging = true;
            ui.removeCallbacks(hideControls);
          }

          public void onStopTrackingTouch(SeekBar b) {
            if (player.getLength() > 0)
              seekTo(player.getLength() * b.getProgress() / 10000);
            dragging = false;
            scheduleHide();
          }

          public void onProgressChanged(SeekBar b, int p, boolean user) {}
        });
    loadEpisodeQueue();
    // Finish view attachment before starting Android's foreground-service deadline.
    ui.post(()->{if(!isFinishing()&&!isDestroyed())startStream();});
    ui.post(tick);
  }

  void seekTo(long position) {
    if (player == null) return;
    if (position < Math.max(lastLength, player.getLength()) - 10000) {
      endedHandled = false;
      pendingAuto = false;
    }
    pendingSeek = position;
    player.setTime(position);
  }

  void persistProgress(boolean completed) {
    if (player == null || !playedVideo || !completed && endedHandled) return;
    long duration = Math.max(lastLength, player.getLength()),
        position = completed ? duration : player.getTime();
    if (duration <= 0 || position < 0) return;
    String identity = WatchProgress.identity(getIntent(), key);
    if (!completed
        && identity.equals(savedPlaybackIdentity)
        && Math.abs(position - savedPlaybackPosition) < 1000) return;
    savedPlaybackIdentity = identity;
    savedPlaybackPosition = position;
    WatchProgress.save(this, identity, position, duration);
    WatchSync.request(this, true);
  }

  String positionKey() {
    String identity = getIntent().getStringExtra("episodeIdentity");
    return "position_" + source + (identity == null || identity.isEmpty() ? "" : "|" + identity);
  }

  void loadEpisodeQueue() {
    String token = getIntent().getStringExtra("episodeQueue");
    if (token == null) return;
    episodeWorker.execute(
        () -> {
          try {
            org.json.JSONObject doc = EpisodeQueue.read(this, token);
            List<org.json.JSONObject> rows = EpisodeQueue.rows(doc);
            ui.post(
                () -> {
                  if (isDestroyed()) return;
                  episodeQueue = doc;
                  episodeRows = rows;
                  updateEpisodeButtons();
                });
          } catch (Exception ignored) {
          }
        });
  }

  org.json.JSONObject neighbour(int direction) {
    return EpisodePlayback.neighbour(
        episodeRows,
        getIntent().getIntExtra("season", 0),
        getIntent().getIntExtra("episode", 0),
        direction);
  }

  void updateEpisodeButtons() {
    boolean episodic = episodeQueue != null;
    for (ImageButton b : new ImageButton[] {previousEpisode, nextEpisode})
      b.setVisibility(episodic ? View.VISIBLE : View.GONE);
    previousEpisode.setEnabled(!changingEpisode && neighbour(-1) != null);
    nextEpisode.setEnabled(!changingEpisode && neighbour(1) != null);
    previousEpisode.setAlpha(previousEpisode.isEnabled() ? 1f : 0.35f);
    nextEpisode.setAlpha(nextEpisode.isEnabled() ? 1f : 0.35f);
  }

  void episodeEnded() {
    long len = Math.max(lastLength, player.getLength()),
        position = Math.max(lastPosition, player.getTime());
    if (endedHandled || !EpisodePlayback.completed(position, len, playedVideo, userPaused)) return;
    endedHandled = true;
    getPreferences(0).edit().remove(key).apply();
    if (getSharedPreferences("playback", 0).getBoolean("autoNext", true)
        && episodeQueue != null
        && neighbour(1) != null) pendingAuto = true;
  }

  void changeEpisode(int direction, boolean automatic) {
    if (changingEpisode || isFinishing()) return;
    org.json.JSONObject row = neighbour(direction);
    if (row == null) return;
    changingEpisode = true;
    pendingAuto = false;
    updateEpisodeButtons();
    setControlsVisible(true);
    episodeMessage = "Finding " + (direction > 0 ? "next" : "previous") + " episode…";
    status.setText(episodeMessage);
    final int version = playbackVersion;
    episodeWorker.execute(
        () -> {
          try {
            List<Catalog.Source> prepared=EpisodePreload.choices(episodeQueue.optString("imdb")+":"+EpisodeLibrary.key(row));
            List<Catalog.Source> choices = prepared!=null?prepared:
                EpisodeQueue.resolve(episodeQueue, row, DeviceProfile.lite(this));
            ui.post(
                () -> {
                  if (isDestroyed() || version != playbackVersion) return;
                  getPreferences(0)
                      .edit()
                      .putLong(key, endedHandled ? 0 : Math.max(0, player.getTime()))
                      .apply();
                  ++playbackVersion;
                  player.stop();
                  Catalog.Source best = choices.get(0);
                  source = best.url;
                  key =
                      "position_"
                          + source
                          + "|"
                          + episodeQueue.optString("imdb")
                          + ":"
                          + EpisodeLibrary.key(row);
                  getIntent()
                      .putExtra("source", source)
                      .putExtra("alternatives", AlternateEpisodes.encode(choices))
                      .putExtra("fileIndex", best.fileIndex)
                      .putExtra("filename", best.filename)
                      .putExtra("season", row.optInt("season"))
                      .putExtra("episode", row.optInt("episode"))
                      .putExtra("absolute", row.optInt("absolute"))
                      .putExtra(
                          "episodeIdentity",
                          episodeQueue.optString("imdb") + ":" + EpisodeLibrary.key(row))
                      .putExtra("embeddedFirst", row.optBoolean("liveRelease"))
                      .putExtra("startFromBeginning", true);
                  String title =
                      episodeQueue.optString("title")
                          + " · S"
                          + row.optInt("season")
                          + " E"
                          + row.optInt("episode");
                  getIntent().putExtra("title", title);
                  playingTitle.setText(title);
                  getSharedPreferences("library", 0)
                      .edit()
                      .putString(
                          "episode_" + episodeQueue.optString("imdb"), EpisodeLibrary.key(row))
                      .apply();
                  alternatives = choices;
                  attempt = 0;
                  resumeAfterSwitch = 0;
                  lastPosition = 0;
                  lastLength = 0;
                  opened = false;
                  mediaSet = false;
                  startupRetries = 0;
                  videoOutputs = 0;
                  softwareDecoder = false;
                  playedVideo = false;
                  sourceFailed = false;
                  userPaused = false;
                  resumeOnReturn = false;
                  endedHandled = false;
                  pendingAuto = false;
                  skipRequested = false;
                  pendingSeek = -1;
                  subtitleBusy = false;
                  segments = new ArrayList<>();
                  activeSegment = null;
                  skipSegment.setVisibility(View.GONE);
                  attemptStarted = lastProgressAt = SystemClock.elapsedRealtime();
                  lastReceived = 0;
                  changingEpisode = false;
                  episodeMessage = null;
                  pause.setImageResource(R.drawable.ic_pause);
                  pause.setContentDescription("Pause");
                  nextSource.setVisibility(choices.size() > 1 ? View.VISIBLE : View.GONE);
                  subtitleStatus.setText("");
                  time.setText("");
                  seek.setProgress(0);
                  updateEpisodeButtons();
                  startStream();
                });
          } catch (Exception error) {
            ui.post(
                () -> {
                  if (isDestroyed() || version != playbackVersion) return;
                  changingEpisode = false;
                  updateEpisodeButtons();
                  episodeMessage = error.getMessage();
                  status.setText(episodeMessage);
                  if (!automatic)
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
          }
        });
  }

  void requestSegments(long length) {
    if (skipRequested
        || length <= 0
        || episodeQueue == null
        || !getSharedPreferences("playback", 0).getBoolean("skipButtons", true)) return;
    skipRequested = true;
    int version = playbackVersion;
    String imdb = getIntent().getStringExtra("imdb");
    int season = getIntent().getIntExtra("season", 0),
        episode = getIntent().getIntExtra("episode", 0),
        mal =
            episodeRows.stream()
                .filter(e -> e.optInt("season") == season && e.optInt("episode") == episode)
                .mapToInt(e -> e.optInt("skipMalId"))
                .findFirst()
                .orElse(0);
    episodeWorker.execute(
        () -> {
          List<SkipSegments.Segment> found =
              SkipSegments.load(imdb == null ? "" : imdb, season, episode, mal, length);
          ui.post(
              () -> {
                if (!isDestroyed() && version == playbackVersion) segments = found;
              });
        });
  }

  void updateSegment(long position) {
    activeSegment = SkipSegments.active(segments, position);
    String current = activeSegment == null ? "" :
        activeSegment.type + ":" + activeSegment.start + ":" + activeSegment.end;
    long now = SystemClock.elapsedRealtime();
    boolean changed = !current.equals(skipKey);
    if (changed) {
      skipKey = current;
      skipRemaining = 5000;
      skipClock = now;
      skipCancelled = false;
    }
    boolean visible = activeSegment != null && !changingEpisode;
    boolean enabled = getSharedPreferences("playback", 0).getBoolean("autoSkip", true);
    if (enabled && !skipAutoEnabled) {
      skipRemaining = 5000;
      skipClock = now;
      skipCancelled = false;
      changed = true;
    }
    skipAutoEnabled = enabled;
    boolean auto = enabled && !skipCancelled;
    boolean running = player != null && player.isPlaying() && viewsAttached && hasWindowFocus() && !dragging;
    if (visible && auto && running)
      skipRemaining -= Math.min(1000, Math.max(0, now - (skipClock == 0 ? now : skipClock)));
    skipClock = now;
    skipSegment.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (visible && auto && !running) skipBackground.freeze(changed);
    else skipBackground.progress((5000-skipRemaining+(running ? 500 : 0))/5000f,visible && auto,changed);
    if (visible) {
      String label = activeSegment.type.equals("intro") ? "Skip Intro" : "Skip Credits";
      skipSegment.setText(label + (auto ? "  " + Math.max(1, (skipRemaining + 999) / 1000) + "s" : ""));
      skipSegment.setContentDescription(label);
      if (auto && skipRemaining <= 0) {
        skipCancelled = true;
        seekTo(activeSegment.end);
      }
    }
  }

  void startStream() {
    startForegroundService(
        new Intent(this, StreamService.class)
            .putExtra("source", source)
            .putExtra("episodeIdentity", getIntent().getStringExtra("episodeIdentity"))
            .putExtra("fileIndex", getIntent().getIntExtra("fileIndex", -1))
            .putExtra("filename", getIntent().getStringExtra("filename"))
            .putExtra("season", getIntent().getIntExtra("season", 0))
            .putExtra("episode", getIntent().getIntExtra("episode", 0))
            .putExtra("absolute", getIntent().getIntExtra("absolute", 0))
            .putExtra(
                "catalogSource",
                getIntent().getStringExtra("imdb") != null
                    && getIntent().getStringExtra("imdb").matches("tt[0-9]+")));
  }

  void switchSource(boolean automatic) {
    if (changingEpisode) return;
    if (attempt + 1 >= alternatives.size()) {
      if (!automatic)
        Toast.makeText(
                this,
                "No more sources at this quality. Return to episode details to change quality.",
                Toast.LENGTH_LONG)
            .show();
      return;
    }
    resumeAfterSwitch = Math.max(resumeAfterSwitch, Math.max(0, player.getTime()));
    TorrentEngine old = StreamService.engine;
    if (old != null) old.closed = true;
    player.stop();
    Catalog.Source next = alternatives.get(++attempt);
    source = next.url;
    key = positionKey();
    getIntent()
        .putExtra("source", source)
        .putExtra("fileIndex", next.fileIndex)
        .putExtra("filename", next.filename);
    ++playbackVersion;
    skipRequested = false;
    segments = new ArrayList<>();
    activeSegment = null;
    skipSegment.setVisibility(View.GONE);
    subtitleBusy = false;
    opened = false;
    mediaSet = false;
    startupRetries = 0;
    videoOutputs = 0;
    softwareDecoder = false;
    playedVideo = false;
    sourceFailed = false;
    userPaused = false;
    attemptStarted = SystemClock.elapsedRealtime();
    lastProgressAt = attemptStarted;
    lastReceived = 0;
    status.setText(
        "Trying source " + (attempt + 1) + " of " + alternatives.size() + " · " + next.label);
    subtitleStatus.setText("");
    pause.setImageResource(R.drawable.ic_pause);
    pause.setContentDescription("Pause");
    setControlsVisible(true);
    startStream();
  }

  void immersive() {
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
  }

  void scheduleHide() {
    ui.removeCallbacks(hideControls);
    ui.postDelayed(hideControls, 3500);
  }

  void setControlsVisible(boolean visible) {
    controlsVisible = visible;
    topControls.setVisibility(visible ? View.VISIBLE : View.GONE);
    bottomControls.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (visible) scheduleHide();
    else {
      ui.removeCallbacks(hideControls);
      video.requestFocus();
      immersive();
    }
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN && controlsVisible) scheduleHide();
    return super.dispatchTouchEvent(event);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    int key = event.getKeyCode();
    boolean navigation =
        key == KeyEvent.KEYCODE_DPAD_UP
            || key == KeyEvent.KEYCODE_DPAD_DOWN
            || key == KeyEvent.KEYCODE_DPAD_LEFT
            || key == KeyEvent.KEYCODE_DPAD_RIGHT
            || key == KeyEvent.KEYCODE_DPAD_CENTER
            || key == KeyEvent.KEYCODE_ENTER;
    if (event.getAction() == KeyEvent.ACTION_DOWN && navigation) {
      if (!controlsVisible) {
        setControlsVisible(true);
        pause.requestFocus();
        return true;
      }
      scheduleHide();
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (focused && topControls != null) {
      immersive();
      scheduleHide();
    }
  }

  ImageButton button(int icon, String description, LinearLayout row, Runnable run) {
    ImageButton b = new ImageButton(this);
    b.setImageResource(icon);
    b.setContentDescription(description);
    b.setTooltipText(description);
    android.graphics.drawable.StateListDrawable bg =
        new android.graphics.drawable.StateListDrawable();
    bg.addState(
        new int[] {android.R.attr.state_focused},
        new android.graphics.drawable.ColorDrawable(0xff2389cf));
    bg.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(0xff172338));
    b.setBackground(bg);
    int height = (int) (52 * getResources().getDisplayMetrics().density);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, 1);
    params.setMargins(6, 4, 6, 4);
    row.addView(b, params);
    b.setOnClickListener(
        v -> {
          setControlsVisible(true);
          run.run();
          scheduleHide();
        });
    return b;
  }

  void subtitleMenu() {
    setControlsVisible(true);
    new AlertDialog.Builder(this)
        .setTitle("Subtitles")
        .setItems(
            new String[] {
              "Find on OpenSubtitles",
              "Choose a local file",
              "Language: English / Hindi",
              "OpenSubtitles account",
              "Turn subtitles off"
            },
            (d, n) -> {
              if (n == 0) findSubtitles(false);
              else if (n == 1)
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    30);
              else if (n == 2)
                new AlertDialog.Builder(this)
                    .setTitle("Subtitle language")
                    .setItems(
                        new String[] {"English", "Hindi"},
                        (x, i) -> {
                          getSharedPreferences("subtitles", 0)
                              .edit()
                              .putString("language", i == 0 ? "en" : "hi")
                              .apply();
                          getIntent().putExtra("subtitleLanguage", i == 0 ? "en" : "hi");
                          findSubtitles(false);
                        })
                    .show();
              else if (n == 3) startActivity(new Intent(this, SubtitleSettingsActivity.class));
              else {
                player.setSpuTrack(-1);
                subtitleStatus.setText("Subtitles off");
              }
            })
        .show();
  }

  void findSubtitles(boolean automatic) {
    if (subtitleBusy) return;
    final int requestAttempt = attempt;
    final int requestVersion = playbackVersion;
    OpenSubtitles api = new OpenSubtitles(this);
    if (!api.configured()) {
      if (!automatic) startActivity(new Intent(this, SubtitleSettingsActivity.class));
      return;
    }
    subtitleBusy = true;
    subtitleStatus.setText("Finding subtitles…");
    String imdb = getIntent().getStringExtra("imdb"),
        title = getIntent().getStringExtra("title"),
        language =
            getIntent().getStringExtra("subtitleLanguage") == null
                ? getSharedPreferences("subtitles", 0).getString("language", "en")
                : getIntent().getStringExtra("subtitleLanguage");
    int season = getIntent().getIntExtra("season", 0),
        episode = getIntent().getIntExtra("episode", 0);
    subtitleWorker.execute(
        () -> {
          try {
            TorrentEngine engine = StreamService.engine;
            String hash = engine == null ? "" : engine.movieHash();
            java.util.List<SubtitleQuery.Result> results =
                api.search(
                    imdb, season, episode, title, language, hash, engine == null ? 0 : engine.size);
            ui.post(
                () -> {
                  subtitleBusy = false;
                  if (isDestroyed()
                      || (attempt != requestAttempt || playbackVersion != requestVersion)) return;
                  if (results.isEmpty()) {
                    subtitleStatus.setText(
                        "No " + (language.equals("hi") ? "Hindi" : "English") + " subtitles found");
                    return;
                  }
                  if (automatic) downloadSubtitle(results.get(0));
                  else {
                    String[] labels = new String[results.size()];
                    for (int i = 0; i < labels.length; i++) labels[i] = results.get(i).label;
                    new AlertDialog.Builder(this)
                        .setTitle("Choose subtitles")
                        .setItems(labels, (d, n) -> downloadSubtitle(results.get(n)))
                        .setNegativeButton("Cancel", null)
                        .show();
                  }
                });
          } catch (Exception e) {
            ui.post(
                () -> {
                  subtitleBusy = false;
                  if (!isDestroyed()
                      && attempt == requestAttempt
                      && playbackVersion == requestVersion) subtitleStatus.setText(e.getMessage());
                });
          }
        });
  }

  void downloadSubtitle(SubtitleQuery.Result result) {
    final int requestAttempt = attempt;
    final int requestVersion = playbackVersion;
    subtitleStatus.setText("Downloading subtitles…");
    subtitleWorker.execute(
        () -> {
          try {
            File existing =
                new File(
                    getCacheDir(),
                    "opensubtitles-"
                        + (result.provider.equals("org") ? "org-" : "")
                        + result.fileId
                        + ".srt");
            File file =
                existing.exists()
                    ? existing
                    : new OpenSubtitles(this).download(result, getCacheDir());
            ui.post(
                () -> {
                  if (!isDestroyed()
                      && attempt == requestAttempt
                      && playbackVersion == requestVersion) {
                    player.addSlave(Media.Slave.Type.Subtitle, Uri.fromFile(file), true);
                    subtitleStatus.setText("Subtitles added: " + result.label);
                  }
                });
          } catch (Exception e) {
            ui.post(
                () -> {
                  if (!isDestroyed()
                      && attempt == requestAttempt
                      && playbackVersion == requestVersion) subtitleStatus.setText(e.getMessage());
                });
          }
        });
  }

  final Runnable tick =
      new Runnable() {

        public void run() {
          if (isFinishing()) return;
          TorrentEngine e = StreamService.engine;
          if (mediaSet && videoOutputs > 0 && player.isPlaying() && player.getTime() > 1000)
            playedVideo = true;
          if(playedVideo&&e!=null&&!e.closed){
            if(cacheCommitted!=e&&StreamService.retiring==null){cacheCommitted=e;StreamService.playing(getApplicationContext(),e,getIntent().getStringExtra("episodeIdentity"));}
            EpisodePreload.consider(PlayerActivity.this,e,episodeQueue,neighbour(1));
          }
          long now = SystemClock.elapsedRealtime();
          if (mediaSet
              && mediaOpenedAt > 0
              && !playedVideo
              && !userPaused
              && hasWindowFocus()
              && StartupRecovery.reopen(now, mediaOpenedAt, startupRetries, sourceFailed)) {
            resumeAfterSwitch = startupPosition;
            if (startupPosition == 0) getIntent().putExtra("startFromBeginning", true);
            player.stop();
            videoOutputs = 0;
            ++playbackVersion;
            opened = false;
            subtitleBusy = false;
            mediaSet = false;
            sourceFailed = false;
            softwareDecoder = ++startupRetries == 2;
            lastProgressAt = now;
          }
          if (e != null && source.equals(e.sourceUrl) && e.receivedBytes > lastReceived) {
            lastReceived = e.receivedBytes;
            lastProgressAt = SystemClock.elapsedRealtime();
          }
          if (!playedVideo
              && !userPaused
              && alternatives.size() > attempt + 1
              && (!mediaSet || startupRetries >= 2)
              && hasWindowFocus()
              && EpisodeRecovery.retry(
                  SystemClock.elapsedRealtime(), attemptStarted, lastProgressAt, sourceFailed)) {
            switchSource(true);
            ui.postDelayed(this, 1000);
            return;
          }
          if (!playedVideo
              && mediaSet
              && startupRetries >= 2
              && alternatives.size() <= attempt + 1
              && (sourceFailed || now - mediaOpenedAt >= 45000)) {
            episodeMessage = "Video could not start. Go back and choose another quality or source.";
            setControlsVisible(true);
          } else if (playedVideo
              && episodeMessage != null
              && episodeMessage.startsWith("Video could not start")) {
            episodeMessage = null;
          }
          if (e != null
              && source.equals(e.sourceUrl)
              && e.fileIndex == getIntent().getIntExtra("fileIndex", -1)) {
            status.setText(
                episodeMessage != null
                    ? episodeMessage
                    : (alternatives.size() > 1
                            ? "Source " + (attempt + 1) + "/" + alternatives.size() + " · "
                            : "")
                        + e.status());
            if (e.url != null && !mediaSet && viewsAttached && hasWindowFocus()) {
              Media media = new Media(vlc, Uri.parse(e.url));
              media.addOption(":http-proxy=");
              if (DeviceProfile.lite(PlayerActivity.this)) media.addOption(":avcodec-threads=2");
              media.addOption(":http-reconnect");
              media.setHWDecoderEnabled(!softwareDecoder, false);
              player.setMedia(media);
              media.release();
              player.play();
              mediaSet = true;
              mediaOpenedAt = SystemClock.elapsedRealtime();
            }
          }
          long len = player.getLength(), p = player.getTime();
          if (len > 0) lastLength = len;
          if (p >= 0) lastPosition = p;
          if (playedVideo
              && player.isPlaying()
              && SystemClock.elapsedRealtime() - lastProgressSave >= 10000) {
            persistProgress(false);
            lastProgressSave = SystemClock.elapsedRealtime();
          }
          if (mediaSet && playedVideo) requestSegments(len);
          updateSegment(p);
          if (pendingAuto
              && !userPaused
              && getSharedPreferences("playback", 0).getBoolean("autoNext", true)
              && hasWindowFocus()
              && viewsAttached
              && !changingEpisode) changeEpisode(1, true);
          if (len > 0) {
            if (!dragging) seek.setProgress((int) (p * 10000 / len));
            time.setText(clock(p) + " / " + clock(len));
          }
          ui.postDelayed(this, 1000);
        }
      };

  public boolean onKeyDown(int code, KeyEvent event) {
    if (event.getRepeatCount() == 0
        && (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_SPACE)) {
      pause.performClick();
      return true;
    }
    if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
      if (event.getRepeatCount() == 0)
        changeEpisode(code == KeyEvent.KEYCODE_MEDIA_NEXT ? 1 : -1, false);
      return true;
    }
    if (code == KeyEvent.KEYCODE_MEDIA_STOP) {
      finish();
      return true;
    }
    if (code == KeyEvent.KEYCODE_MEDIA_REWIND || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
      player.setTime(
          Math.max(0, player.getTime() + (code == KeyEvent.KEYCODE_MEDIA_REWIND ? -10000 : 10000)));
      return true;
    }
    return super.onKeyDown(code, event);
  }

  static String clock(long t) {
    long s = Math.max(0, t / 1000);
    return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 30 && result == RESULT_OK && data != null) {
      try {
        File f = new File(getCacheDir(), "selected-subtitles.srt");
        try (InputStream in = getContentResolver().openInputStream(data.getData());
            FileOutputStream out = new FileOutputStream(f)) {
          if (in == null) throw new IOException("Cannot open subtitles");
          byte[] b = new byte[8192];
          int n;
          long total = 0;
          while ((n = in.read(b)) != -1) {
            total += n;
            if (total > 10 * 1024 * 1024) throw new IOException("Subtitle file too large");
            out.write(b, 0, n);
          }
        }
        player.addSlave(Media.Slave.Type.Subtitle, Uri.fromFile(f), true);
      } catch (Exception e) {
        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
      }
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (player != null && !viewsAttached) {
      player.attachViews(video, null, true, true);
      viewsAttached = true;
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (player != null && resumeOnReturn) {
      resumeOnReturn = false;
      player.play();
      pause.setImageResource(R.drawable.ic_pause);
      pause.setContentDescription("Pause");
    }
  }

  @Override
  protected void onStop() {
    persistProgress(false);
    WatchSync.request(this, true);
    if (player != null) {
      resumeOnReturn = !isFinishing() && player.isPlaying() && !userPaused;
      if (player.isPlaying()) player.pause();
      if (viewsAttached) {
        player.detachViews();
        viewsAttached = false;
      }
    }
    super.onStop();
  }

  protected void onDestroy() {
    TorrentEngine activeEngine = StreamService.engine;
    if (activeEngine != null) activeEngine.closed = true;
    stopService(new Intent(this, StreamService.class));
    ++playbackVersion;
    episodeWorker.shutdownNow();
    subtitleWorker.shutdownNow();
    ui.removeCallbacksAndMessages(null);
    if (player != null) {
      persistProgress(false);
      getPreferences(0).edit().putLong(key, Math.max(0, player.getTime())).apply();
      player.stop();
      if (viewsAttached) player.detachViews();
      player.release();
    }
    if (vlc != null) vlc.release();
    stopService(new Intent(this, StreamService.class));
    super.onDestroy();
  }
}
