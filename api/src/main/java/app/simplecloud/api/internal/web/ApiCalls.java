package app.simplecloud.api.internal.web;

import app.simplecloud.api.web.ApiException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Runs generated client calls and converts their {@link ApiException}s with {@link ApiExceptions#from(ApiException)}.
 */
public final class ApiCalls {

    private ApiCalls() {
    }

    public static <T> CompletableFuture<T> supplyAsync(Call<T> call, Executor executor) {
        return CompletableFuture.supplyAsync(() -> call(call), executor);
    }

    public static CompletableFuture<Void> runAsync(Action action, Executor executor) {
        return CompletableFuture.runAsync(() -> run(action), executor);
    }

    public static <T> T call(Call<T> call) {
        try {
            return call.call();
        } catch (ApiException e) {
            throw ApiExceptions.from(e);
        }
    }

    public static void run(Action action) {
        try {
            action.run();
        } catch (ApiException e) {
            throw ApiExceptions.from(e);
        }
    }

    @FunctionalInterface
    public interface Call<T> {
        T call() throws ApiException;
    }

    @FunctionalInterface
    public interface Action {
        void run() throws ApiException;
    }
}
