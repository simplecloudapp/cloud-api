package app.simplecloud.api.testing.internal;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class Futures {

    private Futures() {}

    public static <T> CompletableFuture<T> call(Supplier<T> supplier) {
        try {
            return CompletableFuture.completedFuture(supplier.get());
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static CompletableFuture<Void> run(Runnable runnable) {
        try {
            runnable.run();
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
