package app.streamnest;
import org.junit.Test;import static org.junit.Assert.*;
public class EngineBudgetTest {
 @Test public void boundedWorkersAndPromotion(){assertEquals(2,EngineBudget.diskThreads(true,true));assertEquals(2,EngineBudget.diskThreads(true,false));assertEquals(4,EngineBudget.diskThreads(false,true));assertEquals(10,EngineBudget.diskThreads(false,false));}
 @Test public void statusCadenceRetainsStartupFeedback(){assertEquals(500,WorkerCadence.delay(true,true));assertEquals(500,WorkerCadence.delay(true,false));assertEquals(1000,WorkerCadence.delay(false,false));assertEquals(2000,WorkerCadence.delay(false,true));}
}
