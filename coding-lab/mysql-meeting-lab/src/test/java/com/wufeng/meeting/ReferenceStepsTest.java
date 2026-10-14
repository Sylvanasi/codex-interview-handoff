package com.wufeng.meeting;
import com.wufeng.meeting.answer.ReferenceSteps;
import com.wufeng.meeting.model.Models.Outcome;
import com.wufeng.meeting.service.MockNotificationClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class ReferenceStepsTest {
    ReferenceSteps steps(MockNotificationClient client) {return new ReferenceSteps(new JdbcTemplate(),new TransactionTemplate(),client);}
    @Test void rejectionIsImmediateAndDoesNotRunOnCaller() throws Exception {
        var steps=steps(new MockNotificationClient());var rejected=new AtomicInteger();var pool=steps.createPool(1,1,1,rejected);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger called=new AtomicInteger();
        try {
            pool.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            assertTrue(entered.await(3,TimeUnit.SECONDS));pool.submit(called::incrementAndGet);
            assertThrows(RejectedExecutionException.class,()->pool.submit(called::incrementAndGet));
            assertEquals(1,rejected.get());assertEquals(0,called.get());
            // 同一个生产parallel方法也要将提交拒绝转为该人的局部结果。
            var outcomes=steps.parallel(List.of(1L,2L),1,false,pool);
            assertEquals(2,outcomes.stream().filter(o->o.status().equals("REJECTED")).count());
        } finally {release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
    }
    @Test void failureIsIsolatedAndSharedPoolStaysUsable() throws Exception {
        var steps=steps(new MockNotificationClient());var pool=steps.createPool(2,2,8,new AtomicInteger());
        try {
            var outcomes=steps.parallel(List.of(1L,17L,18L),1,true,pool);
            assertEquals(2,outcomes.stream().filter(o->o.status().equals("SUCCESS")).count());
            assertEquals(1,outcomes.stream().filter(o->o.status().equals("FAILED")).count());
            assertFalse(pool.isShutdown());assertEquals(42,pool.submit(()->42).get());
            var bad=pool.submit(()->{throw new IllegalArgumentException("expected");});
            assertInstanceOf(IllegalArgumentException.class,assertThrows(ExecutionException.class,bad::get).getCause());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
    }
    @Test void interruptCancelsThisRequestButKeepsSharedPool() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),held=new CountDownLatch(1);
        var steps=steps(new MockNotificationClient(){
            @Override public Outcome send(long id,int attempt,boolean inject) throws InterruptedException {
                entered.countDown();held.await();return new Outcome(id,"SUCCESS",attempt);
            }
        });
        var pool=steps.createPool(1,1,8,new AtomicInteger());AtomicBoolean flag=new AtomicBoolean();AtomicReference<Throwable> error=new AtomicReference<>();
        Thread caller=new Thread(()->{
            try {steps.parallel(List.of(1L,2L,3L),1,false,pool);error.set(new AssertionError("预期中断"));}
            catch(InterruptedException expected){flag.set(Thread.currentThread().isInterrupted());}
            catch(Throwable e){error.set(e);}
        });
        try {
            caller.start();assertTrue(entered.await(3,TimeUnit.SECONDS));caller.interrupt();caller.join(3000);
            assertFalse(caller.isAlive());assertTrue(flag.get());assertNull(error.get());
            assertFalse(pool.isShutdown());assertEquals(42,pool.submit(()->42).get(3,TimeUnit.SECONDS));
            assertTrue(pool.getQueue().isEmpty());
        } finally {held.countDown();caller.interrupt();pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
    }
}
