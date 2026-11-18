package app.streamnest;
import java.util.*;import org.json.*;
/** Release hints rank candidates; actual decoded tracks establish language availability. */
final class SourcePolicy {
 static String text(Catalog.Source s){return s.filename+" "+s.release+" "+s.label;}
 static int audioRank(Catalog.Source s){String t=text(s).replaceAll("\\s+"," ").replaceAll("(?i)\\bsubs?(?:titles?)?[ ._-]*(?:eng(?:lish)?|ita|jpn|kor)\\b","").toLowerCase(Locale.ROOT);boolean english=t.matches(".*\\b(eng|english)\\b.*"),foreign=t.matches(".*\\b(ita|italian|rus|russian|french|fra|ger|german|spa|spanish)\\b.*");return foreign&&!english?3:english?0:1;}
 static boolean valid(Catalog.Source s,JSONObject row){String name=s.filename.isEmpty()?s.release:s.filename;return !EpisodeFiles.hasNumber(name)||EpisodeFiles.matches(name,row.optInt("season"),row.optInt("episode"),row.optInt("absolute"));}
}
