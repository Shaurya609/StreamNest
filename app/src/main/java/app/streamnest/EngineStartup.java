package app.streamnest;
/** Monotonic startup watchdog. A timed-out native call must drain before another session is started. */
final class EngineStartup {
 static boolean shutdownRace(Throwable e){if(!(e instanceof NullPointerException)||e.getMessage()==null||!e.getMessage().contains("pop_alerts"))return false;StackTraceElement[] stack=e.getStackTrace();return stack.length>0&&stack[0].getClassName().equals("org.libtorrent4j.SessionManager$5")&&stack[0].getMethodName().equals("run");}
 final java.util.function.LongSupplier clock;final java.util.function.Consumer<String> reporter;
 EngineStartup(){this(System::nanoTime);}
 EngineStartup(java.util.function.LongSupplier clock){this(clock,s->{});}
 EngineStartup(java.util.function.LongSupplier clock,java.util.function.Consumer<String> reporter){this.clock=clock;this.reporter=reporter;since=clock.getAsLong();}
 volatile String stage="Waiting for engine worker";volatile long since,limitMs=30000;volatile boolean active=true,timedOut;
 synchronized void begin(String name,long limit){reporter.accept(stage+" finished after "+elapsed()+"ms; "+name);stage=name;since=clock.getAsLong();limitMs=limit;active=true;timedOut=false;}
 synchronized void enter(String name,long limit)throws java.io.IOException{check();begin(name,limit);}
 long elapsed(){return Math.max(0,(clock.getAsLong()-since)/1000000);}
 boolean expired(){if(active&&elapsed()>=limitMs&&!timedOut){timedOut=true;reporter.accept("Startup stalled during "+stage+" after "+elapsed()+"ms");}return timedOut;}
 void check()throws java.io.IOException{if(expired())throw new java.io.IOException("Torrent startup timed out during "+stage.toLowerCase(java.util.Locale.ROOT)+". Retry after cleanup finishes.");}
 synchronized void done(){active=false;}
 String status(){long seconds=elapsed()/1000;if(expired())return "Startup stalled: "+stage+" · "+seconds+"s. Waiting for safe cleanup; if this persists, close and restart StreamNest.";return stage+" · "+seconds+"s";}
}
