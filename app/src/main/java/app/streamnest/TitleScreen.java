package app.streamnest;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Popcorn Time's detail, selection and watch controls adapted to native mobile views. */
final class TitleScreen extends Dialog {
  final MainActivity a;
  final Catalog.Title title;
  final boolean series;
  LinearLayout root, qualityRow, episodePanel;
  TextView heading, overview, airdate, health;
  Button watch, favorite, subtitle;
  final Map<String, Button> qualityButtons = new LinkedHashMap<>();
  List<Catalog.Source> sources = new ArrayList<>();
  String selectedQuality = "", subtitleLanguage;
  boolean subtitlesEnabled;
  List<JSONObject> episodes = new ArrayList<>(), visible = new ArrayList<>();
  JSONObject selected;
  ListView seasonsView, episodesView;
  Spinner seasonSpinner;
  List<Integer> seasons = new ArrayList<>();
  int activeSeason = -1;
  BaseAdapter episodeAdapter;
  boolean loaded, lookingForAlternatives;
  Spinner episodeQualityPicker;
  java.util.concurrent.Future<?> alternateJob;

  boolean episodeMode() {
    return series || title.raw.optBoolean("animeRelease");
  }

  Catalog.Source chosenSource() {
    return episodeMode()
        ? PlaybackPlan.episodeBest(sources, selectedQuality, DeviceProfile.lite(a))
        : PlaybackPlan.best(sources, selectedQuality, DeviceProfile.lite(a));
  }

  TitleScreen(MainActivity activity, Catalog.Title t) {
    super(activity, android.R.style.Theme_Material_NoActionBar);
    a = activity;
    title = t;
    series = !t.kind.equals("movies");
    if (episodeMode()) selectedQuality = "Auto";
    subtitleLanguage = a.getSharedPreferences("subtitles", 0).getString("language", "en");
    subtitlesEnabled = a.getSharedPreferences("subtitles", 0).getBoolean("auto", true);
  }

  int dp(int x) {
    return a.dp(x);
  }

  TextView text(String s, int size, int color) {
    return a.text(s, size, color);
  }

  LinearLayout column() {
    LinearLayout l = new LinearLayout(a);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  Button control(String label, LinearLayout parent, Runnable action) {
    return a.button(label, parent, action);
  }

  @Override
  protected void onCreate(android.os.Bundle state) {
    super.onCreate(state);
    root = column();
    root.setPadding(dp(20), dp(12), dp(20), dp(14));
    root.setBackgroundColor(0xff17181b);
    setContentView(root);
    LinearLayout top = new LinearLayout(a);
    top.setGravity(Gravity.CENTER_VERTICAL);
    TextView brand = text("StreamNest", 15, 0xff92959c);
    top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton close = new ImageButton(a);
    close.setImageResource(R.drawable.ic_close);
    close.setContentDescription("Close details");
    close.setBackground(a.focusBackground());
    top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
    close.setOnClickListener(v -> dismiss());
    root.addView(top);
    boolean wide = a.getResources().getConfiguration().screenWidthDp >= 700;
    LinearLayout hero = new LinearLayout(a);
    hero.setPadding(0, dp(12), 0, dp(16));
    ImageView poster = new ImageView(a);
    poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
    int height = series ? 160 : wide ? 290 : 190;
    hero.addView(
        poster, new LinearLayout.LayoutParams(dp(series ? 105 : wide ? 190 : 125), dp(height)));
    a.posters.load(title.poster, poster);
    LinearLayout info = column();
    info.setPadding(dp(20), 0, 0, 0);
    TextView name = text(title.title, series ? 25 : 30, -1);
    name.setTypeface(null, Typeface.BOLD);
    info.addView(name);
    String meta = title.year;
    int runtime = title.raw.optInt("runtime");
    if (runtime > 0) meta += " · " + runtime + " min";
    double rating = title.raw.optDouble("rating", 0);
    if (rating > 0) meta += " · ★ " + rating;
    info.addView(text(meta, 13, 0xffbfc2c9));
    TextView synopsis = text(title.synopsis, 14, 0xffbfc2c9);
    synopsis.setPadding(0, dp(12), 0, 0);
    if (series) synopsis.setMaxLines(4);
    ScrollView description = new ScrollView(a);
    description.addView(synopsis);
    info.addView(description, new LinearLayout.LayoutParams(-1, 0, 1));
    hero.addView(info, new LinearLayout.LayoutParams(0, dp(height), 1));
    root.addView(hero);
    if (series) buildEpisodes(wide);
    else {
      LinearLayout spacer = column();
      root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
      heading = text("", 19, -1);
      overview = text("", 14, 0xffbfc2c9);
      airdate = text("", 12, 0xff92959c);
    }
    LinearLayout toggles = new LinearLayout(a);
    favorite =
        control(
            a.prefs.contains(savedKey()) ? "★ Bookmarked" : "☆ Bookmark",
            toggles,
            this::toggleFavorite);
    subtitle = control(subtitleLabel(), toggles, this::chooseSubtitles);
    root.addView(toggles);
    if (!series) buildControls(root);
    watch.setEnabled(false);
    health.setText(series ? "Loading seasons and episodes…" : "Loading available qualities…");
    if (series) loadEpisodes();
    else loadMovie();
  }

  @Override
  public void show() {
    super.show();
    getWindow().setLayout(-1, -1);
  }

  String savedKey() {
    return "saved_" + title.kind + "_" + title.id;
  }

  void toggleFavorite() {
    try {
      if (a.prefs.contains(savedKey())) a.prefs.edit().remove(savedKey()).apply();
      else
        a.prefs
            .edit()
            .putString(
                savedKey(),
                new JSONObject().put("kind", title.kind).put("title", title.raw).toString())
            .apply();
      favorite.setText(a.prefs.contains(savedKey()) ? "★ Bookmarked" : "☆ Bookmark");
    } catch (Exception e) {
      a.toast(e);
    }
  }

  String subtitleLabel() {
    return title.raw.optBoolean("animeRelease")
        ? "Subtitles · Embedded"
        : "Subtitles · "
            + (subtitlesEnabled ? (subtitleLanguage.equals("hi") ? "Hindi" : "English") : "Off")
            + " ▾";
  }

  void chooseSubtitles() {
    if (title.raw.optBoolean("animeRelease")) {
      new AlertDialog.Builder(a)
          .setMessage("Embedded subtitles can be selected in the player.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    new AlertDialog.Builder(a)
        .setTitle("Subtitles")
        .setSingleChoiceItems(
            new String[] {"English", "Hindi", "Off"},
            subtitlesEnabled ? (subtitleLanguage.equals("hi") ? 1 : 0) : 2,
            (d, n) -> {
              subtitlesEnabled = n < 2;
              subtitleLanguage = n == 1 ? "hi" : "en";
              subtitle.setText(subtitleLabel());
              d.dismiss();
            })
        .show();
  }

  void buildControls(LinearLayout parent) {
    health = text("", 12, 0xff92959c);
    health.setPadding(0, dp(8), 0, dp(8));
    parent.addView(health);
    LinearLayout controls = new LinearLayout(a);
    controls.setGravity(Gravity.CENTER_VERTICAL);
    qualityRow = new LinearLayout(a);
    qualityRow.setGravity(Gravity.CENTER_VERTICAL);
    if (!series) {
      watch = control("▶  Watch Now", controls, this::watch);
      watch.setTextSize(16);
      watch.setBackgroundTintList(
          new android.content.res.ColorStateList(
              new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
              new int[] {0xff292b30, 0xff168bcd}));
      controls.addView(qualityRow, new LinearLayout.LayoutParams(0, -2, 1));
    } else controls.addView(qualityRow, new LinearLayout.LayoutParams(-1, -2));
    if (episodeMode()) {
      episodeQualityPicker = new Spinner(a);
      episodeQualityPicker.setContentDescription("Episode quality");
      qualityRow.addView(episodeQualityPicker, new LinearLayout.LayoutParams(-1, dp(48)));
      episodeQualityPicker.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
              String value = String.valueOf(parent.getItemAtPosition(position));
              if (!value.equals(selectedQuality)) {
                selectedQuality = value;
                updateControls();
              }
            }

            public void onNothingSelected(AdapterView<?> parent) {}
          });
    } else
      for (String quality : new String[] {"1080p", "2160p"}) {
        Button b =
            control(
                quality,
                qualityRow,
                () -> {
                  selectedQuality = quality;
                  updateControls();
                });
        b.setContentDescription("Quality " + quality);
        qualityButtons.put(quality, b);
      }
    parent.addView(controls);
    if (series) {
      LinearLayout playRow = new LinearLayout(a);
      watch = control("▶  Watch Now", playRow, this::watch);
      watch.setTextSize(17);
      watch.setBackgroundTintList(
          new android.content.res.ColorStateList(
              new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
              new int[] {0xff292b30, 0xff168bcd}));
      parent.addView(playRow);
    }
  }

  void updateControls() {
    List<String> available =
        episodeMode() ? PlaybackPlan.episodeQualities(sources) : PlaybackPlan.qualities(sources);
    if (episodeMode()) {
      List<String> choices = new ArrayList<>();
      choices.add("Auto");
      choices.addAll(available);
      if (!choices.contains(selectedQuality)) selectedQuality = "Auto";
      List<String> current = new ArrayList<>();
      if (episodeQualityPicker.getAdapter() != null)
        for (int n = 0; n < episodeQualityPicker.getAdapter().getCount(); n++)
          current.add(String.valueOf(episodeQualityPicker.getAdapter().getItem(n)));
      if (!current.equals(choices)) {
        episodeQualityPicker.setAdapter(
            new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, choices));
      }
      if (episodeQualityPicker.getSelectedItemPosition() != choices.indexOf(selectedQuality))
        episodeQualityPicker.setSelection(choices.indexOf(selectedQuality));
    }
    if (selectedQuality.isEmpty()) selectedQuality = PlaybackPlan.preferred(sources, a.quality);
    for (Map.Entry<String, Button> q : qualityButtons.entrySet()) {
      q.getValue().setEnabled(loaded && available.contains(q.getKey()));
      q.getValue().setSelected(q.getKey().equals(selectedQuality));
    }
    Catalog.Source best = chosenSource();
    watch.setEnabled(loaded && best != null);
    health.setText(
        !loaded
            ? "Loading available qualities…"
            : best == null
                ? (episodeMode()
                    ? (lookingForAlternatives
                        ? "Looking for alternative episode sources…"
                        : "No playable source listed. Tap to retry alternatives.")
                    : "No 1080p or 4K source is currently listed.")
                : (episodeMode() && selectedQuality.equals("Auto") ? "Auto → " : "")
                    + best.seeds
                    + " reported seeds · "
                    + best.label);
  }

  void loadMovie() {
    sources = new ArrayList<>(title.sources);
    loaded = !sources.isEmpty();
    updateControls();
    a.work.execute(
        () -> {
          try {
            List<Catalog.Source> full =
                title.raw.optBoolean("animeRelease")
                    ? PlaybackPlan.sources(title.raw)
                    : PlaybackPlan.sources(
                        Catalog.details(
                            a.prefs.getString("server_" + title.kind, "https://api.apiabcd.com/"),
                            title));
            a.ui.post(
                () -> {
                  if (!isShowing() || a.isDestroyed()) return;
                  if (!full.isEmpty()) sources = full;
                  loaded = true;
                  updateControls();
                });
          } catch (Exception e) {
            a.ui.post(
                () -> {
                  if (!isShowing() || a.isDestroyed()) return;
                  loaded = true;
                  updateControls();
                  if (sources.isEmpty())
                    health.setText("Could not load qualities. Tap here to retry.");
                  health.setOnClickListener(v -> loadMovie());
                });
          }
        });
  }

  void buildEpisodes(boolean wide) {
    LinearLayout workspace = new LinearLayout(a);
    workspace.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout seasonColumn = column();
    seasonColumn.addView(text("SEASONS", 12, 0xff92959c));
    if (wide) {
      seasonsView = new ListView(a);
      seasonsView.setSelector(a.focusBackground());
      seasonColumn.addView(seasonsView, new LinearLayout.LayoutParams(-1, 0, 1));
      workspace.addView(seasonColumn, new LinearLayout.LayoutParams(dp(100), -1));
    } else {
      seasonSpinner = new Spinner(a);
      seasonColumn.addView(seasonSpinner);
      workspace.addView(seasonColumn);
    }
    LinearLayout listColumn = column();
    listColumn.setPadding(dp(wide ? 12 : 0), 0, dp(wide ? 16 : 0), 0);
    listColumn.addView(text("EPISODES", 12, 0xff92959c));
    episodesView = new ListView(a);
    episodesView.setSelector(a.focusBackground());
    listColumn.addView(episodesView, new LinearLayout.LayoutParams(-1, 0, 1));
    workspace.addView(
        listColumn,
        wide
            ? new LinearLayout.LayoutParams(dp(240), -1)
            : new LinearLayout.LayoutParams(-1, 0, 1));
    episodePanel = column();
    heading = text("Select an episode", 20, -1);
    heading.setTypeface(null, Typeface.BOLD);
    episodePanel.addView(heading);
    airdate = text("", 12, 0xff92959c);
    episodePanel.addView(airdate);
    overview = text("", 14, 0xffbfc2c9);
    overview.setPadding(0, dp(12), 0, dp(12));
    ScrollView scroll = new ScrollView(a);
    scroll.addView(overview);
    episodePanel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    buildControls(episodePanel);
    workspace.addView(
        episodePanel,
        wide
            ? new LinearLayout.LayoutParams(0, -1, 1)
            : new LinearLayout.LayoutParams(-1, dp(240)));
    episodeAdapter =
        new BaseAdapter() {
          public int getCount() {
            return visible.size();
          }

          public Object getItem(int p) {
            return visible.get(p);
          }

          public long getItemId(int p) {
            return p;
          }

          public View getView(int p, View recycled, ViewGroup parent) {
            JSONObject ep = visible.get(p);
            TextView row = recycled instanceof TextView ? (TextView) recycled : text("", 14, -1);
            row.setPadding(dp(8), dp(12), dp(8), dp(12));
            row.setMinHeight(dp(58));
            row.setMaxLines(3);
            row.setText(
                (ep.optInt("episode") > 0
                        ? String.format(Locale.ROOT, "%02d   ", ep.optInt("episode"))
                        : "•   ")
                    + ep.optString("title", "Episode"));
            row.setBackground(a.focusBackground());
            row.setSelected(ep == selected);
            return row;
          }
        };
    episodesView.setAdapter(episodeAdapter);
    episodesView.setOnItemClickListener((p, v, n, id) -> selectEpisode(visible.get(n)));
  }

  void loadEpisodes() {
    a.work.execute(
        () -> {
          try {
            List<JSONObject> result =
                EpisodeLibrary.load(
                    a.prefs.getString("server_shows", "https://api.apiabcd.com/"), title);
            a.ui.post(
                () -> {
                  if (!isShowing() || a.isDestroyed()) return;
                  episodes = result;
                  loaded = true;
                  populateSeasons();
                });
          } catch (Exception e) {
            a.ui.post(
                () -> {
                  if (!isShowing() || a.isDestroyed()) return;
                  health.setText("Could not load episodes. Tap here to retry.");
                  health.setOnClickListener(v -> loadEpisodes());
                });
          }
        });
  }

  void populateSeasons() {
    LinkedHashSet<Integer> ids = new LinkedHashSet<>();
    for (JSONObject ep : episodes) ids.add(ep.optInt("season"));
    seasons = new ArrayList<>(ids);
    List<String> names = new ArrayList<>();
    for (int n : seasons) names.add(n == 0 ? "Specials" : "Season " + n);
    if (seasons.isEmpty()) {
      health.setText("No episodes listed.");
      return;
    }
    String saved = a.prefs.getString("episode_" + title.id, "");
    JSONObject initial =
        episodes.stream()
            .filter(e -> EpisodeLibrary.key(e).equals(saved))
            .findFirst()
            .orElse(
                episodes.stream()
                    .filter(
                        e -> {
                          try {
                            return !PlaybackPlan.episodeQualities(PlaybackPlan.sources(e))
                                .isEmpty();
                          } catch (Exception ignored) {
                            return false;
                          }
                        })
                    .findFirst()
                    .orElse(episodes.get(0)));
    int initialSeason = seasons.indexOf(initial.optInt("season"));
    if (seasonsView != null) {
      seasonsView.setAdapter(
          new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, names) {
            public View getView(int p, View v, ViewGroup parent) {
              TextView row = (TextView) super.getView(p, v, parent);
              row.setTextColor(-1);
              row.setTextSize(13);
              row.setPadding(dp(8), dp(12), dp(8), dp(12));
              row.setSingleLine(true);
              row.setBackground(a.focusBackground());
              row.setSelected(seasons.get(p) == activeSeason);
              return row;
            }
          });
      seasonsView.setOnItemClickListener((p, v, n, id) -> selectSeason(seasons.get(n), null));
      selectSeason(initial.optInt("season"), initial);
      seasonsView.setSelection(Math.max(0, initialSeason));
    } else {
      seasonSpinner.setAdapter(
          new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, names));
      seasonSpinner.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}

            public void onItemSelected(AdapterView<?> p, View v, int n, long id) {
              selectSeason(
                  seasons.get(n), seasons.get(n) == initial.optInt("season") ? initial : null);
            }
          });
      seasonSpinner.setSelection(Math.max(0, initialSeason));
    }
  }

  void selectSeason(int season, JSONObject preferred) {
    activeSeason = season;
    visible.clear();
    for (JSONObject e : episodes) if (e.optInt("season") == season) visible.add(e);
    if (!visible.isEmpty())
      selectEpisode(preferred != null && visible.contains(preferred) ? preferred : visible.get(0));
    episodeAdapter.notifyDataSetChanged();
    if (seasonsView != null && seasonsView.getAdapter() instanceof BaseAdapter)
      ((BaseAdapter) seasonsView.getAdapter()).notifyDataSetChanged();
    episodesView.setSelection(Math.max(0, visible.indexOf(selected)));
  }

  void selectEpisode(JSONObject ep) {
    if (selected != ep) selectedQuality = "Auto";
    selected = ep;
    heading.setText(ep.optString("title", "Episode"));
    String numbering =
        (ep.optInt("season") == 0 ? "Special" : "Season " + ep.optInt("season"))
            + " · "
            + (ep.optInt("episode") > 0 ? "Episode " + ep.optInt("episode") : "Unnumbered special");
    airdate.setText(
        numbering + (ep.optString("airdate").isEmpty() ? "" : " · " + ep.optString("airdate")));
    overview.setText(
        ep.optString("synopsis", ep.optString("description", "No episode synopsis available.")));
    try {
      sources = PlaybackPlan.sources(ep);
    } catch (Exception e) {
      sources = new ArrayList<>();
    }
    a.prefs.edit().putString("episode_" + title.id, EpisodeLibrary.key(ep)).apply();
    loaded = true;
    updateControls();
    episodeAdapter.notifyDataSetChanged();
    if (alternateJob != null) alternateJob.cancel(true);
    lookingForAlternatives = true;
    health.setOnClickListener(v -> selectEpisode(selected));
    updateControls();
    alternateJob =
        a.work.submit(
            () -> {
              try {
                List<Catalog.Source> alternate =
                    AlternateEpisodes.find(title.id, ep.optInt("season"), ep.optInt("episode"));
                a.ui.post(
                    () -> {
                      if (!isShowing() || a.isDestroyed() || selected != ep) return;
                      Set<String> seen = new HashSet<>();
                      for (Catalog.Source source : sources)
                        seen.add(source.url + ":" + source.fileIndex);
                      for (Catalog.Source source : alternate)
                        if (seen.add(source.url + ":" + source.fileIndex)) sources.add(source);
                      lookingForAlternatives = false;
                      updateControls();
                    });
              } catch (Exception ignored) {
                a.ui.post(
                    () -> {
                      if (isShowing() && selected == ep) {
                        lookingForAlternatives = false;
                        updateControls();
                      }
                    });
              }
            });
  }

  @Override
  public void dismiss() {
    if (alternateJob != null) alternateJob.cancel(true);
    super.dismiss();
  }

  void watch() {
    Catalog.Source best = chosenSource();
    if (best == null) return;
    String playTitle =
        series
            ? title.title + " · S" + selected.optInt("season") + " E" + selected.optInt("episode")
            : title.title;
    String queueToken = null;
    if (series)
      try {
        queueToken = EpisodeQueue.create(a, title, episodes, selectedQuality);
      } catch (Exception error) {
        Toast.makeText(
                a, "Episode navigation unavailable: " + error.getMessage(), Toast.LENGTH_LONG)
            .show();
      }
    a.startActivity(
        new Intent(a, PlayerActivity.class)
            .putExtra("episodeQueue", queueToken)
            .putExtra("title", playTitle)
            .putExtra("source", best.url)
            .putExtra("fileIndex", best.fileIndex)
            .putExtra("filename", best.filename)
            .putExtra("absolute", series ? selected.optInt("absolute") : 0)
            .putExtra(
                "alternatives",
                episodeMode()
                    ? AlternateEpisodes.encode(
                        AlternateEpisodes.attempts(sources, selectedQuality, DeviceProfile.lite(a)))
                    : "[]")
            .putExtra("imdb", title.id)
            .putExtra(
                "episodeIdentity",
                series
                    ? title.id + ":" + EpisodeLibrary.key(selected)
                    : title.raw.optBoolean("animeRelease") ? title.id : "")
            .putExtra("season", series ? selected.optInt("season") : 0)
            .putExtra("episode", series ? selected.optInt("episode") : 0)
            .putExtra("subtitleLanguage", subtitleLanguage)
            .putExtra("subtitlesEnabled", subtitlesEnabled)
            .putExtra(
                "embeddedFirst",
                title.raw.optBoolean("animeRelease")
                    || series && selected.optBoolean("liveRelease")));
  }
}
