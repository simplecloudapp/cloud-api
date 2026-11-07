package app.simplecloud.api.internal.web;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.web.ApiException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Runs generated client calls and turns their {@link ApiException}s into {@link CloudApiException}s.
 */
public final class ApiCalls {

    private ApiCalls() {}

    public static <T> T execute(Call<T> call) {
        try {
            return call.execute();
        } catch (ApiException e) {
            throw ApiExceptions.from(e);
        }
    }

    public static <T> CompletableFuture<T> supplyAsync(Executor executor, Call<T> call) {
        return CompletableFuture.supplyAsync(() -> execute(call), executor);
    }

    public static CompletableFuture<Void> runAsync(Executor executor, Action action) {
        return CompletableFuture.runAsync(
                () -> execute(() -> {
                    action.execute();
                    return null;
                }),
                executor);
    }

    @FunctionalInterface
    public interface Call<T> {
        T execute() throws ApiException;
    }

    @FunctionalInterface
    public interface Action {
        void execute() throws ApiException;
    }
}
