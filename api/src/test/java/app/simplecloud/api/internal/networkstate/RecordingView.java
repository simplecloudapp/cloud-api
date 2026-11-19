package app.simplecloud.api.internal.networkstate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A view for tests of the shell: it records every input, asks for nothing, and answers reads from a fixed copy
 * while {@link #allowAnswers} allows it, and otherwise lets them wait, for as long as {@link #waitForOwnWrite}
 * says so. It stays the same object across steps, so a test can change it while a shell runs it.
 */
final class RecordingView implements NetworkView {

    private final List<ViewInput> received = new CopyOnWriteArrayList<>();
    private final NetworkCopy copy;
    private volatile boolean answersAllowed;
    private volatile boolean waitsForOwnWrite;
    private volatile Class<? extends ViewInput> failingInput;

    RecordingView(NetworkCopy copy) {
        this.copy = copy;
    }

    static RecordingView withoutCopy() {
        return new RecordingView(null);
    }

    void allowAnswers(boolean answersAllowed) {
        this.answersAllowed = answersAllowed;
    }

    void waitForOwnWrite(boolean waitsForOwnWrite) {
        this.waitsForOwnWrite = waitsForOwnWrite;
    }

    /**
     * Makes every step of an input of {@code inputType} throw a {@link NoClassDefFoundError}, as a step of a
     * class loader that is going away does.
     */
    void failOn(Class<? extends ViewInput> inputType) {
        failingInput = inputType;
    }

    List<ViewInput> received() {
        return received;
    }

    <T extends ViewInput> List<T> received(Class<T> inputType) {
        return received.stream()
                .filter(inputType::isInstance)
                .map(inputType::cast)
                .toList();
    }

    @Override
    public ViewStep step(ViewInput input, long nowMillis) {
        Class<? extends ViewInput> failing = failingInput;
        if (failing != null && failing.isInstance(input)) {
            throw new NoClassDefFoundError("app/simplecloud/api/internal/networkstate/Gone");
        }
        received.add(input);
        return new ViewStep(this, List.of());
    }

    @Override
    public ReadRoute routeRead(long nowMillis) {
        if (answersAllowed) {
            return ReadRoute.FROM_COPY;
        }
        return waitsForOwnWrite ? ReadRoute.WAIT : ReadRoute.OVER_HTTP;
    }

    @Override
    public Optional<NetworkCopy> copy() {
        return Optional.ofNullable(copy);
    }
}
