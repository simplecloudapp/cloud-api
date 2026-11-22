package app.simplecloud.api.testing.internal;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.testing.network.TestNetwork;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

public final class TestNetworkImpl implements TestNetwork {

    private static final int UNLIMITED = -1;

    private final List<Rule> rules = new ArrayList<>();

    @Override
    public TestNetwork failNext(Class<?> api, String method) {
        return failNext(api, method, injectedFailure(api, method));
    }

    @Override
    public TestNetwork failNext(Class<?> api, String method, RuntimeException error) {
        return add(new Rule(api, requireMethod(api, method), Objects.requireNonNull(error, "error"), Duration.ZERO, 1));
    }

    @Override
    public TestNetwork failAlways(Class<?> api, String method) {
        return failAlways(api, method, injectedFailure(api, method));
    }

    @Override
    public TestNetwork failAlways(Class<?> api, String method, RuntimeException error) {
        return add(new Rule(
                api, requireMethod(api, method), Objects.requireNonNull(error, "error"), Duration.ZERO, UNLIMITED));
    }

    @Override
    public TestNetwork delay(Duration delay) {
        return add(new Rule(null, null, null, requireDelay(delay), UNLIMITED));
    }

    @Override
    public TestNetwork delay(Class<?> api, String method, Duration delay) {
        return add(new Rule(api, requireMethod(api, method), null, requireDelay(delay), UNLIMITED));
    }

    @Override
    public synchronized void clear() {
        rules.clear();
    }

    public <T> T wrap(Class<T> type, T target) {
        Object proxy =
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (instance, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return invoke(target, method, args);
                    }
                    RuntimeException failure = consumeFailure(type, method.getName());
                    Duration delay = delayFor(type, method.getName());
                    boolean async = CompletableFuture.class.isAssignableFrom(method.getReturnType());
                    if (!async) {
                        if (failure != null) {
                            throw failure;
                        }
                        return invoke(target, method, args);
                    }
                    if (delay.isZero()) {
                        return callAsync(target, method, args, failure);
                    }
                    return CompletableFuture.runAsync(
                                    () -> {},
                                    CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS))
                            .thenCompose(ignored -> callAsync(target, method, args, failure));
                });
        return type.cast(proxy);
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<Object> callAsync(
            Object target, Method method, Object[] args, @Nullable RuntimeException failure) {
        if (failure != null) {
            return CompletableFuture.failedFuture(failure);
        }
        return (CompletableFuture<Object>) invoke(target, method, args);
    }

    private synchronized TestNetwork add(Rule rule) {
        rules.add(rule);
        return this;
    }

    private synchronized @Nullable RuntimeException consumeFailure(Class<?> api, String method) {
        for (Rule rule : rules) {
            if (rule.error != null && rule.matches(api, method)) {
                if (rule.remaining != UNLIMITED && --rule.remaining == 0) {
                    rules.remove(rule);
                }
                return rule.error;
            }
        }
        return null;
    }

    private synchronized Duration delayFor(Class<?> api, String method) {
        return rules.stream()
                .filter(rule -> rule.error == null && rule.matches(api, method))
                .map(rule -> rule.delay)
                .max(Duration::compareTo)
                .orElse(Duration.ZERO);
    }

    private static Object invoke(Object target, Method method, Object[] args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String requireMethod(Class<?> api, String method) {
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(method, "method");
        boolean exists = Arrays.stream(api.getMethods())
                .anyMatch(candidate -> candidate.getName().equals(method));
        if (!exists) {
            throw new IllegalArgumentException(api.getSimpleName() + " has no method '" + method + "'");
        }
        return method;
    }

    private static Duration requireDelay(Duration delay) {
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must be >= 0");
        }
        return delay;
    }

    private static CloudApiException injectedFailure(Class<?> api, String method) {
        return new CloudApiException(
                CloudApiException.Reason.UNAVAILABLE,
                503,
                "Injected failure for " + api.getSimpleName() + "." + method,
                null);
    }

    private static final class Rule {

        private final @Nullable Class<?> api;
        private final @Nullable String method;
        private final @Nullable RuntimeException error;
        private final Duration delay;
        private int remaining;

        private Rule(
                @Nullable Class<?> api,
                @Nullable String method,
                @Nullable RuntimeException error,
                Duration delay,
                int remaining) {
            this.api = api;
            this.method = method;
            this.error = error;
            this.delay = delay;
            this.remaining = remaining;
        }

        private boolean matches(Class<?> api, String method) {
            return (this.api == null || this.api == api) && (this.method == null || this.method.equals(method));
        }
    }
}
