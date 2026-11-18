package app.streamnest;

import android.os.Bundle;
import java.util.*;

/** Measures the release-label workload implicated by the projector ANR. No network or user history. */
final class SourceRankingRegression {
  static Bundle run() {
    List<Catalog.Source> rows = new ArrayList<>();
    for (int n = 0; n < 2000; n++) {
      Catalog.Source s = new Catalog.Source("1080p " + (n % 2 == 0 ? "HEVC" : "H264"), "source" + n, n % 70);
      s.release = "Black Clover S01E121 [Multiple Subtitle] [ENG][ITA][JPN][KOR]";
      if (n % 17 == 0) s.release = "Black Clover S01E121 AAC ENG ITA Sub.ENG";
      rows.add(s);
    }
    long before = System.nanoTime();
    List<Catalog.Source> sorted = new ArrayList<>(rows);
    sorted.sort(Comparator.<Catalog.Source>comparingInt(SourceRankingRegression::oldAudio)
      .thenComparingInt(s -> s.label.toLowerCase(Locale.ROOT).matches(".*(265|hevc|av1).*") ? 1 : 0)
      .thenComparing(Comparator.comparingInt((Catalog.Source s) -> s.seeds).reversed()).thenComparing(s -> s.url));
    long oldMs = (System.nanoTime() - before) / 1000000;
    before = System.nanoTime();
    Catalog.Source actual = PlaybackPlan.episodeBest(rows, "Auto", true);
    long newMs = (System.nanoTime() - before) / 1000000;
    if (actual != sorted.get(0)) throw new AssertionError("Ranking changed selected source");
    Bundle result = new Bundle();
    result.putString("result", "PASS: 2000-source ranking preserves selection; old=" + oldMs + "ms new=" + newMs + "ms");
    return result;
  }
  static int oldAudio(Catalog.Source s) {
    String t = SourcePolicy.text(s).replaceAll("\\s+", " ")
      .replaceAll("(?i)\\[(?:multiple\\s+subtitles?|multi[- ]?subs?)\\].*$", " ")
      .replaceAll("(?i)\\bsubs?(?:titles?)?[ ._-]*(?:eng(?:lish)?|ita|jpn|kor)\\b", "").toLowerCase(Locale.ROOT);
    boolean english = t.matches(".*\\b(eng|english)\\b.*"), foreign = t.matches(".*\\b(ita|italian|rus|russian|french|fra|ger|german|spa|spanish)\\b.*");
    return foreign && !english ? 3 : english ? 0 : 1;
  }
}
