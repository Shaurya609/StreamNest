package app.streamnest;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Deletes one recorded app cache entry without following symbolic links. */
final class CachePurger {
  static void deleteEntry(File root, File entry) throws IOException {
    Path base = root.getCanonicalFile().toPath();
    File parent = entry.getAbsoluteFile().getParentFile();
    if (parent == null || !parent.getCanonicalFile().toPath().equals(base))
      throw new IOException("Cache entry outside its folder");
    Path target = base.resolve(entry.getName()).normalize();
    if (!target.getParent().equals(base)) throw new IOException("Cache entry outside its folder");
    if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
    Files.walkFileTree(
        target,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException error)
              throws IOException {
            if (error != null) throw error;
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }
}
