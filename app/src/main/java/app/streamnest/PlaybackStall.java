package app.streamnest;
/** Distinguish stalled playback from deliberate pause/seek, startup and natural ending. */
final class PlaybackStall {
 long position=-1,advancedAt;boolean reported;
 boolean sample(long now,long current,long length,boolean eligible){if(!eligible||length-current<=5000||position<0||Math.abs(current-position)>=500){position=current;advancedAt=now;reported=false;return false;}if(!reported&&now-advancedAt>=45000){reported=true;return true;}return false;}
}
