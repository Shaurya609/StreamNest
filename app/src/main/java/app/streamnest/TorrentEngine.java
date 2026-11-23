package app.streamnest;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.util.*;
import org.libtorrent4j.*;
import org.libtorrent4j.alerts.*;

class TorrentEngine implements AutoCloseable {
  volatile String message = "Starting torrent engine…", url;
  volatile boolean closed;
  volatile boolean storageFailure;
  volatile long internalFreeMb=-1,cacheFreeMb=-1;
  ContextStorageCheck storageCheck;
  interface ContextStorageCheck {void check() throws IOException;}
  long storageCheckedAt;
  final EngineStartup startup=new EngineStartup(System::nanoTime,s->{android.util.Log.i("StreamNestStartup",s);Diagnostics.startup(s);});
  boolean lite, publicMetadataLookup, episodeRequest;
  volatile boolean preloadOnly;
  volatile boolean verifiedComplete;
  int fileIndex = -1, requestedSeason, requestedEpisode, absoluteEpisode;
  String expectedFilename = "", sourceUrl, episodeIdentity;
  volatile TorrentHandle handle;
  SessionManager session;
  TorrentInfo info;
  File root, video;
  volatile long size, offset;
  volatile long workerPulse=System.nanoTime();
  volatile String torrentFingerprint="",cachedMovieHash="";
  double resumeFraction;
  void pulse(){workerPulse=System.nanoTime();}
  String fingerprint(){return torrentFingerprint;}
  volatile boolean budgetChanged;
  void promote(){preloadOnly=false;budgetChanged=true;}
  final RangeTrace ranges=new RangeTrace(System::nanoTime);
  volatile String rangeReport="{}",diagnosticSeekKind="none";
  volatile int diagnosticSeekPercent=-1;
  volatile long receivedBytes;
  volatile int diagnosticPeers,diagnosticKbps,diagnosticPercent,diagnosticError;
  volatile int sourceTrackers,restoredTrackers,trackerErrors;
  volatile long trackerErrorCode;
  long diagnosticSampleAt;
  volatile String lastStatusText="";
  volatile boolean statusPending;
  volatile long statusRequestAt,statusGeneration;
  int pieceLength;
  final PieceAvailability available = new PieceAvailability();
  final java.util.concurrent.atomic.AtomicBoolean startupHints=new java.util.concurrent.atomic.AtomicBoolean(true);
  NanoHTTPD server;
  final java.util.concurrent.CountDownLatch resumeSaved =
      new java.util.concurrent.CountDownLatch(1);

  void start(String source, File directory) throws Exception {
    startup.enter("Preparing torrent folder",20000);
    pulse();
    root = directory;
    sourceTrackers=MagnetTrackers.urls(source).size();
    if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create download folder");
    startup.enter("Creating torrent session",30000);
    session = new SafeTorrentSession();
    session.addListener(
        new AlertListener() {
          public int[] types() {
            return new int[] {
              AlertType.ADD_TORRENT.swig(),
              AlertType.TRACKER_ERROR.swig(),
              AlertType.FILE_ERROR.swig(),
              AlertType.STATE_UPDATE.swig(),
              AlertType.PIECE_FINISHED.swig(),
              AlertType.HASH_FAILED.swig(),
              AlertType.SAVE_RESUME_DATA.swig(),
              AlertType.SAVE_RESUME_DATA_FAILED.swig()
            };
          }

          public void alert(Alert<?> a) {
            if(a instanceof StateUpdateAlert){try{if(!closed)for(TorrentStatus s:((StateUpdateAlert)a).status())acceptStatus(s,statusGeneration);}finally{statusPending=false;}}
            if(a instanceof FileErrorAlert){storageFailure=true;diagnosticError=((FileErrorAlert)a).error().getValue();message="The selected cache could not read or write video data. Check the USB connection, folder access, free space and drive format in Playback & storage, then retry.";closed=true;url=null;}
            if(a instanceof TrackerErrorAlert){trackerErrors=Math.min(1000000,trackerErrors+1);trackerErrorCode=Math.abs((long)((TrackerErrorAlert)a).error().getValue());}
            if (a instanceof PieceFinishedAlert) available.finished(((PieceFinishedAlert)a).pieceIndex());
            if (a instanceof HashFailedAlert) available.failed(((HashFailedAlert)a).pieceIndex());
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
    settings.setInteger(org.libtorrent4j.swig.settings_pack.int_types.aio_threads.swigValue(),EngineBudget.diskThreads(lite,preloadOnly));
    if(lite){settings.setInteger(org.libtorrent4j.swig.settings_pack.int_types.mmap_file_size_cutoff.swigValue(),Integer.MAX_VALUE);settings.setInteger(org.libtorrent4j.swig.settings_pack.int_types.disk_write_mode.swigValue(),org.libtorrent4j.swig.settings_pack.mmap_write_mode_t.always_pwrite.swigValue());}
    startup.enter("Starting torrent engine",45000);
    session.start(new SessionParams(settings));
    startup.check();
    startup.done();
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
              saved.isFile() && saved.length() <= 8388608
                  ? java.nio.file.Files.readAllBytes(saved.toPath())
                  : null;
          if (bytes != null) {
            TorrentInfo cached = new TorrentInfo(bytes);
            if (cached.isValid() && cached.infoHash().toHex().equalsIgnoreCase(expected))
              info = cached;
          }
          if (info == null) {
            message = "Fetching torrent metadata…";
            bytes = Net.bytes(TorrentMetadata.endpoint(expected), 8388608, 4000, 6000);
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
      pulse();Thread.sleep(250);
    }
    if (closed) return;
    info = handle.torrentFile();
    if (info == null) throw new IOException("Torrent metadata unavailable");
    torrentFingerprint=info.infoHash().toHex();
    restoredTrackers=MagnetTrackers.apply(handle,source,info.isPrivate());
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
    if(restoredTrackers>0)handle.forceReannounce();
    for(int[] range:StartupPieces.ranges(size,offset,pieceLength,resumeFraction))
      for(int piece=range[0];piece<=range[1];piece++)handle.setPieceDeadline(piece,range[2]+(piece-range[0])*150);
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
            PieceStream traced=null;
            try {
              InputStream input =
                  req.getMethod() == Method.HEAD
                      ? new ByteArrayInputStream(new byte[0])
                      : new PieceStream(range.start, range.end);
              if(input instanceof PieceStream){PieceStream stream=(PieceStream)input;traced=stream;ranges.state(stream.rangeId,"prioritizing");if(startupHints.compareAndSet(true,false))handle.clearPieceDeadlines();prioritize(range.start);}
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
              if(traced!=null)try{traced.close();}catch(IOException ignored){}
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
      if(storageCheck!=null&&System.nanoTime()-storageCheckedAt>5000000000L){storageCheckedAt=System.nanoTime();storageCheck.check();}
      if(budgetChanged){budgetChanged=false;session.applySettings(new SettingsPack().setInteger(org.libtorrent4j.swig.settings_pack.int_types.aio_threads.swigValue(),EngineBudget.diskThreads(lite,preloadOnly)));}
      message = updateStatus();
      if (url == null && startupReady()) url = readyUrl;
      if(cachedMovieHash.isEmpty()&&url!=null)cachedMovieHash=movieHash();
      pulse();
      if(preloadOnly && root.getUsableSpace()<256L*1024*1024)
        throw new IOException("Preloading stopped to keep storage available");
      Thread.sleep(1000);
    }
  }

  // Only the engine worker checks native handles. UI/preload reads this published result.
  boolean complete() { return !closed && verifiedComplete; }

  void verifyCompletion(float progress) {
    if (verifiedComplete || closed || size <= 0 || pieceLength <= 0 || !Float.isFinite(progress) || progress < 1) return;
    int first=(int)(offset/pieceLength), last=(int)((offset+size-1)/pieceLength);
    for(int piece=first;piece<=last;piece++)if(!available.has(piece))return;
    verifiedComplete=true;
  }

  boolean startupReady() {
    if (closed || handle == null || !handle.isValid() || size <= 0) return false;
    return StartupBuffer.ready(size, offset, pieceLength, available::has);
  }

  void prioritize(long position) {
    if (closed || handle == null || !handle.isValid()) return;
    int start = (int) ((offset + position) / pieceLength),
        last = (int) ((offset + size - 1) / pieceLength);
    for (int p = start; p <= Math.min(last, start + (lite ? 16 : 24)); p++)
      handle.setPieceDeadline(p, (p - start) * 150);
  }

  String status() {
    return startup.active?startup.status():message;
  }

  String updateStatus() {
    try {
      // post_torrent_updates queues work instead of blocking this worker in handle.status().
      // A slow USB/native status operation can no longer prevent its control heartbeat.
      long now=System.nanoTime();
      // Never overlap queries: the generation belongs to this one bitfield request.
      if(statusPending&&now-statusRequestAt>60000000000L){message="Torrent status request timed out. Stop and retry after checking the selected storage.";closed=true;url=null;return message;}
      if(session!=null&&handle!=null&&!statusPending){
        statusPending=true;statusRequestAt=now;statusGeneration=available.generation();
        session.swig().post_torrent_updates(TorrentHandle.QUERY_PIECES);
      }
    }catch(Exception ignored){statusPending=false;}
    return lastStatusText.isEmpty()?message:lastStatusText;
  }

  void acceptStatus(TorrentStatus s,long generation){
    if(closed)return;
    try{
        PieceIndexBitfield pieces = s.pieces();
        java.util.BitSet snapshot = new java.util.BitSet();
        for (int i=0;i<pieces.size();i++) if(pieces.getBit(i)) snapshot.set(i);
        available.snapshot(snapshot, generation);
        verifyCompletion(s.progress());
        receivedBytes = s.totalWantedDone();
        diagnosticPeers=Math.max(0,s.numPeers());diagnosticKbps=Math.max(0,s.downloadRate()/1024);diagnosticPercent=Math.max(0,Math.min(100,Math.round(s.progress()*100)));diagnosticError=s.errorCode().getValue();
        if (s.errorCode().getValue() != 0) {lastStatusText="Torrent error: " + s.errorCode().getMessage();return;}
        lastStatusText=String.format(
            Locale.ROOT,
            "%s · %.1f%% · %.0f KB/s · %d peers",
            s.state().toString().toLowerCase(Locale.ROOT).contains("check")
                ? "Checking cache"
                : url == null ? diagnosticPeers==0?"Waiting for peers":"Buffering video headers" : "Downloading",
            s.progress() * 100,
            s.downloadRate() / 1024.0,
            s.numPeers());
    }catch(Exception ignored){}
  }

  String movieHash() {
    try {
      if (closed || handle == null || video == null || size < 131072) return "";
      for (long edge : new long[] {0, size - 65536}) {
        int first = (int) ((offset + edge) / pieceLength),
            last = (int) ((offset + edge + 65535) / pieceLength);
        for (int p = first; p <= last; p++) if (!available.has(p)) return "";
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

  org.json.JSONObject rangeSnapshot(){org.json.JSONObject d=ranges.snapshot();try{d.put("sourceTrackers",sourceTrackers).put("restoredTrackers",restoredTrackers).put("trackerErrors",trackerErrors).put("trackerErrorCode",trackerErrorCode);if(size>0&&pieceLength>=16384){java.util.Set<Integer> needed=new java.util.HashSet<>();for(int[] range:StartupPieces.ranges(size,offset,pieceLength,0))for(int n=range[0];n<=range[1];n++)needed.add(n);int ready=0;for(int n:needed)if(available.has(n))ready++;d.put("startupPiecesRequired",needed.size()).put("startupPiecesReady",ready).put("pieceSizeKb",pieceLength/1024);}}catch(org.json.JSONException ignored){}return d;}

  final class PieceStream extends InputStream {
    long pos, end;
    RandomAccessFile file;
    volatile boolean ended;
    int priorityPiece = -1;
    final long rangeId;

    PieceStream(long s, long e) {
      pos = s;
      end = e;
      rangeId=ranges.open(s,e,size);
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
      long waitUntil = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
      // Hundreds of small HTTP reads can belong to one piece; set its window once.
      if (piece != priorityPiece) { ranges.state(rangeId,"prioritizing");prioritize(pos); priorityPiece = piece; }
      while (!closed && !ended && !available.has(piece)) {
        ranges.waiting(rangeId,pos);
        if (System.nanoTime() > waitUntil){
          ranges.state(rangeId,"timeout");
          throw new IOException("Timed out buffering video");
        }
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException(e);
        }
      }
      if (closed || ended) throw new IOException("Stream stopped");
      ranges.state(rangeId,"reading");
      if (file == null) file = new RandomAccessFile(video, "r");
      int n =
          (int) Math.min(Math.min(len, end - pos + 1), pieceLength - (offset + pos) % pieceLength);
      file.seek(pos);
      int got = file.read(b, off, n);
      if (got < 0) throw new IOException("Downloaded piece is unavailable on disk");
      pos += got;
      ranges.served(rangeId,pos,got);
      return got;
    }

    public void close() throws IOException {
      ended = true;
      ranges.close(rangeId);
      if (file != null) file.close();
    }
  }
}
