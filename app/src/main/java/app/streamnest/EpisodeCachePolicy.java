package app.streamnest;

import java.util.*;

final class EpisodeCachePolicy {
  static List<String> obsolete(
      Map<String, String> episodes, String currentEpisode, String currentPath) {
    List<String> out = new ArrayList<>();
    if (currentEpisode == null || currentEpisode.isEmpty()) return out;
    for (Map.Entry<String, String> entry : episodes.entrySet())
      if (!entry.getKey().equals(currentPath) && !entry.getValue().equals(currentEpisode))
        out.add(entry.getKey());
    return out;
  }
}
