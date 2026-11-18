package app.streamnest;
import java.util.*;import org.json.*;
/** Release hints rank candidates; actual decoded tracks establish language availability. */
final class SourcePolicy {
 static final java.util.regex.Pattern SPACE=java.util.regex.Pattern.compile("\\s+"), SUB_LIST=java.util.regex.Pattern.compile("(?i)\\[(?:multiple\\s+subtitles?|multi[- ]?subs?)\\].*$"), SUB_LANGUAGE=java.util.regex.Pattern.compile("(?i)\\bsubs?(?:titles?)?[ ._-]*(?:eng(?:lish)?|ita|jpn|kor)\\b"), ENGLISH=java.util.regex.Pattern.compile("(?i)\\b(eng|english)\\b"), FOREIGN=java.util.regex.Pattern.compile("(?i)\\b(ita|italian|rus|russian|french|fra|ger|german|spa|spanish)\\b");
 static String text(Catalog.Source s){return s.filename+" "+s.release+" "+s.label;}
 static int audioRank(Catalog.Source s){String t=SUB_LANGUAGE.matcher(SUB_LIST.matcher(SPACE.matcher(text(s)).replaceAll(" ")).replaceAll(" ")).replaceAll("");boolean english=ENGLISH.matcher(t).find(),foreign=FOREIGN.matcher(t).find();return foreign&&!english?3:english?0:1;}
 static boolean valid(Catalog.Source s,JSONObject row){String name=s.filename.isEmpty()?s.release:s.filename;return !EpisodeFiles.hasNumber(name)||EpisodeFiles.matches(name,row.optInt("season"),row.optInt("episode"),row.optInt("absolute"));}
}
