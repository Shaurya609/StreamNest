package app.streamnest;

import java.io.*;

final class FolderPolicy {
  static boolean inside(File root, File child) throws IOException {
    String base = root.getCanonicalPath(), path = child.getCanonicalPath();
    return path.equals(base) || path.startsWith(base + File.separator);
  }

  static File child(File parent, String name) throws IOException {
    String n = name.trim();
    if (n.isEmpty()
        || n.equals(".")
        || n.equals("..")
        || n.contains("/")
        || n.contains("\\")
        || n.codePoints().anyMatch(Character::isISOControl))
      throw new IOException("Enter a folder name without slashes.");
    File out = new File(parent, n).getCanonicalFile();
    if (!out.getParentFile().equals(parent.getCanonicalFile()))
      throw new IOException("Invalid folder name");
    return out;
  }
}
