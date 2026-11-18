package app.simplecloud.api.internal.networkstate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A view for tests of the shell and of the reads it answers: it records every input, asks for nothing, and
 * answers reads from a fixed copy while {@link #answer} allows it, and lets them wait for an own write while
 * {@link #awaitOwnWrite} says so. It stays the same object across steps,
 * so a test can change it while a shell runs it.
 */
public final class RecordingView implements NetworkView {

    private final List<ViewInput> received = new CopyOnWriteArrayList<>();
    private final NetworkCopy copy;
    private volatile boolean mayAnswer;
    private volatile boolean awaitsOwnWrite;
    private volatile Class<? extends ViewInput> failingInput;

    public RecordingView(NetworkCopy copy) {
        this.copy = copy;
    }

    static RecordingView withoutCopy() {
        return new RecordingView(null);
    }

    public void answer(boolean mayAnswer) {
        this.mayAnswer = mayAnswer;
    }

    void awaitOwnWrite(boolean awaitsOwnWrite) {
        this.awaitsOwnWrite = awaitsOwnWrite;
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
    public boolean isLive(long nowMillis) {
        return mayAnswer;
    }

    @Override
    public boolean mayAnswer(ReadKind kind, long nowMillis) {
        return mayAnswer;
    }

    @Override
    public boolean awaitsOwnWrite(ReadKind kind, long nowMillis) {
        return awaitsOwnWrite;
    }

    @Override
    public Optional<NetworkCopy> copy() {
        return Optional.ofNullable(copy);
    }
}
