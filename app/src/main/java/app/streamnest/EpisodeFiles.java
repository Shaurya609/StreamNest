package app.streamnest;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Exact torrent member selection; an episode request never falls back to largest-in-pack. */
final class EpisodeFiles {
  static boolean video(String path) {
    return path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|avi|webm|mov|m4v|ts)$")
        && !path.toLowerCase(Locale.ROOT).matches(".*(?:^|[/\\\\._ -])sample(?:[/\\\\._ -]|$).*");
  }

  static boolean matches(String path, int season, int episode, int absolute) {
    path=path.replace('\\','/');path=path.substring(path.lastIndexOf('/')+1);
    if(Pattern.compile("(?i)s[0-9]+e[0-9]+[ -]*(?:e[0-9]+|s[0-9]+e[0-9]+)").matcher(path).find())return false;
    Matcher m = Pattern.compile("(?i)(?:^|[^a-z0-9])s(\\d{1,2})e(\\d{1,4})(?!\\d)").matcher(path);
    if (m.find())
      return Integer.parseInt(m.group(1)) == season && Integer.parseInt(m.group(2)) == episode;
    m = Pattern.compile("(?i)(?:^|[^a-z0-9])(\\d{1,2})x(\\d{1,4})(?!\\d)").matcher(path);
    if (m.find())
      return Integer.parseInt(m.group(1)) == season && Integer.parseInt(m.group(2)) == episode;
    if (absolute > 0) {
      m = Pattern.compile("\\s-\\s(\\d{1,4})(?:v\\d+)?(?=\\s|\\[|\\(|\\.)").matcher(path);
      if (m.find()) return Integer.parseInt(m.group(1)) == absolute;
    }
    return false;
  }

  static boolean hasNumber(String name){String member=new java.io.File(name.replace('\\','/')).getName();return Pattern.compile("(?i)(?:s[0-9]{1,2}e[0-9]{1,4}|[0-9]{1,2}x[0-9]{1,4}| - [0-9]{1,4})").matcher(member).find();}

  static int select(
      List<String> paths,
      List<Long> sizes,
      int explicit,
      String expectedName,
      boolean episodeRequest,
      int season,
      int episode,
      int absolute)
      throws IOException {
    if (explicit >= 0) {
      if (explicit >= paths.size() || !video(paths.get(explicit)))
        throw new IOException("Episode file index is invalid");
      if (!expectedName.isEmpty()
          && !new java.io.File(paths.get(explicit).replace('\\','/')).getName().equals(new java.io.File(expectedName.replace('\\','/')).getName()))
        throw new IOException("Episode file does not match the selected source");
      boolean named=matches(paths.get(explicit),season,episode,absolute);
      if(episodeRequest&&(!named&&hasNumber(paths.get(explicit))||!named&&paths.stream().filter(EpisodeFiles::video).count()>1))throw new IOException("Selected file does not identify the requested episode");
      return explicit;
    }
    List<Integer> videos = new ArrayList<>(), matched = new ArrayList<>();
    for (int n = 0; n < paths.size(); n++)
      if (video(paths.get(n))) {
        videos.add(n);
        if (matches(paths.get(n), season, episode, absolute)) matched.add(n);
      }
    if (episodeRequest) {
      if (matched.size() == 1) return matched.get(0);
      if (matched.isEmpty() && videos.size() == 1 && !Pattern.compile("(?i)(?:^|[^a-z0-9])(?:s[0-9]{1,2}e[0-9]{1,4}|[0-9]{1,2}x[0-9]{1,4}|- [0-9]{1,4})(?![0-9])").matcher(paths.get(videos.get(0))).find()) return videos.get(0);
      throw new IOException("Cannot uniquely identify this episode in the season pack");
    }
    return videos.stream()
        .max(Comparator.comparingLong(sizes::get))
        .orElseThrow(() -> new IOException("No supported video file"));
  }
}
