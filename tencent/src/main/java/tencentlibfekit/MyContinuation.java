package tencentlibfekit;

import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

class MyContinuation<T> implements Continuation<T> {
    private final CompletableFuture<T> future;

    public MyContinuation(CompletableFuture<T> future) {
        this.future = future;
    }

    @Override
    public void resumeWith(@NotNull Object o) {
        if (o instanceof Result.Failure) {
            future.completeExceptionally(((Result.Failure) o).exception);
        } else {
            future.complete((T) o);
        }
    }

    @Override
    public @NotNull CoroutineContext getContext() {
        return EmptyCoroutineContext.INSTANCE;
    }
}