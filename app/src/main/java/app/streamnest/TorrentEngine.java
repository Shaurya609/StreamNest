package app.streamnest;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.util.*;
import org.libtorrent4j.*;
import org.libtorrent4j.alerts.*;

final class TorrentEngine implements AutoCloseable {
  volatile String message = "Starting torrent engine…", url;
  volatile boolean closed;
  boolean lite, publicMetadataLookup, episodeRequest, preloadOnly;
  boolean verifiedComplete;
  int fileIndex = -1, requestedSeason, requestedEpisode, absoluteEpisode;
  String expectedFilename = "", sourceUrl;
  volatile TorrentHandle handle;
  SessionManager session;
  TorrentInfo info;
  File root, video;
  long size, offset;
  volatile long receivedBytes;
  int pieceLength;
  NanoHTTPD server;
  final java.util.concurrent.CountDownLatch resumeSaved =
      new java.util.concurrent.CountDownLatch(1);

  void start(String source, File directory) throws Exception {
    root = directory;
    if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create download folder");
    session = new SessionManager();
    session.addListener(
        new AlertListener() {
          public int[] types() {
            return new int[] {
              AlertType.ADD_TORRENT.swig(),
              AlertType.SAVE_RESUME_DATA.swig(),
              AlertType.SAVE_RESUME_DATA_FAILED.swig()
            };
          }

          public void alert(Alert<?> a) {
            if (a instanceof SaveResumeDataAlert) {
              try {
                byte[] bytes =
                    AddTorrentParams.writeResumeDataBuf(((SaveResumeDataAlert) a).params());
                File temp = new File(root, ".streamnest.resume.tmp");
                try (FileOutputStream out = new FileOutputStream(temp)) {
                  out.write(bytes);
                }
                java.nio.file.Files.move(
                    temp.toPath(),
                    new File(root, ".streamnest.resume").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
              } catch (Exception ignored) {
              } finally {
                resumeSaved.countDown();
              }
            }
            if (a instanceof SaveResumeDataFailedAlert) resumeSaved.countDown();
            if (a instanceof AddTorrentAlert)
              handle = session.find(((AddTorrentAlert) a).handle().infoHash());
          }
        });
    SettingsPack settings =
        new SettingsPack()
            .connectionsLimit(preloadOnly ? 16 : lite ? 32 : 100)
            .maxPeerlistSize(lite ? 200 : 1000)
            .activeDownloads(1)
            .alertQueueSize(300);
    session.start();
    session.applySettings(settings);
    if (closed) return;
    message = "Connecting to peers…";
    boolean magnet = source.startsWith("magnet:?");
    android.util.Log.d("StreamNest", "Catalog metadata lookup enabled: " + publicMetadataLookup);
    if (magnet && publicMetadataLookup) {
      String expected = TorrentMetadata.hash(source);
      if (!expected.isEmpty())
        try {
          File saved = new File(root, ".streamnest.torrent");
          byte[] bytes =
              saved.isFile() && saved.length() < 4000000
                  ? java.nio.file.Files.readAllBytes(saved.toPath())
                  : null;
          if (bytes != null) {
            TorrentInfo cached = new TorrentInfo(bytes);
            if (cached.isValid() && cached.infoHash().toHex().equalsIgnoreCase(expected))
              info = cached;
          }
          if (info == null) {
            message = "Fetching torrent metadata…";
            bytes = Net.bytes(TorrentMetadata.endpoint(expected), 4000000, 4000, 6000);
            TorrentInfo candidate = new TorrentInfo(bytes);
            if (candidate.isValid() && candidate.infoHash().toHex().equalsIgnoreCase(expected)) {
              info = candidate;
              java.nio.file.Files.write(saved.toPath(), bytes);
            }
          }
        } catch (Exception failure) {
          android.util.Log.d(
              "StreamNest",
              "Metadata cache fallback: "
                  + failure.getClass().getSimpleName()
                  + ": "
                  + failure.getMessage());
        }
    }
    if (closed) return;
    if (magnet && info == null) session.download(source, root, TorrentFlags.DEFAULT_DONT_DOWNLOAD);
    else {
      if (info == null) info = new TorrentInfo(Net.bytes(source));
      session.download(
          info,
          root,
          new File(root, ".streamnest.resume").isFile()
              ? new File(root, ".streamnest.resume")
              : null,
          Priority.array(Priority.IGNORE, info.numFiles()),
          null,
          TorrentFlags.SEQUENTIAL_DOWNLOAD);
    }
    long connectingSince = System.currentTimeMillis();
    long deadline = connectingSince + 120000;
    while (!closed && (handle == null || !handle.isValid() || !handle.status().hasMetadata())) {
      if (System.currentTimeMillis() > deadline)
        throw new IOException(
            "No torrent metadata received. Check the link and peer availability.");
      long elapsed = (System.currentTimeMillis() - connectingSince) / 1000;
      message =
          "Finding torrent metadata · "
              + elapsed
              + "s"
              + (elapsed >= 30 ? " · waiting for peers; try another source if this continues" : "");
      Thread.sleep(250);
    }
    if (closed) return;
    info = handle.torrentFile();
    if (info == null) throw new IOException("Torrent metadata unavailable");
    FileStorage files = info.files();
    List<String> paths = new ArrayList<>();
    List<Long> sizes = new ArrayList<>();
    for (int i = 0; i < files.numFiles(); i++) {
      File f = new File(root, files.filePath(i)).getCanonicalFile();
      if (!f.toPath().startsWith(root.getCanonicalFile().toPath()))
        throw new IOException("Unsafe torrent file path");
      paths.add(files.filePath(i));
      sizes.add(files.fileSize(i));
    }
    int chosen =
        EpisodeFiles.select(
            paths,
            sizes,
            fileIndex,
            expectedFilename,
            episodeRequest,
            requestedSeason,
            requestedEpisode,
            absoluteEpisode);
    android.util.Log.d("StreamNest", "Selected torrent file " + chosen + " of " + files.numFiles());
    video = new File(root, files.filePath(chosen));
    size = files.fileSize(chosen);
    offset = files.fileOffset(chosen);
    pieceLength = info.pieceLength();
    if (preloadOnly && !PreloadStorage.canFit(size,root.getUsableSpace()))
      throw new IOException("Not enough free space to preload this episode");
    Priority[] priorities = Priority.array(Priority.IGNORE, files.numFiles());
    priorities[chosen] = Priority.DEFAULT;
    handle.prioritizeFiles(priorities);
    handle.unsetFlags(TorrentFlags.AUTO_MANAGED);
    handle.unsetFlags(TorrentFlags.PAUSED);
    handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD);
    handle.resume();
    prioritize(0);
    long startupWindow = Math.min(size, 1024 * 1024);
    for (long edge : new long[] {0, size - startupWindow}) {
      int first = (int) ((offset + edge) / pieceLength);
      int last = (int) ((offset + edge + startupWindow - 1) / pieceLength);
      for (int piece = first; piece <= last; piece++)
        handle.setPieceDeadline(piece, (piece - first) * 150);
    }
    String token = "/" + UUID.randomUUID();
    server =
        new NanoHTTPD("127.0.0.1", 0) {
          public Response serve(IHTTPSession req) {
            System.out.println(
                "StreamNest HTTP " + req.getMethod() + " range=" + req.getHeaders().get("range"));
            if (!token.equals(req.getUri()))
              return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
            if (req.getMethod() != Method.GET && req.getMethod() != Method.HEAD)
              return newFixedLengthResponse(
                  Response.Status.METHOD_NOT_ALLOWED, "text/plain", "GET or HEAD required");
            ByteRange range;
            try {
              range = ByteRange.parse(req.getHeaders().get("range"), size);
            } catch (Exception e) {
              Response res =
                  newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "");
              res.addHeader("Content-Range", "bytes */" + size);
              return res;
            }
            try {
              prioritize(range.start);
              InputStream input =
                  req.getMethod() == Method.HEAD
                      ? new ByteArrayInputStream(new byte[0])
                      : new PieceStream(range.start, range.end);
              String mime =
                  video.getName().toLowerCase().endsWith("mkv") ? "video/x-matroska" : "video/mp4";
              Response res =
                  newFixedLengthResponse(
                      range.partial ? Response.Status.PARTIAL_CONTENT : Response.Status.OK,
                      mime,
                      input,
                      range.end - range.start + 1);
              res.addHeader("Accept-Ranges", "bytes");
              if (range.partial)
                res.addHeader(
                    "Content-Range", "bytes " + range.start + "-" + range.end + "/" + size);
              return res;
            } catch (Exception e) {
              return newFixedLengthResponse(
                  Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Buffering; retry playback");
            }
          }
        };
    server.start(60000, false);
    String readyUrl = "http://127.0.0.1:" + server.getListeningPort() + token;
    System.out.println("StreamNest URL " + url);
    message = "Buffering · " + video.getName();
    while (!closed) {
      if (url == null && startupReady()) url = readyUrl;
      message = updateStatus();
      if(preloadOnly && root.getUsableSpace()<256L*1024*1024)
        throw new IOException("Preloading stopped to keep storage available");
      Thread.sleep(1000);
    }
  }

  boolean complete() {
    try {
    if(closed||handle==null||!handle.isValid()||size<=0||pieceLength<=0)return false;
    if(verifiedComplete)return true;
    if(handle.status().progress()<1)return false;
    int first=(int)(offset/pieceLength),last=(int)((offset+size-1)/pieceLength);
    for(int i=first;i<=last;i++)if(!handle.havePiece(i))return false;
    return verifiedComplete=true;
    }catch(Exception stopped){return false;}
  }

  boolean startupReady() {
    if (closed || handle == null || !handle.isValid() || size <= 0) return false;
    return StartupBuffer.ready(size, offset, pieceLength, piece -> handle.havePiece(piece));
  }

  void prioritize(long position) {
    if (closed || handle == null || !handle.isValid()) return;
    int start = (int) ((offset + position) / pieceLength),
        last = (int) ((offset + size - 1) / pieceLength);
    for (int p = start; p <= Math.min(last, start + (lite ? 16 : 24)); p++)
      handle.setPieceDeadline(p, (p - start) * 150);
  }

  String status() {
    return message;
  }

  String updateStatus() {
    try {
      if (handle != null && handle.isValid()) {
        TorrentStatus s = handle.status(true);
        receivedBytes = s.totalWantedDone();
        if (s.errorCode().getValue() != 0) return "Torrent error: " + s.errorCode().getMessage();
        return String.format(
            Locale.ROOT,
            "%s · %.1f%% · %.0f KB/s · %d peers",
            s.state().toString().toLowerCase(Locale.ROOT).contains("check")
                ? "Checking cache"
                : url == null ? "Buffering video headers" : "Downloading",
            s.progress() * 100,
            s.downloadRate() / 1024.0,
            s.numPeers());
      }
    } catch (Exception ignored) {
    }
    return message;
  }

  String movieHash() {
    try {
      if (closed || handle == null || video == null || size < 131072) return "";
      for (long edge : new long[] {0, size - 65536}) {
        int first = (int) ((offset + edge) / pieceLength),
            last = (int) ((offset + edge + 65535) / pieceLength);
        for (int p = first; p <= last; p++) if (!handle.havePiece(p)) return "";
      }
      long hash = size;
      byte[] chunk = new byte[65536];
      try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(video, "r")) {
        for (long edge : new long[] {0, size - 65536}) {
          file.seek(edge);
          file.readFully(chunk);
          java.nio.ByteBuffer buffer =
              java.nio.ByteBuffer.wrap(chunk).order(java.nio.ByteOrder.LITTLE_ENDIAN);
          while (buffer.hasRemaining()) hash += buffer.getLong();
        }
      }
      return String.format(java.util.Locale.ROOT, "%016x", hash);
    } catch (Exception e) {
      return "";
    }
  }

  public synchronized void close() {
    closed = true;
    url = null;
    if (server != null) server.stop();
    if (session != null) {
      try {
        if (handle != null && handle.isValid() && handle.status().hasMetadata()) {
          handle.pause();
          handle.saveResumeData(TorrentHandle.FLUSH_DISK_CACHE.or_(TorrentHandle.SAVE_INFO_DICT));
          resumeSaved.await(2200, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
      } catch (Exception ignored) {
      }
      session.stop();
    }
  }

  final class PieceStream extends InputStream {
    long pos, end;
    RandomAccessFile file;
    boolean ended;

    PieceStream(long s, long e) {
      pos = s;
      end = e;
    }

    public int read() throws IOException {
      byte[] b = new byte[1];
      int n = read(b, 0, 1);
      return n < 0 ? -1 : b[0] & 255;
    }

    public int read(byte[] b, int off, int len) throws IOException {
      if (len == 0) return 0;
      if (pos > end || ended) return -1;
      if (closed) throw new IOException("Stream stopped");
      int piece = (int) ((offset + pos) / pieceLength);
      long waitUntil = System.currentTimeMillis() + 60000;
      prioritize(pos);
      while (!closed && !ended && !handle.havePiece(piece)) {
        if (System.currentTimeMillis() > waitUntil)
          throw new IOException("Timed out buffering video");
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException(e);
        }
      }
      if (closed || ended) throw new IOException("Stream stopped");
      if (file == null) file = new RandomAccessFile(video, "r");
      int n =
          (int) Math.min(Math.min(len, end - pos + 1), pieceLength - (offset + pos) % pieceLength);
      file.seek(pos);
      int got = file.read(b, off, n);
      if (got < 0) throw new IOException("Downloaded piece is unavailable on disk");
      pos += got;
      return got;
    }

    public void close() throws IOException {
      ended = true;
      if (file != null) file.close();
    }
  }
}
