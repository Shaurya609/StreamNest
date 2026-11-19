package app.streamnest;
import org.junit.Test;import static org.junit.Assert.*;
public class EpisodeCompletionTest {
 @Test public void roundedTailCompletesOnceAfterThreeSeconds(){EpisodeCompletion e=new EpisodeCompletion();assertFalse(e.sample(0,3167100,3167104,true));assertFalse(e.sample(2999,3167100,3167104,true));assertTrue(e.sample(3000,3167100,3167104,true));assertFalse(e.sample(4000,3167100,3167104,true));}
 @Test public void pausesAndUnderrunsDoNotComplete(){EpisodeCompletion e=new EpisodeCompletion();e.sample(0,99950,100000,true);assertFalse(e.sample(10000,99950,100000,false));assertFalse(e.sample(20000,99000,100000,true));assertFalse(e.sample(30000,50000,100000,true));assertFalse(e.sample(40000,99950,100000,true));assertFalse(e.sample(42000,99950,100000,true));}
}
