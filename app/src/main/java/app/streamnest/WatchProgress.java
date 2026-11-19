package app.streamnest;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

final class WatchProgress {
  static volatile String preferenceName = "watch-progress";

  static String identity(String imdb, int season, int episode, String fallback) {
    if (imdb != null && imdb.matches("tt[0-9]+"))
      return episode > 0 ? "episode:" + imdb + ":" + season + ":" + episode : "movie:" + imdb;
    return "source:" + fallback;
  }

  static String identity(Intent intent, String fallback) {
    return identity(
        intent.getStringExtra("imdb"),
        intent.getIntExtra("season", 0),
        intent.getIntExtra("episode", 0),
        fallback);
  }

  static long resumable(long position, long duration) {
    return position >= 10000
            && (duration <= 0 || position < duration - 10000 && position < duration * 0.98)
        ? position
        : 0;
  }

  static synchronized long read(Context c, String key, long legacy) {
    SharedPreferences selected=c.getSharedPreferences(preferenceName,0);Object value;
    try{value=selected.getString(key,null);}catch(ClassCastException legacyType){value=selected.getAll().get(key);}

    try{String marker=c.getSharedPreferences(preferenceName,0).getString("removed:"+key.split(":")[1],null);if(marker!=null){JSONObject removed=new JSONObject(marker),row=value instanceof String?new JSONObject((String)value):null;if(row==null||removed.optLong("updatedAt")>=row.optLong("updatedAt"))return 0;}}catch(Exception ignored){}
    try {
      if (value instanceof String) {
        JSONObject row = new JSONObject((String) value);
        return resumable(row.getLong("positionMs"), row.getLong("durationMs"));
      }
      if (value instanceof Number) return ((Number) value).longValue();
    } catch (Exception ignored) {
    }
    return legacy;
  }

  static synchronized JSONObject record(Context c,String key)throws Exception {
    String raw;try{raw=c.getSharedPreferences(preferenceName,0).getString(key,null);}catch(ClassCastException legacy){return null;}
    if(raw==null)return null;
    try{return WatchSyncCrypto.clean(new JSONObject().put(key,new JSONObject(raw))).optJSONObject(key);}catch(JSONException invalid){return null;}
  }

  static synchronized void save(Context c, String key, long position, long duration) {
    if (position < 0 || duration <= 0) return;
    try {
      SharedPreferences prefs = c.getSharedPreferences(preferenceName, 0);
      long observed = prefs.getLong("_clock", 0);
      if (!prefs.contains("_clock")) {
        JSONObject existing = records(c);
        for (Iterator<String> it=existing.keys();it.hasNext();)
          observed=Math.max(observed,existing.getJSONObject(it.next()).optLong("updatedAt"));
      }
      long clock = Math.max(System.currentTimeMillis(), observed + 1);
      JSONObject row =
          new JSONObject()
              .put("positionMs", resumable(position, duration))
              .put("durationMs", duration)
              .put("completed", position >= duration - 10000 || position >= duration * 0.98)
              .put("updatedAt", clock);
      prefs.edit().putString(key, row.toString()).putLong("_clock", clock).apply();
      WatchSync.request(c, false);
    } catch (Exception ignored) {
    }
  }

  static synchronized void removeTitle(Context c,String imdb)throws Exception{
    if(!imdb.matches("tt[0-9]+"))throw new IllegalArgumentException("Invalid title");
    long clock=Math.max(System.currentTimeMillis(),c.getSharedPreferences(preferenceName,0).getLong("_clock",0)+1);JSONObject rows=records(c);
    for(Iterator<String> it=rows.keys();it.hasNext();)clock=Math.max(clock,rows.getJSONObject(it.next()).getLong("updatedAt")+1);
    merge(c,new JSONObject().put("removed:"+imdb,new JSONObject().put("positionMs",0).put("durationMs",0).put("updatedAt",clock)));WatchSync.request(c,true);
  }

  static synchronized JSONObject records(Context c) throws Exception {
    JSONObject out = new JSONObject();
    for (Map.Entry<String, ?> entry : c.getSharedPreferences(preferenceName, 0).getAll().entrySet())
      if (entry.getValue() instanceof String)
        try {
          out.put(entry.getKey(), new JSONObject((String) entry.getValue()));
        } catch (JSONException ignored) {
        }
    return WatchSyncCrypto.clean(out);
  }

  static synchronized void merge(Context c, JSONObject incoming) throws Exception {
    JSONObject merged = WatchSyncCrypto.merge(records(c), incoming);
    SharedPreferences.Editor edit = c.getSharedPreferences(preferenceName, 0).edit();
    for(String key:c.getSharedPreferences(preferenceName,0).getAll().keySet())if(key.matches("(movie|episode|removed):tt[0-9]+.*")&&!merged.has(key))edit.remove(key);
    long observed = c.getSharedPreferences(preferenceName, 0).getLong("_clock", 0);
    for (Iterator<String> it = merged.keys(); it.hasNext(); ) {
      String key = it.next();
      edit.putString(key, merged.getJSONObject(key).toString());
      observed = Math.max(observed, merged.getJSONObject(key).optLong("updatedAt"));
    }
    edit.putLong("_clock", observed).apply();
  }
}
