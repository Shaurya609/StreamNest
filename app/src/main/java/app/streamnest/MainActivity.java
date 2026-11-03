package app.streamnest;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
  final ExecutorService work = Executors.newFixedThreadPool(2);
  PosterLoader posters;
  boolean activeLite;
  Button sortControl, genreControl, qualityControl, languageControl;
  String language = "all", animeFormat = "series";
  int order = -1;
  final Handler ui = new Handler(Looper.getMainLooper());
  android.content.SharedPreferences prefs;
  LinearLayout root, content, tabs;
  TextView status;
  EditText search;
  String kind = "movies", sort = "trending", genre = "All", query = "", quality = "All";
  int page = 1, generation;
  boolean favoriteTab, nextAvailable;
  boolean hindiMode;
  final BrowseState browse = new BrowseState();
  List<Catalog.Title> titles = browse.rows;
  Future<?> browseJob;
  BaseAdapter browseAdapter;
  AbsListView browseList;
  Button moreControl;

  int dp(int n) {
    return (int) (getResources().getDisplayMetrics().density * n);
  }

  TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  Button button(String s, LinearLayout row, Runnable action) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextColor(
        new android.content.res.ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {0xff60636b, 0xffffffff}));
    b.setTextSize(13);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setMinHeight(0);
    b.setMinimumHeight(0);
    b.setPadding(dp(10), 0, dp(10), 0);
    b.setBackground(focusBackground());
    row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  public void onCreate(Bundle state) {
    super.onCreate(state);
    PlaybackCache.enter(this);
    if (Build.VERSION.SDK_INT >= 33)
      getOnBackInvokedDispatcher()
          .registerOnBackInvokedCallback(
              android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::exitApp);
    setRequestedOrientation(
        DeviceProfile.projector(this)
            ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    prefs = getSharedPreferences("library", 0);
    kind = prefs.getString("kind", "movies");
    favoriteTab = prefs.getBoolean("favoriteTab", false);
    hindiMode = prefs.getBoolean("hindiMode", false);
    animeFormat = prefs.getString("animeFormat", "series");
    language = prefs.getString("language", hindiMode ? "hi" : "all");
    hindiMode = language.equals("hi");
    order = prefs.getInt("order", -1);
    sort = prefs.getString("sort", "trending");
    genre = prefs.getString("genre", "All");
    quality = prefs.getString("quality", "All");
    FreshMovies.discoveryThreads = DeviceProfile.lite(this) ? 2 : 4;
    activeLite = DeviceProfile.lite(this);
    posters = new PosterLoader(this);
    MovieIndex.configure(getCacheDir());
    SeriesIndex.configure(getAssets(), getCacheDir());
    AnimeCatalog.configure(getAssets(), getCacheDir());
    getWindow().setStatusBarColor(0xff17181b);
    getWindow().setNavigationBarColor(0xff17181b);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xff17181b);
    root.setPadding(dp(10), dp(4), dp(10), 0);
    setContentView(root);
    HorizontalScrollView barScroll = new HorizontalScrollView(this);
    barScroll.setHorizontalScrollBarEnabled(false);
    root.addView(barScroll);
    LinearLayout bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    barScroll.addView(bar);
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.ic_logo);
    bar.addView(logo, new LinearLayout.LayoutParams(dp(32), dp(32)));
    TextView brand = text("StreamNest", 17, -1);
    brand.setPadding(dp(6), 0, dp(14), 0);
    bar.addView(brand);
    tabs = new LinearLayout(this);
    bar.addView(tabs);
    navButton("Movies", tabs, () -> tab("movies"));
    navButton("Series", tabs, () -> tab("shows"));
    navButton("Anime", tabs, () -> tab("animes"));
    navButton(
        "Favorites",
        tabs,
        () -> {
          favoriteTab = true;
          load();
        });
    ImageButton searchButton = new ImageButton(this);
    searchButton.setImageResource(R.drawable.ic_search);
    searchButton.setContentDescription("Search titles");
    searchButton.setBackground(focusBackground());
    bar.addView(searchButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
    ImageButton settingsButton = new ImageButton(this);
    settingsButton.setImageResource(R.drawable.ic_settings);
    settingsButton.setContentDescription("Settings");
    settingsButton.setBackground(focusBackground());
    bar.addView(settingsButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
    settingsButton.setOnClickListener(v -> settings());
    search = new EditText(this);
    search.setTextColor(-1);
    search.setHintTextColor(0xff9a9a9a);
    search.setHint("Search titles");
    search.setSingleLine();
    search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    search.setVisibility(View.GONE);
    root.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));
    searchButton.setOnClickListener(
        v -> {
          search.setVisibility(search.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
          if (search.getVisibility() == View.VISIBLE) search.requestFocus();
        });
    search.setOnEditorActionListener(
        (v, a, e) -> {
          query = search.getText().toString().trim();
          page = 1;
          load();
          ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
              .hideSoftInputFromWindow(search.getWindowToken(), 0);
          return true;
        });
    LinearLayout filtersRow = new LinearLayout(this);
    root.addView(filtersRow);
    qualityControl = button("Quality", filtersRow, this::qualityMenu);
    genreControl = button("Genre", filtersRow, this::genreMenu);
    languageControl = button("Language", filtersRow, this::hindiMenu);
    sortControl = button("Sort by", filtersRow, this::sortMenu);
    status = text("", 12, 0xff92959c);
    status.setPadding(dp(6), dp(4), 0, dp(6));
    root.addView(status);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    load();
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 1);
  }

  void hindiMenu() {
    if (kind.equals("animes")) {
      new AlertDialog.Builder(this)
          .setTitle("Anime format")
          .setItems(
              new String[] {
                "Series · Episode sources",
                "Films · Japanese animation",
                "Latest episodes · trusted releases"
              },
              (d, n) -> {
                animeFormat = new String[] {"series", "films", "latest"}[n];
                if (animeFormat.equals("latest")) {
                  genre = "All";
                  sort = "last added";
                  order = -1;
                }
                prefs.edit().putString("animeFormat", animeFormat).apply();
                page = 1;
                load();
              })
          .show();
      return;
    }

    new AlertDialog.Builder(this)
        .setTitle("Language")
        .setItems(
            new String[] {"All · English & Hindi", "English", "Hindi"},
            (d, n) -> {
              language = new String[] {"all", "en", "hi"}[n];
              hindiMode = language.equals("hi");
              prefs
                  .edit()
                  .putString("language", language)
                  .putBoolean("hindiMode", hindiMode)
                  .apply();
              page = 1;
              load();
            })
        .show();
  }

  void tab(String k) {
    favoriteTab = false;
    kind = k;
    if (k.equals("movies") && !Arrays.asList("All", "1080p", "2160p").contains(quality))
      quality = "All";
    if (k.equals("animes")) hindiMode = false;
    page = 1;
    load();
  }

  String base() {
    return prefs.getString(
        "server_" + kind,
        kind.equals("movies")
            ? "https://api.apiabcd.com/"
            : kind.equals("shows")
                ? "https://api.apiabcd.com/"
                : "https://movies-api.accel.li/api/v2/");
  }

  void load() {
    prefs.edit().putString("kind", kind).putBoolean("favoriteTab", favoriteTab).apply();
    prefs
        .edit()
        .putString("sort", sort)
        .putString("genre", genre)
        .putString("quality", quality)
        .putInt("order", order)
        .apply();
    String sortLabel =
        kind.equals("animes") && animeFormat.equals("latest")
            ? (sort.equals("popularity")
                ? "Reported seeds"
                : sort.equals("title") ? "Release name" : "Upload time")
            : sort;
    sortControl.setText("Sort by  " + sortLabel + (order == 1 ? " ↑" : " ↓"));
    languageControl.setText(
        kind.equals("animes")
            ? "Format  "
                + (animeFormat.equals("series")
                    ? "Series"
                    : animeFormat.equals("latest") ? "Latest episodes" : "Films")
                + " ▾"
            : "Language  "
                + (language.equals("all")
                    ? "All"
                    : language.equals("hi") ? "Hindi titles" : "English titles")
                + " ▾");
    genreControl.setEnabled(!(kind.equals("animes") && animeFormat.equals("latest")));
    genreControl.setText("Genre  " + genre + " ▾");
    qualityControl.setText(
        "Quality  "
            + (quality.equals("All")
                ? (kind.equals("movies") || kind.equals("animes") && animeFormat.equals("films")
                    ? "1080p & 4K"
                    : "All available")
                : quality)
            + " ▾");
    for (int i = 0; i < tabs.getChildCount(); i++) {
      Button b = (Button) tabs.getChildAt(i);
      String name = b.getText().toString();
      b.setSelected(
          (favoriteTab && name.equals("Favorites"))
              || (!favoriteTab
                  && name.equals(
                      kind.equals("movies") ? "Movies" : kind.equals("shows") ? "Series" : "Anime"))
              || (hindiMode && name.equals("Hindi")));
    }
    if (browseJob != null) browseJob.cancel(true);
    generation++;
    page = 0;
    browse.reset(generation);
    browseAdapter = null;
    browseList = null;
    moreControl = null;
    content.removeAllViews();
    status.setText(favoriteTab ? "Saved titles" : "Loading " + kind + "…");
    requestPage(1, generation);
  }

  void loadMore() {
    if (browse.loading || !browse.hasMore) return;
    requestPage(browse.page + 1, generation);
  }

  void requestPage(int requestedPage, int request) {
    if (!browse.begin(request, requestedPage)) return;
    updatePaging();
    String b = base(), k = kind, q = query, s = sort, g = genre, qual = quality, lang = language;
    int direction = order;
    int p = requestedPage;
    boolean saved = favoriteTab;
    boolean hindi = hindiMode;
    String format = animeFormat;
    browseJob =
        work.submit(
            () -> {
              try {
                List<Catalog.Title> found;
                if (saved) {
                  found = new ArrayList<>();
                  for (Map.Entry<String, ?> entry : prefs.getAll().entrySet())
                    if (entry.getKey().startsWith("saved_")) {
                      JSONObject o = new JSONObject((String) entry.getValue());
                      Catalog.Title t =
                          Catalog.parse(o.getJSONObject("title"), o.getString("kind"));
                      if (t.title.toLowerCase().contains(q.toLowerCase())) found.add(t);
                    }
                } else
                  found =
                      k.equals("animes") && format.equals("latest")
                          ? AnimeReleases.list(p, q, s, qual, direction)
                          : k.equals("animes") && format.equals("series")
                              ? AnimeCatalog.list(p, q, s, g, qual, direction)
                              : k.equals("movies") && Catalog.legacy(b)
                                  ? PagedMovies.list(p, q, s, g, qual, lang, direction, false)
                                  : k.equals("shows") && Catalog.legacy(b)
                                      ? SeriesIndex.list(b, p, q, s, g, lang, direction)
                                      : hindi
                                          ? HindiCatalog.list(
                                              this, b, k, p, q, s, qual, g, direction)
                                          : Catalog.list(b, k, p, q, s, g, qual, direction);
                boolean more = found.size() == 24;
                if (!saved && k.equals("shows") && Catalog.legacy(b))
                  more = !SeriesIndex.list(b, p + 1, q, s, g, lang, direction).isEmpty();
                else if (!saved && k.equals("animes") && format.equals("latest"))
                  more = !AnimeReleases.list(p + 1, q, s, qual, direction).isEmpty();
                else if (!saved && k.equals("animes") && format.equals("series"))
                  more = !AnimeCatalog.list(p + 1, q, s, g, qual, direction).isEmpty();
                else if (!saved && (k.equals("movies") && Catalog.legacy(b) || k.equals("animes")))
                  more = Boolean.TRUE.equals(PagedMovies.remaining.get());
                final boolean canNext = more;
                ui.post(
                    () -> {
                      if (request != generation || isDestroyed()) return;
                      if (!browse.commit(request, p, found, !saved && canNext)) return;
                      page = browse.page;
                      nextAvailable = browse.hasMore;
                      status.setText(
                          (hindi && !saved ? "Hindi " : "")
                              + (saved
                                  ? "Saved"
                                  : k.equals("movies")
                                      ? "Movies"
                                      : k.equals("shows")
                                          ? "Series"
                                          : format.equals("series")
                                              ? "Anime series · Episode sources"
                                              : format.equals("latest")
                                                  ? "Latest anime episodes · trusted releases"
                                                  : "Anime films · Japanese animation")
                              + " · "
                              + titles.size()
                              + (k.equals("animes") && format.equals("latest")
                                  ? (titles.size() == 1 ? " release" : " releases")
                                  : (titles.size() == 1 ? " title" : " titles"))
                              + (saved ? "" : " loaded"));
                      if (hindi && !saved)
                        content.setContentDescription(
                            "Live Hindi catalog. Playback sources and audio vary by title.");
                      if (browseAdapter == null) render();
                      else browseAdapter.notifyDataSetChanged();
                      updatePaging();
                    });
              } catch (Exception e) {
                ui.post(
                    () -> {
                      if (request != generation || isDestroyed()) return;
                      browse.fail(request, p);
                      if (!titles.isEmpty()) {
                        updatePaging();
                        return;
                      }
                      status.setText("Catalog unavailable");
                      TextView error =
                          text(
                              "Could not load this catalog.\n\n"
                                  + (e.getMessage() == null ? "Connection failed" : e.getMessage())
                                  + "\n\n"
                                  + "Set a working server in Settings. You can still open a torrent"
                                  + " or use Test film to check playback.",
                              16,
                              0xffc4cee0);
                      error.setPadding(0, dp(24), 0, dp(24));
                      content.addView(error);
                      LinearLayout retry = new LinearLayout(this);
                      content.addView(retry);
                      button("Retry", retry, this::load);
                      button("Settings", retry, this::settings);
                    });
              }
            });
  }

  android.graphics.drawable.StateListDrawable focusBackground() {
    android.graphics.drawable.StateListDrawable states =
        new android.graphics.drawable.StateListDrawable();
    GradientDrawable focus = new GradientDrawable();
    focus.setColor(0xff253c55);
    focus.setStroke(dp(2), 0xff279be8);
    states.addState(new int[] {android.R.attr.state_focused}, focus);
    GradientDrawable selected = new GradientDrawable();
    selected.setColor(0xff243342);
    selected.setStroke(dp(1), 0xff279be8);
    states.addState(new int[] {android.R.attr.state_selected}, selected);
    states.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(0xff17181b));
    return states;
  }

  void navButton(String name, LinearLayout row, Runnable action) {
    Button b = button(name, row, action);
    b.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(46)));
  }

  void render() {
    content.removeAllViews();
    if (titles.isEmpty()) {
      content.addView(
          text(
              favoriteTab
                  ? "Your saved titles will appear here."
                  : "No titles match these filters.",
              16,
              0xffaaaaaa));
    } else if (!favoriteTab && kind.equals("animes") && animeFormat.equals("latest")) {
      ListView list = new ListView(this);
      list.setSelector(focusBackground());
      browseAdapter =
          new BaseAdapter() {
            public int getCount() {
              return titles.size();
            }

            public Object getItem(int p) {
              return titles.get(p);
            }

            public long getItemId(int p) {
              return p;
            }

            public View getView(int p, View recycled, android.view.ViewGroup parent) {
              LinearLayout row = new LinearLayout(MainActivity.this);
              row.setOrientation(LinearLayout.VERTICAL);
              row.setPadding(dp(12), dp(12), dp(12), dp(12));
              row.setBackground(focusBackground());
              Catalog.Title item = titles.get(p);
              TextView name = text(item.title, 16, -1);
              name.setMaxLines(3);
              row.addView(name);
              row.addView(
                  text(
                      item.raw.optString("releaseQuality")
                          + "   ·   "
                          + item.raw.optInt("releaseSeeds")
                          + " reported seeds",
                      12,
                      0xff92959c));
              return row;
            }
          };
      list.setAdapter(browseAdapter);
      bindPagination(list);

      list.setOnItemClickListener((p, v, n, id) -> details(titles.get(n)));
      content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    } else {
      GridView grid = new GridView(this);
      int width = getResources().getConfiguration().screenWidthDp;
      int columns = width < 600 ? 2 : Math.max(4, width / 150);
      grid.setNumColumns(columns);
      grid.setHorizontalSpacing(dp(12));
      grid.setVerticalSpacing(dp(12));
      grid.setPadding(dp(3), dp(4), dp(3), dp(8));
      grid.setClipToPadding(false);
      grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
      grid.setDrawSelectorOnTop(true);
      grid.setSelector(new android.graphics.drawable.ColorDrawable(0x55279be8));
      int posterHeight =
          DeviceProfile.lite(this) ? dp(180) : dp(Math.min(235, (width / columns - 16) * 3 / 2));
      browseAdapter =
          new BaseAdapter() {
            public int getCount() {
              return titles.size();
            }

            public Object getItem(int p) {
              return titles.get(p);
            }

            public long getItemId(int p) {
              return p;
            }

            public View getView(int p, View recycled, android.view.ViewGroup parent) {
              LinearLayout card;
              ImageView poster;
              TextView title, year;
              if (recycled == null) {
                card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(2), dp(2), dp(2), dp(5));
                poster = new ImageView(MainActivity.this);
                poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                poster.setBackgroundColor(0xff25262b);
                card.addView(poster, new LinearLayout.LayoutParams(-1, posterHeight));
                title = text("", 13, -1);
                title.setSingleLine();
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                title.setPadding(0, dp(6), 0, 0);
                card.addView(title);
                year = text("", 11, 0xff8c8e94);
                card.addView(year);
                card.setTag(new View[] {poster, title, year});
              } else {
                card = (LinearLayout) recycled;
                View[] views = (View[]) card.getTag();
                poster = (ImageView) views[0];
                title = (TextView) views[1];
                year = (TextView) views[2];
              }
              Catalog.Title t = titles.get(p);
              title.setText(t.title);
              year.setText(
                  t.year + "  " + (t.raw.optString("language").equals("hi") ? "Hindi" : ""));
              card.setContentDescription(t.title + " " + t.year);
              posters.load(t.poster, poster);
              return card;
            }
          };
      grid.setAdapter(browseAdapter);
      bindPagination(grid);

      grid.setOnItemClickListener((p, v, n, id) -> details(titles.get(n)));
      content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    if (!favoriteTab) {
      LinearLayout footer = new LinearLayout(this);
      moreControl = button("", footer, this::loadMore);
      content.addView(footer);
      updatePaging();
    }
  }

  void bindPagination(AbsListView view) {
    browseList = view;
    view.setOnScrollListener(
        new AbsListView.OnScrollListener() {
          public void onScrollStateChanged(AbsListView v, int state) {}

          public void onScroll(AbsListView v, int first, int visible, int total) {
            if (total > 0
                && total == titles.size()
                && visible > 0
                && first + visible >= total - 6
                && !browse.loading
                && browse.hasMore
                && !browse.failed) loadMore();
          }
        });
  }

  void updatePaging() {
    if (moreControl == null) return;
    moreControl.setText(
        browse.loading
            ? "Loading more…"
            : browse.failed
                ? "Retry loading more"
                : browse.hasMore ? "Load more" : "End of results");
    moreControl.setEnabled(!browse.loading && (browse.hasMore || browse.failed));
  }

  void sortMenu() {
    if (kind.equals("animes") && animeFormat.equals("latest")) {
      String[] values = {"last added", "popularity", "title"};
      new AlertDialog.Builder(this)
          .setTitle("Sort releases")
          .setItems(
              new String[] {"Upload time", "Reported seeds", "Release name"},
              (d, n) -> {
                String next = values[n];
                order = sort.equals(next) ? -order : next.equals("title") ? 1 : -1;
                sort = next;
                page = 1;
                load();
              })
          .show();
      return;
    }

    new AlertDialog.Builder(this)
        .setTitle("Sort by")
        .setSingleChoiceItems(
            BrowseOrder.LABELS,
            java.util.Arrays.asList(BrowseOrder.KEYS).indexOf(sort),
            (d, n) -> {
              String next = BrowseOrder.KEYS[n];
              order = sort.equals(next) ? -order : next.equals("title") ? 1 : -1;
              sort = next;
              d.dismiss();
              page = 1;
              load();
            })
        .show();
  }

  void qualityMenu() {
    boolean episodes =
        !kind.equals("movies") && !(kind.equals("animes") && animeFormat.equals("films"));
    String[] labels =
        episodes
            ? new String[] {"All available", "1080p", "4K", "720p", "480p"}
            : new String[] {"1080p & 4K", "1080p", "4K"};
    String[] values =
        episodes
            ? new String[] {"All", "1080p", "2160p", "720p", "480p"}
            : new String[] {"All", "1080p", "2160p"};
    new AlertDialog.Builder(this)
        .setTitle("Quality")
        .setItems(
            labels,
            (d, n) -> {
              quality = values[n];
              page = 1;
              load();
            })
        .show();
  }

  void genreMenu() {
    String[] genres = {
      "All",
      "Action",
      "Adventure",
      "Animation",
      "Comedy",
      "Crime",
      "Documentary",
      "Drama",
      "Fantasy",
      "Horror",
      "Mystery",
      "Romance",
      "Sci-Fi",
      "Thriller"
    };
    new AlertDialog.Builder(this)
        .setTitle("Genre")
        .setItems(
            genres,
            (d, n) -> {
              genre = genres[n];
              page = 1;
              load();
            })
        .show();
  }

  void details(Catalog.Title t) {
    new TitleScreen(this, t).show();
  }

  void choose(String title, List<Catalog.Source> sources) {
    choose(title, sources, "", 0, 0);
  }

  void choose(String title, List<Catalog.Source> sources, String imdb, int season, int episode) {
    if (sources.isEmpty()) {
      new AlertDialog.Builder(this)
          .setTitle("No playback sources")
          .setMessage("The catalog did not supply torrent links for this title.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    List<Catalog.Source> sorted = new ArrayList<>(sources);
    sorted.removeIf(
        item ->
            ReleaseQuality.camera(item.label)
                || !(item.label.contains("1080p") || item.label.contains("2160p"))
                || (!quality.equals("All") && !item.label.contains(quality)));
    if (sorted.isEmpty()) {
      new AlertDialog.Builder(this)
          .setTitle("No HD sources")
          .setMessage("No source matches the selected HD quality.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    sorted.sort(
        java.util.Comparator.<Catalog.Source>comparingInt(
                item -> DeviceProfile.lite(this) && item.label.contains("2160p") ? 1 : 0)
            .thenComparing((a, b) -> Integer.compare(b.seeds, a.seeds)));
    String[] labels = new String[sorted.size()];
    for (int i = 0; i < labels.length; i++)
      labels[i] = sorted.get(i).label + " · " + sorted.get(i).seeds + " reported seeds";
    new AlertDialog.Builder(this)
        .setTitle("Choose source")
        .setItems(labels, (d, n) -> play(title, sorted.get(n).url, imdb, season, episode))
        .setNegativeButton("Cancel", null)
        .show();
  }

  void movieSources(Catalog.Title t) {
    if (!t.sources.isEmpty()) {
      choose(t.title, t.sources, t.id, 0, 0);
      return;
    }
    ProgressDialog progress =
        ProgressDialog.show(this, "Playback sources", "Loading…", true, false);
    String b = prefs.getString("server_" + t.kind, "https://api.apiabcd.com/");
    work.execute(
        () -> {
          try {
            Catalog.Title detailed = Catalog.parse(Catalog.details(b, t), t.kind);
            ui.post(
                () -> {
                  if (!isDestroyed()) {
                    progress.dismiss();
                    choose(t.title, detailed.sources, t.id, 0, 0);
                  }
                });
          } catch (Exception e) {
            ui.post(
                () -> {
                  if (!isDestroyed()) {
                    progress.dismiss();
                    toast(e);
                  }
                });
          }
        });
  }

  void showEpisodes(Catalog.Title title, List<JSONObject> episodes) {
    Dialog dialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(dp(20), dp(16), dp(20), dp(12));
    layout.setBackgroundColor(0xff17181b);
    LinearLayout heading = new LinearLayout(this);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    TextView name = text(title.title, 22, -1);
    heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton close = new ImageButton(this);
    close.setImageResource(R.drawable.ic_close);
    close.setContentDescription("Close episodes");
    close.setBackground(focusBackground());
    heading.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
    close.setOnClickListener(v -> dialog.dismiss());
    layout.addView(heading);
    TextView help = text("Only episodes with matching HD sources are shown", 13, 0xff92959c);
    layout.addView(help);
    TreeSet<Integer> seasonSet = new TreeSet<>();
    for (JSONObject e : episodes) seasonSet.add(e.optInt("season"));
    List<Integer> seasons = new ArrayList<>(seasonSet);
    Spinner season = new Spinner(this);
    List<String> seasonNames = new ArrayList<>();
    for (int n : seasons) seasonNames.add(n == 0 ? "Specials" : "Season " + n);
    season.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, seasonNames));
    layout.addView(season);
    ListView list = new ListView(this);
    list.setSelector(focusBackground());
    layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    List<JSONObject> visible = new ArrayList<>();
    season.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> p) {}

          public void onItemSelected(AdapterView<?> p, View v, int n, long id) {
            visible.clear();
            List<String> labels = new ArrayList<>();
            for (JSONObject ep : episodes)
              if (ep.optInt("season") == seasons.get(n)) {
                visible.add(ep);
                labels.add(
                    String.format(
                        Locale.ROOT,
                        "E%02d   %s\n%d HD sources",
                        ep.optInt("episode"),
                        ep.optString("title", "Episode " + ep.optInt("episode")),
                        ep.optInt("hdSourceCount")));
              }
            list.setAdapter(
                new ArrayAdapter<String>(
                    MainActivity.this, android.R.layout.simple_list_item_1, labels) {
                  public View getView(int position, View recycled, android.view.ViewGroup parent) {
                    TextView row = (TextView) super.getView(position, recycled, parent);
                    row.setTextColor(-1);
                    row.setTextSize(16);
                    row.setMinHeight(dp(68));
                    row.setBackground(focusBackground());
                    return row;
                  }
                });
          }
        });
    list.setOnItemClickListener(
        (p, v, n, id) -> {
          JSONObject ep = visible.get(n);
          try {
            List<Catalog.Source> sources = new ArrayList<>();
            Catalog.collect(ep.opt("items"), "", sources);
            Catalog.collect(ep.opt("torrents"), "", sources);
            Catalog.collect(ep.opt("items_lang"), "", sources);
            choose(
                title.title + " · S" + ep.optInt("season") + " E" + ep.optInt("episode"),
                sources,
                title.id,
                ep.optInt("season"),
                ep.optInt("episode"));
          } catch (Exception e) {
            toast(e);
          }
        });
    dialog.setContentView(layout);
    dialog.show();
    dialog.getWindow().setLayout(-1, -1);
  }

  void episodes(Catalog.Title t) {
    ProgressDialog progress = ProgressDialog.show(this, "Episodes", "Loading…", true, false);
    String b =
        prefs.getString(
            "server_" + t.kind,
            t.kind.equals("shows")
                ? "https://api.apiabcd.com/"
                : "https://movies-api.accel.li/api/v2/");
    work.execute(
        () -> {
          try {
            JSONObject o = Catalog.details(b, t);
            List<JSONObject> eps = ReleaseQuality.episodes(o, quality);
            if (eps.isEmpty())
              throw new Exception(
                  "No episodes have a source matching "
                      + (quality.equals("All") ? "1080p or 4K" : quality));
            ui.post(
                () -> {
                  if (isDestroyed()) return;
                  progress.dismiss();
                  showEpisodes(t, eps);
                });
          } catch (Exception e) {
            ui.post(
                () -> {
                  if (!isDestroyed()) {
                    progress.dismiss();
                    toast(e);
                  }
                });
          }
        });
  }

  void play(String title, String source) {
    play(title, source, "", 0, 0);
  }

  void play(String title, String source, String imdb, int season, int episode) {
    startActivity(
        new Intent(this, PlayerActivity.class)
            .putExtra("title", title)
            .putExtra("source", source)
            .putExtra("imdb", imdb)
            .putExtra("season", season)
            .putExtra("episode", episode));
  }

  void openLink() {
    EditText input = new EditText(this);
    input.setHint("magnet:?… or https://… .torrent");
    input.setTextColor(-1);
    new AlertDialog.Builder(this)
        .setTitle("Open torrent")
        .setView(input)
        .setPositiveButton(
            "Play",
            (d, n) -> {
              String u = input.getText().toString().trim();
              if (u.startsWith("magnet:?") || u.startsWith("https://") || u.startsWith("http://"))
                play("Your torrent", u);
              else
                Toast.makeText(this, "Enter a magnet link or torrent URL", Toast.LENGTH_LONG)
                    .show();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void settings() {
    new AlertDialog.Builder(this)
        .setTitle("Settings")
        .setItems(
            new String[] {
              "Catalog servers",
              "OpenSubtitles account",
              "Cache folder & projector mode",
              "Open torrent",
              "Test film",
              "Exit StreamNest"
            },
            (d, n) -> {
              if (n == 0) catalogSettings();
              else if (n == 1) startActivity(new Intent(this, SubtitleSettingsActivity.class));
              else if (n == 2) startActivity(new Intent(this, PlaybackSettingsActivity.class));
              else if (n == 3) openLink();
              else if (n == 5) exitApp();
              else
                play(
                    "Big Buck Bunny · public test film",
                    "https://webtorrent.io/torrents/big-buck-bunny.torrent");
            })
        .show();
  }

  void catalogSettings() {
    LinearLayout fields = new LinearLayout(this);
    fields.setPadding(dp(18), 0, dp(18), 0);
    fields.setOrientation(LinearLayout.VERTICAL);
    fields.addView(
        text(
            "Movies and Series use the server discovered from your reference app. A dedicated anime"
                + " API is still needed. You can change servers here.",
            14,
            0xffa8b5cb));
    String[] kinds = {"movies", "shows", "animes"};
    EditText[] inputs = new EditText[3];
    for (int j = 0; j < 3; j++) {
      String k = kinds[j];
      fields.addView(text(k, 14, -1));
      inputs[j] = new EditText(this);
      inputs[j].setTextColor(-1);
      inputs[j].setSingleLine();
      inputs[j].setInputType(17);
      inputs[j].setText(
          prefs.getString(
              "server_" + k,
              k.equals("movies")
                  ? "https://api.apiabcd.com/"
                  : k.equals("shows")
                      ? "https://api.apiabcd.com/"
                      : "https://movies-api.accel.li/api/v2/"));
      fields.addView(inputs[j]);
    }
    ScrollView scroll = new ScrollView(this);
    scroll.addView(fields);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Catalog settings")
            .setView(scroll)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      for (EditText input : inputs) {
                        String s = input.getText().toString().trim();
                        try {
                          java.net.URI u = java.net.URI.create(s);
                          if ((!"https".equals(u.getScheme()) && !"http".equals(u.getScheme()))
                              || u.getHost() == null) throw new Exception();
                        } catch (Exception e) {
                          input.setError("Enter a valid HTTP or HTTPS server");
                          return;
                        }
                      }
                      android.content.SharedPreferences.Editor editor = prefs.edit();
                      for (int i = 0; i < 3; i++)
                        editor.putString(
                            "server_" + kinds[i], inputs[i].getText().toString().trim());
                      editor.apply();
                      dialog.dismiss();
                      page = 1;
                      load();
                    }));
    dialog.show();
  }

  void filters() {
    String[] choices = {
      "Latest additions", "Most seeded", "Year", "Title", "Rating", "Genre", "Quality"
    };
    new AlertDialog.Builder(this)
        .setTitle("Browse filters")
        .setItems(
            choices,
            (d, n) -> {
              if (n == 6) {
                String[] qs = {"All qualities", "360p", "480p", "720p", "1080p"};
                new AlertDialog.Builder(this)
                    .setTitle("Quality")
                    .setItems(
                        qs,
                        (x, i) -> {
                          quality = i == 0 ? "360p,480p,720p,1080p" : qs[i];
                          page = 1;
                          load();
                        })
                    .show();
              } else if (n == 5) {
                String[] gs = {
                  "All",
                  "Action",
                  "Adventure",
                  "Animation",
                  "Comedy",
                  "Crime",
                  "Documentary",
                  "Drama",
                  "Fantasy",
                  "Horror",
                  "Mystery",
                  "Romance",
                  "Science Fiction",
                  "Thriller"
                };
                new AlertDialog.Builder(this)
                    .setTitle("Genre")
                    .setItems(
                        gs,
                        (x, i) -> {
                          genre = gs[i];
                          page = 1;
                          load();
                        })
                    .show();
              } else {
                sort = new String[] {"last added", "seeds", "year", "title", "rating"}[n];
                page = 1;
                load();
              }
            })
        .show();
  }

  void toast(Exception e) {
    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
  }

  protected void onResume() {
    super.onResume();
    if (posters != null && activeLite != DeviceProfile.lite(this)) {
      recreate();
    }
  }

  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (posters != null) posters.trim();
  }

  void exitApp() {
    PlaybackCache.exit(this);
    finishAndRemoveTask();
  }

  @Override
  public void onBackPressed() {
    exitApp();
  }

  protected void onDestroy() {
    if (isFinishing()) PlaybackCache.exit(this);
    generation++;
    work.shutdownNow();
    if (posters != null) posters.close();
    ui.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
