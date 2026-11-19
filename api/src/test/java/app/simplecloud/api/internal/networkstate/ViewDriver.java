package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plays the shell for one view in the example checks: it keeps the current view and the time, hands inputs
 * over and remembers the effects of the last step.
 */
final class ViewDriver {

    private static final long PULSE_INTERVAL_MILLIS = 5_000;

    private NetworkView view;
    private long now;
    private List<ViewEffect> lastEffects = List.of();

    private ViewDriver(NetworkView view) {
        this.view = view;
    }

    /**
     * Starts a view whose random numbers are all {@code randomNumber}, so delays are exact.
     */
    static ViewDriver start(ViewStarter starter, double randomNumber) {
        return new ViewDriver(starter.start(index -> randomNumber));
    }

    static ViewDriver startWithSequence(ViewStarter starter, RandomSequence random) {
        return new ViewDriver(starter.start(random));
    }

    static ViewDriver start(ViewStarter starter) {
        return start(starter, 0.0);
    }

    /**
     * Starts a view and makes it live on {@code snapshot}: the load starts at 0 ms, the snapshot arrives at
     * 100 ms and a good pulse at 200 ms.
     */
    static ViewDriver liveAt(ViewStarter starter, NetworkState snapshot) {
        ViewDriver driver = start(starter);
        driver.subscribe();
        driver.assertLoadRequested("a view loads a snapshot once its subscriptions are active");
        driver.advance(100);
        driver.loadSnapshot(snapshot);
        driver.advance(100);
        driver.pulse(StateItems.pulse(snapshot.getEpoch(), snapshot.getRevision()));
        assertTrue(driver.isLive(), "a view must be live after its snapshot and a good pulse");
        return driver;
    }

    long now() {
        return now;
    }

    void send(ViewInput input) {
        ViewStep step = view.step(input, now);
        view = step.view();
        lastEffects = step.effects();
    }

    void subscribe() {
        send(new ViewInput.SubscriptionsActive());
    }

    void loseConnection() {
        send(new ViewInput.ConnectionLost());
    }

    void loadSnapshot(NetworkState snapshot) {
        send(new ViewInput.SnapshotLoaded(snapshot));
    }

    void failLoad() {
        send(new ViewInput.SnapshotFailed());
    }

    void batch(StateBatch batch) {
        send(new ViewInput.BatchReceived(batch));
    }

    void pulse(StatePulse pulse) {
        send(new ViewInput.PulseReceived(pulse));
    }

    void wrote(long epoch, long revision) {
        send(new ViewInput.WriteReturned(epoch, revision));
    }

    void advance(long millis) {
        now += millis;
        send(new ViewInput.TimePassed());
    }

    /**
     * Moves the time without a step, so the next input is the first step at {@code time}.
     */
    void jumpTo(long time) {
        now = time;
    }

    /**
     * Lets time pass in steps of 1 ms until {@code time}, and fails if the view asks for a load before it.
     */
    void advanceWithoutLoadTo(long time, String message) {
        while (now < time) {
            advance(1);
            if (requestedLoad()) {
                fail(message + " (asked for a load at " + now + " ms)");
            }
        }
    }

    /**
     * Lets time pass in steps of 1 ms until the view asks for a load, and returns the time of that step.
     */
    long waitForLoad(long maxMillis) {
        long deadline = now + maxMillis;
        while (now < deadline) {
            advance(1);
            if (requestedLoad()) {
                return now;
            }
        }
        return fail("the view asked for no load within " + maxMillis + " ms");
    }

    /**
     * Like {@link #waitForLoad}, in steps of 100 ms, and sends a pulse at the copy's revision every 5 s so
     * that the view stays live meanwhile.
     */
    long waitForLoadKeepingLive(long maxMillis) {
        long deadline = now + maxMillis;
        long nextPulseAt = now + PULSE_INTERVAL_MILLIS;
        while (now < deadline) {
            advance(100);
            if (requestedLoad()) {
                return now;
            }
            if (now >= nextPulseAt) {
                sendPulseAtCopy();
                nextPulseAt += PULSE_INTERVAL_MILLIS;
            }
            if (requestedLoad()) {
                return now;
            }
        }
        return fail("the view asked for no load within " + maxMillis + " ms");
    }

    /**
     * Lets time pass in steps of 100 ms until {@code time}, with a pulse at the copy's revision every 5 s,
     * and fails if the view asks for a load meanwhile.
     */
    void keepLiveWithoutLoadUntil(long time) {
        long nextPulseAt = now + PULSE_INTERVAL_MILLIS;
        while (now < time) {
            advance(100);
            if (now >= nextPulseAt) {
                sendPulseAtCopy();
                nextPulseAt += PULSE_INTERVAL_MILLIS;
            }
            if (requestedLoad()) {
                fail("the view asked for a load at " + now + " ms");
            }
        }
    }

    void sendPulseAtCopy() {
        NetworkCopy current = copy();
        pulse(StateItems.pulse(current.epoch(), current.revision()));
    }

    boolean requestedLoad() {
        for (ViewEffect effect : lastEffects) {
            if (effect instanceof ViewEffect.LoadSnapshot) {
                return true;
            }
        }
        return false;
    }

    void assertLoadRequested(String message) {
        assertTrue(requestedLoad(), message);
    }

    Optional<AuditOutcome> lastAuditOutcome() {
        for (ViewEffect effect : lastEffects) {
            if (effect instanceof ViewEffect.AuditFinished finished) {
                return Optional.of(finished.outcome());
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether the view is live (rule 5), as a read now shows it: it is answered or waits
     * exactly while the view is live and the last pulse allowed answers. Every check's pulses allow answers
     * unless the check says otherwise; before the first pulse no read is answered, so a check that looks at
     * live there sends a pulse first.
     */
    boolean isLive() {
        return isLiveAt(now);
    }

    /**
     * Asks whether the current view is live at a later time, without a step in between.
     */
    boolean isLiveAt(long time) {
        return routeAt(time) != ReadRoute.OVER_HTTP;
    }

    boolean mayAnswer() {
        return route() == ReadRoute.FROM_COPY;
    }

    boolean awaitsOwnWrite() {
        return route() == ReadRoute.WAIT;
    }

    ReadRoute route() {
        return routeAt(now);
    }

    /**
     * Asks where a read goes at a later time, without a step in between.
     */
    ReadRoute routeAt(long time) {
        return view.routeRead(time);
    }

    NetworkCopy copy() {
        return view.copy().orElseThrow(() -> new AssertionError("the view holds no copy"));
    }

    Optional<NetworkCopy> copyIfAny() {
        return view.copy();
    }
}
