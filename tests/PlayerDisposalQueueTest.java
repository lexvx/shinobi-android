package ru.lex42.shinobiviewer;
import java.util.*;
import java.util.concurrent.*;

public final class PlayerDisposalQueueTest {
 static class Manual implements Executor {final ArrayDeque<Runnable> tasks=new ArrayDeque<>();public void execute(Runnable action){tasks.add(action);}void drain(){while(!tasks.isEmpty())tasks.remove().run();}}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Manual nativeThread=new Manual(),mainThread=new Manual();List<String> log=new ArrayList<>();List<Throwable> failures=new ArrayList<>();
  PlayerDisposalQueue queue=new PlayerDisposalQueue(nativeThread,mainThread,failures::add);
  queue.retire(()->log.add("stopA"),()->log.add("detachA"),()->log.add("releaseA"));
  queue.retire(()->log.add("stopB"),()->log.add("detachB"),()->log.add("releaseB"));
  queue.close(()->log.add("engine"));queue.close(()->log.add("duplicateEngine"));
  check(log.isEmpty(),"retire/close must not run blocking work inline");
  nativeThread.drain();check(log.equals(Arrays.asList("stopA","stopB")),"UI detach must be dispatched, engine must remain alive");
  mainThread.drain();check(log.equals(Arrays.asList("stopA","stopB","detachA","detachB")),"native release must wait for UI detach");
  nativeThread.drain();check(log.equals(Arrays.asList("stopA","stopB","detachA","detachB","releaseA","releaseB","engine")),"engine last and close idempotent");
  boolean rejected=false;try{queue.retire(()->{},()->{},()->{});}catch(IllegalStateException e){rejected=true;}check(rejected,"no new resources after engine close");
  PlayerDisposalQueue errors=new PlayerDisposalQueue(nativeThread,mainThread,failures::add);
  errors.retire(()->{throw new IllegalStateException();},()->log.add("detachedAfterStopError"),()->log.add("releasedAfterStopError"));
  errors.retire(()->{},()->{throw new IllegalArgumentException();},()->log.add("unsafeRelease"));
  errors.close(()->log.add("errorEngine"));nativeThread.drain();mainThread.drain();nativeThread.drain();
  check(!log.contains("detachedAfterStopError")&&!log.contains("releasedAfterStopError"),"failed stop must retain the producer and surfaces");check(!log.contains("unsafeRelease"),"failed UI detach must not mutate Views in worker release");check(failures.size()==2,"report both phase failures");check(!log.contains("errorEngine"),"quarantined producer must keep its engine alive");
  ExecutorService real=Executors.newSingleThreadExecutor();CountDownLatch entered=new CountDownLatch(1),allowStop=new CountDownLatch(1),finished=new CountDownLatch(1);
  PlayerDisposalQueue blocking=new PlayerDisposalQueue(real,Runnable::run,error->{throw new AssertionError(error);});
  blocking.retire(()->{entered.countDown();try{allowStop.await();}catch(InterruptedException e){throw new RuntimeException(e);}},()->{},()->{});
  check(entered.await(2,TimeUnit.SECONDS),"background stop entered");blocking.close(finished::countDown);check(finished.getCount()==1,"engine must wait for blocked stop without blocking caller");allowStop.countDown();check(finished.await(2,TimeUnit.SECONDS),"shutdown completes after stop");real.shutdown();
  System.out.println("PlayerDisposalQueue: 12 lifecycle checks passed");
 }
}
