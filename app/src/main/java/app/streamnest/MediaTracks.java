package app.streamnest;
import java.util.*;
/** Runtime track labels determine language; catalog/source labels are not evidence. */
final class MediaTracks {
 static boolean language(String code,String name,String wanted){code=String.valueOf(code).toLowerCase(Locale.ROOT);name=String.valueOf(name).toLowerCase(Locale.ROOT);if(wanted.equals("en"))return code.equals("en")||code.equals("eng")||name.matches(".*\\benglish\\b.*");if(wanted.equals("hi"))return code.equals("hi")||code.equals("hin")||name.matches(".*\\bhindi\\b.*");return code.equals(wanted);}
 static boolean commentary(String name){return String.valueOf(name).toLowerCase(Locale.ROOT).matches(".*\\b(commentary|descriptive|description)\\b.*");}
 static boolean partial(String name){return !String.valueOf(name).toLowerCase(Locale.ROOT).matches(".*\\b(full|dialogue)\\b.*")&&String.valueOf(name).toLowerCase(Locale.ROOT).matches(".*\\b(signs|songs|forced)\\b.*");}
}
