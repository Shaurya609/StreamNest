package app.streamnest;

final class OrgAuth {
  static String digest(String password) {
    try {
      byte[] bytes =
          java.security.MessageDigest.getInstance("MD5")
              .digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
      return out.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  static String loginError(String status) {
    return "OpenSubtitles.org rejected this API login ("
        + status
        + "). Use your .org account username, not a .com/forum login or email address. If website"
        + " login works, the legacy API may be restricting this account. You can try Use .org"
        + " without account, or the working .com service.";
  }
}
