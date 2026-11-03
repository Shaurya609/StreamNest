package app.streamnest;

import java.net.*;
import java.util.*;

final class TorrentMetadata {
  static String hash(String magnet) {
    if (magnet == null || !magnet.startsWith("magnet:?")) return "";
    for (String part : magnet.substring(8).split("&")) {
      int equals = part.indexOf('=');
      if (equals < 0) continue;
      try {
        String key = URLDecoder.decode(part.substring(0, equals), "UTF-8"),
            value = URLDecoder.decode(part.substring(equals + 1), "UTF-8");
        if (key.equals("xt") && value.matches("(?i)urn:btih:[a-f0-9]{40}"))
          return value.substring(9).toLowerCase(Locale.ROOT);
      } catch (Exception ignored) {
      }
    }
    return "";
  }

  static String endpoint(String hash) {
    if (!hash.matches("[a-f0-9]{40}")) throw new IllegalArgumentException("Invalid torrent hash");
    return "https://itorrents.net/torrent/" + hash.toUpperCase(Locale.ROOT) + ".torrent";
  }
}
