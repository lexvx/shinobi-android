package ru.lex42.shinobiviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Stop native playback off main, detach Android views on main, then release native ownership.
 * No phase waits for another executor. Closing releases the engine only after all players finish. */
public final class PlayerDisposalQueue {
 // A failed native teardown has unknown ownership. Keep it alive, never finalize an active surface.
 private static final Set<PlayerDisposalQueue> QUARANTINED=ConcurrentHashMap.newKeySet();
 private final List<Runnable[]> retainedFailures=new ArrayList<>();
 private final Executor nativeExecutor,uiExecutor;
 private final Consumer<Throwable> failure;
 private int pending;
 private boolean closed,engineScheduled;
 private Runnable engineRelease;
 public PlayerDisposalQueue(Executor nativeExecutor,Executor uiExecutor,Consumer<Throwable> failure){this.nativeExecutor=nativeExecutor;this.uiExecutor=uiExecutor;this.failure=failure;}
 public synchronized void retire(Runnable stop,Runnable detach,Runnable release){
  if(closed)throw new IllegalStateException("Disposal queue is closed");
  pending++;Runnable[] ownership={stop,detach,release};
  nativeExecutor.execute(()->{if(!attempt(stop)){finished(ownership);return;}uiExecutor.execute(()->{
   if(!attempt(detach)){finished(ownership);return;}
   nativeExecutor.execute(()->finished(attempt(release)?null:ownership));
  });});
 }
 public synchronized void close(Runnable releaseEngine){if(closed)return;closed=true;engineRelease=releaseEngine;scheduleEngineIfReady();}
 private boolean attempt(Runnable action){try{action.run();return true;}catch(Throwable error){try{failure.accept(error);}catch(Throwable ignored){}return false;}}
 private synchronized void finished(Runnable[] unsafeOwnership){if(unsafeOwnership!=null){retainedFailures.add(unsafeOwnership);QUARANTINED.add(this);}pending--;scheduleEngineIfReady();}
 private void scheduleEngineIfReady(){if(closed&&pending==0&&retainedFailures.isEmpty()&&!engineScheduled){engineScheduled=true;nativeExecutor.execute(()->attempt(engineRelease));}}
}
