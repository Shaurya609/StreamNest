package app.streamnest;
/** UI-thread search ownership: manual language changes supersede automatic work. */
final class SubtitleSearchState {
 private long serial; private boolean busy;
 long begin(boolean automatic){if(automatic&&busy)return 0;busy=true;return ++serial;}
 boolean finish(long request){if(request!=serial)return false;busy=false;return true;}
 void reset(){serial++;busy=false;}
}
