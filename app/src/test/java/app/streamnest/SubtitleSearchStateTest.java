package app.streamnest;
import org.junit.Test;import static org.junit.Assert.*;
public class SubtitleSearchStateTest {
 @Test public void languageChangeSupersedesSearchWithoutLettingLateCompletionClearNewWork(){SubtitleSearchState state=new SubtitleSearchState();long original=state.begin(true);long hindi=state.begin(false);assertFalse(state.finish(original));assertEquals(0,state.begin(true));assertTrue(state.finish(hindi));assertTrue(state.begin(true)>hindi);}
 @Test public void episodeChangeInvalidatesOldCompletion(){SubtitleSearchState state=new SubtitleSearchState();long old=state.begin(false);state.reset();long current=state.begin(true);assertFalse(state.finish(old));assertEquals(0,state.begin(true));assertTrue(state.finish(current));}
}
