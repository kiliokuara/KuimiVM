package tencentlibfekit;


import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.jvm.functions.Function1;
import kotlinx.coroutines.AbstractCoroutine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;

public class UnidbgEventLoop {
    boolean alive = true;

    // () -> Boolean, rsp = isSuspend
    private final ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();
    private final Object lock = new Object();
    Thread runThread;

    public <T> T acquire(CoroutineContext context, Function1<Continuation<T>, Object> task) {
        if (Thread.currentThread() != runThread) {
            var future = new CompletableFuture<T>();
            pushTask(() -> future.complete(acquire(context, task)));
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        var cont = new AbstractCoroutine<T>(context, false, true) {
            volatile T rsp;
            volatile Throwable err;
            volatile boolean resumed = false;

            T getRsp() {
                if (err != null) {
                    throw new RuntimeException(err);
                }
                return rsp;
            }

            @Override
            protected void onCompleted(T value) {
                rsp = value;
                resumed = true;
            }

            @Override
            protected void onCancelled(@NotNull Throwable cause, boolean handled) {
                err = cause;
                resumed = true;
            }

            @Override
            protected void afterResume(@Nullable Object state) {
                synchronized (lock) {
                    lock.notify();
                }
            }
        };

        var rsp = task.invoke(cont);
        if (rsp != IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
            return (T) rsp;
        }

        while (true) {
            if (cont.resumed) {
                return cont.getRsp();
            }
            runNextTask();
        }
    }

    private void runNextTask() {
        var nextTask = tasks.poll();
        if (nextTask != null) {
            nextTask.run();
            return;
        }

        synchronized (lock) {
            if (tasks.isEmpty()) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void pushTask(Runnable task) {
        if (task == null) return;

        tasks.push(() -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        });
        synchronized (lock) {
            lock.notify();
        }
    }

    void runLoop() {
        try {
            runThread = Thread.currentThread();
            while (alive) {
                runNextTask();
            }
        } finally {
            runThread = null;
        }
    }

    public void boot(ThreadFactory factory) {
        factory.newThread(this::runLoop).start();
    }

    public void shutdown() {
        alive = false;
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public void runTask(Runnable task) {
        acquire(EmptyCoroutineContext.INSTANCE, (c) -> {
            task.run();
            return null;
        });
    }

    public Object suspended() {
        return IntrinsicsKt.getCOROUTINE_SUSPENDED();
    }
}
