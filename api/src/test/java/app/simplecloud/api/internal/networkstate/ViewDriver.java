package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plays the shell for one view in the example checks: it keeps the current view and the time, hands inputs
 * over and remembers the effects of the last step.
 *
 * <p>Its set-up steps ({@link #liveAt}, {@link #waitForLoad}, {@link #waitForLoadKeepingLive},
 * {@link #requireLoadRequested}) throw {@link SetUpFailed} when the view does not get where they bring it.
 * Its other methods that fail throw an {@link AssertionError}, because they check a rule.
 */
final class ViewDriver {

    private NetworkView view;
    private long now;
    private List<ViewEffect> lastEffects = List.of();
    private int auditsFinished;

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
        driver.requireLoadRequested("a view whose subscriptions are active");
        driver.advance(100);
        driver.loadSnapshot(snapshot);
        driver.advance(100);
        driver.pulse(StateItems.pulse(snapshot.getEpoch(), snapshot.getRevision()));
        if (!driver.readsStayOffHttp()) {
            throw new SetUpFailed("the view is not live after its snapshot and a good pulse");
        }
        return driver;
    }

    long now() {
        return now;
    }

    void send(ViewInput input) {
        ViewStep step = view.step(input, now);
        view = step.view();
        lastEffects = step.effects();
        for (ViewEffect effect : lastEffects) {
            if (effect instanceof ViewEffect.AuditFinished) {
                auditsFinished++;
            }
        }
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
     * Lets time pass in steps of 1 ms until the view asks for a load, and returns the time of that step, or
     * empty if it asks for none within {@code maxMillis}.
     */
    OptionalLong loadWithin(long maxMillis) {
        long deadline = now + maxMillis;
        while (now < deadline) {
            advance(1);
            if (requestedLoad()) {
                return OptionalLong.of(now);
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Like {@link #loadWithin}, as a set-up step: returns the time of the load.
     */
    long waitForLoad(long maxMillis) {
        OptionalLong loadAt = loadWithin(maxMillis);
        if (loadAt.isEmpty()) {
            throw new SetUpFailed("the view asked for no load within " + maxMillis + " ms");
        }
        return loadAt.getAsLong();
    }

    /**
     * Like {@link #loadWithin}, in steps of 100 ms, and sends a pulse at the copy's revision at the relay's
     * pulse interval so that the view stays live meanwhile.
     */
    OptionalLong loadWithinKeepingLive(long maxMillis) {
        long deadline = now + maxMillis;
        long nextPulseAt = now + ModelOwner.PULSE_INTERVAL_MILLIS;
        while (now < deadline) {
            advance(100);
            if (requestedLoad()) {
                return OptionalLong.of(now);
            }
            if (now >= nextPulseAt) {
                sendPulseAtCopy();
                nextPulseAt += ModelOwner.PULSE_INTERVAL_MILLIS;
            }
            if (requestedLoad()) {
                return OptionalLong.of(now);
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Like {@link #loadWithinKeepingLive}, as a set-up step: returns the time of the load.
     */
    long waitForLoadKeepingLive(long maxMillis) {
        OptionalLong loadAt = loadWithinKeepingLive(maxMillis);
        if (loadAt.isEmpty()) {
            throw new SetUpFailed("the view asked for no load within " + maxMillis + " ms while kept live");
        }
        return loadAt.getAsLong();
    }

    /**
     * Lets time pass in steps of 100 ms until {@code time}, with a pulse at the copy's revision at the relay's
     * pulse interval, and fails if the view asks for a load meanwhile.
     */
    void keepLiveWithoutLoadUntil(long time) {
        long nextPulseAt = now + ModelOwner.PULSE_INTERVAL_MILLIS;
        while (now < time) {
            advance(100);
            failIfLoadRequested();
            if (now >= nextPulseAt) {
                sendPulseAtCopy();
                nextPulseAt += ModelOwner.PULSE_INTERVAL_MILLIS;
                failIfLoadRequested();
            }
        }
    }

    private void failIfLoadRequested() {
        if (requestedLoad()) {
            fail("the view asked for a load at " + now + " ms");
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

    /**
     * A set-up step: fails with {@link SetUpFailed} unless the last step asked for a load.
     *
     * @param state the state that must ask for a load, for the message
     */
    void requireLoadRequested(String state) {
        if (!requestedLoad()) {
            throw new SetUpFailed(state + " asked for no load");
        }
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
     * Counts every audit that finished in a step of this driver, so a check can look at a window of steps.
     */
    int auditsFinished() {
        return auditsFinished;
    }

    /**
     * Returns whether a read that starts now stays off HTTP: it is answered from the copy or waits. That is
     * exactly while the view is live (rule 5) and the last pulse allowed answers. Every check's pulses allow
     * answers unless the check says otherwise; before the first pulse every read goes over HTTP, so a check
     * that looks at live there sends a pulse first.
     */
    boolean readsStayOffHttp() {
        return readsStayOffHttpAt(now);
    }

    /**
     * Asks whether a read stays off HTTP at a later time, without a step in between.
     */
    boolean readsStayOffHttpAt(long time) {
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
