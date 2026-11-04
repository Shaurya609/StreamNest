package app.streamnest;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import org.json.*;

/** Durable, indexed last-good movie data. No video/cache/account data is stored here. */
final class MovieRepository extends SQLiteOpenHelper implements MovieProvider.Store {
  private final Map<String, List<String>> sessions = new LinkedHashMap<>();
  private final java.util.concurrent.CountDownLatch ready =
      new java.util.concurrent.CountDownLatch(1);
  private static MovieRepository shared;

  static synchronized MovieRepository shared(Context context) {
    if (shared == null) {
      Context app = context.getApplicationContext();
      shared = new MovieRepository(app);
      MovieRepository selected = shared;
      Thread task = new Thread(() -> selected.initialize(app), "saved-catalog");
      task.setDaemon(true);
      task.start();
    }
    return shared;
  }

  MovieRepository(Context context) {
    this(context, "catalog-movies-v1.db");
  }

  MovieRepository(Context context, String name) {
    super(context, name, null, 1);
    setWriteAheadLoggingEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE movies(id TEXT PRIMARY KEY,title TEXT,language TEXT,year INTEGER,rating"
            + " REAL,uploaded INTEGER,seeds INTEGER,popularity INTEGER,genres TEXT,q1080"
            + " INTEGER,q2160 INTEGER,payload TEXT,verified INTEGER)");
    db.execSQL("CREATE INDEX movie_title ON movies(title)");
    db.execSQL("CREATE INDEX movie_uploaded ON movies(uploaded)");
    db.execSQL(
        "CREATE TABLE requests(key TEXT PRIMARY KEY,feed TEXT,page INTEGER,ids TEXT,payload"
            + " TEXT,saved INTEGER)");
    db.execSQL("CREATE INDEX request_feed ON requests(feed,page)");
    db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int from, int to) {}

  void initialize(Context context) {
    try {
      String asset = "catalog-movies-v1.json";
      long version;
      try (InputStream in = context.getAssets().open(asset)) {
        version = CatalogSync.version(in);
      } catch (FileNotFoundException missing) {
        asset = "movies-hindi-v1.json";
        try (InputStream in = context.getAssets().open(asset)) {
          version = CatalogSync.version(in);
        }
      }
      try (InputStream in = context.getAssets().open(asset)) {
        importStream(in, version);
      }
      try (Cursor c =
          getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key='preferred'", null)) {
        if (c.moveToFirst() && MovieProvider.bases.contains(c.getString(0)))
          MovieProvider.preferred = c.getString(0);
      }
    } catch (Exception error) {
      android.util.Log.w("StreamNest", "Saved movie catalog could not be initialized", error);
    } finally {
      ready.countDown();
    }
  }

  synchronized void importSnapshot(JSONObject doc) throws Exception {
    long saved = doc.getLong("saved");
    String version = String.valueOf(saved);
    SQLiteDatabase db = getWritableDatabase();
    try (Cursor c = db.rawQuery("SELECT value FROM meta WHERE key='snapshot'", null)) {
      if (c.moveToFirst() && version.equals(c.getString(0))) return;
    }
    JSONArray rows = doc.getJSONArray("movies");
    if (rows.length() == 0) throw new IOException("Empty movie snapshot rejected");
    db.beginTransaction();
    try {
      rememberRows(db, rows, saved);
      ContentValues v = new ContentValues();
      v.put("key", "snapshot");
      v.put("value", version);
      db.insertWithOnConflict("meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  synchronized void importStream(InputStream input, long saved) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    String version = String.valueOf(saved);
    try (Cursor c = db.rawQuery("SELECT value FROM meta WHERE key='snapshot'", null)) {
      if (c.moveToFirst() && version.equals(c.getString(0))) return;
    }
    if (saved <= 0) throw new IOException("Invalid movie snapshot version");
    int accepted = 0;
    long declared = 0;
    db.beginTransaction();
    try (android.util.JsonReader reader = CatalogJson.reader(input)) {
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (name.equals("saved")) declared = reader.nextLong();
        else if (name.equals("movies")) {
          reader.beginArray();
          while (reader.hasNext()) {
            JSONObject raw = (JSONObject) CatalogJson.value(reader, 0);
            JSONObject row = FreshMovies.normalize(raw);
            if (row.optString("imdb_code").matches("tt[0-9]+")
                && row.getJSONArray("torrents").length() > 0) {
              rememberRows(db, new JSONArray().put(row), saved);
              accepted++;
            }
          }
          reader.endArray();
        } else reader.skipValue();
      }
      reader.endObject();
      if (accepted == 0 || declared != saved)
        throw new IOException("Empty or invalid movie snapshot rejected");
      db.delete("requests", "saved<?", new String[] {String.valueOf(saved)});
      ContentValues v = new ContentValues();
      v.put("key", "snapshot");
      v.put("value", version);
      db.insertWithOnConflict("meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private void rememberRows(SQLiteDatabase db, JSONArray rows, long verified) throws Exception {
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = FreshMovies.normalize(rows.getJSONObject(i));
      long stamp = row.optLong("catalogVerifiedAt", verified);
      String id = row.optString("imdb_code");
      if (!id.matches("tt[0-9]+") || row.optString("title").isEmpty()) continue;
      try (Cursor c = db.rawQuery("SELECT verified FROM movies WHERE id=?", new String[] {id})) {
        if (c.moveToFirst() && c.getLong(0) > stamp) continue;
      }
      JSONObject compact = new JSONObject();
      for (String key :
          new String[] {
            "imdb_code",
            "title",
            "year",
            "rating",
            "language",
            "date_uploaded_unix",
            "popularityRank",
            "genres",
            "runtime",
            "large_cover_image",
            "medium_cover_image",
            "torrents",
            "freshMovie",
            "catalogVerifiedAt"
          }) if (row.has(key)) compact.put(key, row.get(key));
      String description = row.optString("description_full", row.optString("summary"));
      compact.put(
          "description_full", description.substring(0, Math.min(1200, description.length())));
      boolean hd = false, four = false;
      JSONArray ts = compact.getJSONArray("torrents");
      for (int n = 0; n < ts.length(); n++) {
        hd |= ts.getJSONObject(n).optString("quality").equals("1080p");
        four |= ts.getJSONObject(n).optString("quality").equals("2160p");
      }
      StringBuilder genres = new StringBuilder("|");
      JSONArray gs = row.optJSONArray("genres");
      if (gs != null)
        for (int n = 0; n < gs.length(); n++)
          genres.append(BrowseOrder.genreKey(gs.optString(n))).append('|');
      ContentValues v = new ContentValues();
      v.put("id", id);
      v.put("title", row.optString("title").toLowerCase(Locale.ROOT));
      v.put("language", row.optString("language"));
      v.put("year", row.optInt("year"));
      v.put("rating", row.optDouble("rating", 0));
      v.put("uploaded", row.optLong("date_uploaded_unix"));
      v.put("seeds", BrowseOrder.seeds(row));
      v.put("popularity", row.optInt("popularityRank", Integer.MAX_VALUE));
      v.put("genres", genres.toString());
      v.put("q1080", hd ? 1 : 0);
      v.put("q2160", four ? 1 : 0);
      v.put("payload", compact.toString());
      v.put("verified", stamp);
      db.insertWithOnConflict("movies", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
  }

  static Map<String, String> params(String path) throws Exception {
    if (!path.split("\\?", 2)[0].equals("list_movies.json"))
      throw new IOException("Unsupported cached movie request");
    Map<String, String> p = new TreeMap<>();
    int q = path.indexOf('?');
    if (q >= 0)
      for (String part : path.substring(q + 1).split("&")) {
        String[] pair = part.split("=", 2);
        p.put(
            URLDecoder.decode(pair[0], "UTF-8"),
            pair.length > 1 ? URLDecoder.decode(pair[1], "UTF-8") : "");
      }
    p.putIfAbsent("page", "1");
    p.putIfAbsent("limit", "50");
    return p;
  }

  static String feed(Map<String, String> params) {
    Map<String, String> p = new TreeMap<>(params);
    p.remove("page");
    return p.toString();
  }

  static String key(Map<String, String> params) {
    return params.toString();
  }

  static int number(Map<String, String> p, String name, int fallback) {
    try {
      return Math.max(
          1, Math.min(100000, Integer.parseInt(p.getOrDefault(name, String.valueOf(fallback)))));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  @Override
  public synchronized void remember(String path, JSONObject data) throws Exception {
    Map<String, String> p = params(path);
    JSONArray rows = data.optJSONArray("movies");
    if (rows == null) rows = new JSONArray();
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      rememberRows(db, rows, System.currentTimeMillis());
      JSONArray ids = new JSONArray();
      for (int i = 0; i < rows.length(); i++) ids.put(rows.getJSONObject(i).optString("imdb_code"));
      ContentValues v = new ContentValues();
      v.put("key", key(p));
      v.put("feed", feed(p));
      v.put("page", number(p, "page", 1));
      v.put("ids", ids.toString());
      String json = data.toString();
      if (json.length() <= 2000000) v.put("payload", json);
      v.put("saved", System.currentTimeMillis());
      db.insertWithOnConflict("requests", null, v, SQLiteDatabase.CONFLICT_REPLACE);
      ContentValues preferred = new ContentValues();
      preferred.put("key", "preferred");
      preferred.put("value", MovieProvider.preferred);
      db.insertWithOnConflict("meta", null, preferred, SQLiteDatabase.CONFLICT_REPLACE);
      db.execSQL(
          "DELETE FROM requests WHERE key NOT IN (SELECT key FROM requests ORDER BY saved DESC"
              + " LIMIT 96)");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  public synchronized boolean suspiciousEmpty(String path, JSONObject data) throws Exception {
    Map<String, String> p = params(path);
    if (!p.getOrDefault("query_term", "").isEmpty()
        || number(p, "page", 1) != 1
        || data.optInt("movie_count") > 0) return false;
    JSONArray a = data.optJSONArray("movies");
    if (a != null && a.length() > 0) return false;
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT ids FROM requests WHERE key=?", new String[] {key(p)})) {
      if (c.moveToFirst() && new JSONArray(c.getString(0)).length() > 0) return true;
    }
    return !matchingIds(p).isEmpty();
  }

  @Override
  public synchronized JSONObject continuation(String path) throws Exception {
    Map<String, String> p = params(path);
    String f = feed(p) + "|" + MovieProvider.feedToken.get();
    if (number(p, "page", 1) == 1) return null;
    List<String> ids = sessions.get(f);
    return ids == null ? null : page(p, ids);
  }

  @Override
  public JSONObject saved(String path) throws Exception {
    ready.await(5, java.util.concurrent.TimeUnit.SECONDS);
    synchronized (this) {
      Map<String, String> p = params(path);
      String f = feed(p), token = MovieProvider.feedToken.get();
      if (token == null) return hasMovies() ? page(p, matchingIds(p)) : null;
      String session = f + "|" + token;
      List<String> ids = sessions.get(session);
      if (ids == null) {
        ids = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // Preserve the raw page positions already delivered before an outage. Duplicate
        // editions remain here because PagedMovies performs its own title deduplication.
        try (Cursor c =
            getReadableDatabase()
                .rawQuery(
                    "SELECT page,ids FROM requests WHERE feed=? ORDER BY page", new String[] {f})) {
          int next = 1;
          while (c.moveToNext()) {
            if (c.getInt(0) != next++) break;
            JSONArray rows = new JSONArray(c.getString(1));
            for (int i = 0; i < rows.length(); i++) {
              String id = rows.optString(i);
              if (!id.isEmpty()) {
                ids.add(id);
                seen.add(id);
              }
            }
          }
        }
        for (String id : matchingIds(p)) if (seen.add(id)) ids.add(id);
        if (ids.isEmpty() && !hasMovies()) return null;
        sessions.put(session, ids);
        while (sessions.size() > 4) sessions.remove(sessions.keySet().iterator().next());
      }
      return page(p, ids);
    }
  }

  private boolean hasMovies() {
    try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM movies LIMIT 1", null)) {
      return c.moveToFirst();
    }
  }

  private List<String> matchingIds(Map<String, String> p) {
    StringBuilder where = new StringBuilder("1=1");
    List<String> args = new ArrayList<>();
    String q = p.getOrDefault("query_term", "");
    if (q.matches("tt[0-9]+")) {
      where.append(" AND id=?");
      args.add(q);
    } else if (!q.isEmpty()) {
      where.append(" AND title LIKE ? ESCAPE '\\'");
      args.add(
          "%"
              + q.toLowerCase(Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%");
    }
    String quality = p.getOrDefault("quality", "");
    if (quality.equals("2160p")) where.append(" AND q2160=1");
    else if (quality.equals("1080p")) where.append(" AND q1080=1");
    String genre = p.getOrDefault("genre", "");
    if (!genre.isEmpty() && !genre.equalsIgnoreCase("All")) {
      where.append(" AND genres LIKE ?");
      args.add("%|" + BrowseOrder.genreKey(genre) + "|%");
    }
    String sort = p.getOrDefault("sort_by", "date_added"),
        column =
            sort.equals("title")
                ? "title"
                : sort.equals("year")
                    ? "year"
                    : sort.equals("rating")
                        ? "rating"
                        : sort.equals("seeds")
                            ? "seeds"
                            : sort.equals("download_count") ? "popularity" : "uploaded";
    String order = p.getOrDefault("order_by", "desc").equals("asc") ? " ASC" : " DESC";
    if (column.equals("popularity")) order = order.equals(" ASC") ? " DESC" : " ASC";
    List<String> ids = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "movies",
                new String[] {"id"},
                where.toString(),
                args.toArray(new String[0]),
                null,
                null,
                column
                    + order
                    + (column.equals("popularity")
                        ? (order.equals(" ASC") ? ",seeds DESC" : ",seeds ASC")
                        : "")
                    + ",id "
                    + (p.getOrDefault("order_by", "desc")
                            .equals(column.equals("title") ? "asc" : "desc")
                        ? "ASC"
                        : "DESC"))) {
      while (c.moveToNext()) ids.add(c.getString(0));
    }
    return ids;
  }

  private JSONObject page(Map<String, String> p, List<String> ids) throws Exception {
    int limit = Math.min(50, number(p, "limit", 50)), start = (number(p, "page", 1) - 1) * limit;
    JSONArray rows = new JSONArray();
    for (int i = start; i < Math.min(ids.size(), start + limit); i++)
      try (Cursor c =
          getReadableDatabase()
              .rawQuery("SELECT payload FROM movies WHERE id=?", new String[] {ids.get(i)})) {
        if (c.moveToFirst()) rows.put(new JSONObject(c.getString(0)));
      }
    return new JSONObject()
        .put("movie_count", ids.size())
        .put("movies", rows)
        .put("catalog_saved", true);
  }
}
