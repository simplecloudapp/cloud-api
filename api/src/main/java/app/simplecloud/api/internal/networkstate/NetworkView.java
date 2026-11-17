package app.simplecloud.api.internal.networkstate;

import java.util.Optional;

/**
 * One network's view at one moment: its copy of the network, and everything the rules below need to
 * decide.
 *
 * <p>A view never changes. {@link #step} returns the next view and the effects the shell must carry out, so
 * a read that holds one view sees one consistent copy. A view does no I/O and reads no clock and no random
 * source of its own: the shell passes the time into every call, and a {@link RandomSequence} at the start.
 * It is an interface so that the same checks run against {@link NetworkViewMachine} and against the
 * reference machine in the tests.
 *
 * <p>Times are milliseconds of a monotonic clock. Each call passes a time at or after the time of the step
 * that made this view. A deadline acts in the first step at or after it. Epochs and revisions are uint64
 * values held in a {@code long}, so they compare unsigned. "Local" is the revision of the copy. A view is
 * "synced" while it holds a copy, its connection is up, and it neither wants nor runs a reload.
 *
 * <h2>Rules</h2>
 *
 * <ol>
 *   <li><b>Start.</b> A view starts with no copy and no connection, and waits for
 *   {@link ViewInput.SubscriptionsActive}. Then it reloads (rule 6). Batches that arrive while that load
 *   runs wait; once the snapshot is taken, they go through rule 2, with local = the snapshot's revision.
 *   Batches that arrived before the load started are in the snapshot already.</li>
 *
 *   <li><b>Batch.</b> In a synced view, a batch from another epoch than the copy's means reload. In the same
 *   epoch:
 *   <ul>
 *     <li>{@code revision <= local}: drop it, a duplicate or a former owner's;</li>
 *     <li>{@code base_revision <= local < revision}: apply it and set local = {@code revision}. Each item in
 *     the batch replaces the copy's item with the same id, or adds it; each deleted id removes its item,
 *     and an unknown id is no error. Then the held batches, oldest first, go through this rule again;</li>
 *     <li>{@code base_revision > local}: hold it. A batch held for 3 s means reload.</li>
 *   </ul>
 *   After each applied batch, every reference in the copy must resolve: a server's group and its persistent
 *   server, each if the server names one, and a group's blueprint, if the group names one. A missing item
 *   means reload. The check runs after the whole batch, because a new group and its first server can come
 *   in one batch.</li>
 *
 *   <li><b>Pulse.</b> Every pulse, in every state, sets the "views may answer" flag and replaces the
 *   copy's metrics. In a synced view, the first of these that holds decides:
 *   <ul>
 *     <li>the last good pulse is 15 s old or older, or there was none since the last snapshot was taken and
 *     that is 15 s old or older: reload, whatever the pulse says;</li>
 *     <li>another epoch than the copy's: reload;</li>
 *     <li>{@code revision > local}: the copy is behind, and the view is not live until local reaches that
 *     revision. If it has not within 3 s, the view reloads;</li>
 *     <li>{@code revision == local}: a good pulse;</li>
 *     <li>{@code revision < local}: nothing more. It is a former owner's, or the snapshot was newer than
 *     the relay's last round.</li>
 *   </ul></li>
 *
 *   <li><b>Connection.</b> After {@link ViewInput.ConnectionLost} the view is not live at once. It drops
 *   held batches, a wanted reload and a running audit, and ignores batches until the next
 *   {@link ViewInput.SubscriptionsActive}, which means reload.</li>
 *
 *   <li><b>Live.</b> A view is live only while it is synced, no batch is held, the copy is not behind a
 *   pulse, and a good pulse arrived after the last snapshot was taken and is under 15 s old.</li>
 *
 *   <li><b>Reload.</b> A reload drops held batches and the pulse the copy was behind, and abandons a running
 *   audit. Batches are dropped until the load starts. The load starts at the latest of: the time the reload
 *   was wanted; 2 s after the previous load started; and, after n failed loads in a row,
 *   min(30 s, 2 s &times; 2^(n-1)) after the last failure. Then it waits 2 s &times; a number from the
 *   sequence more, so that an epoch change does not reload every process in the same second. A view runs one
 *   load at a time, and every load starts at least 2 s after the previous one. A load that is no longer
 *   wanted when it ends, because the connection was lost or a new reload was wanted while it ran, is waited
 *   for and its answer ignored.
 *   <p>Taking a snapshot: the copy becomes the snapshot, items and metrics; the good pulse is cleared, so the
 *   view is not live until the next good pulse; held batches go through rule 2; and the next audit is due
 *   20 min + 10 min &times; a number from the sequence later. A taken snapshot ends a row of failed loads,
 *   unless a reference in it does not resolve: that counts as a failed load, and the view reloads.</li>
 *
 *   <li><b>Audit.</b> When an audit is due, the view is live and no load runs, the view loads a snapshot at
 *   some revision S and stays live. When the copy is at exactly S, it compares the items, without metrics.
 *   The outcomes ({@link AuditOutcome}):
 *   <ul>
 *     <li>equal: the next audit is due 20 to 30 min later, drawn as above;</li>
 *     <li>different: the view takes the snapshot;</li>
 *     <li>the copy went past S, or was past it when the snapshot arrived: the next audit is due 2 s + 8 s
 *     &times; a number from the sequence later;</li>
 *     <li>the copy has not reached S 3 s after the snapshot arrived: the view takes the snapshot.</li>
 *   </ul>
 *   An audit snapshot from another epoch means reload. A failed audit load makes the next audit due 20 to 30
 *   min later.</li>
 * </ol>
 *
 * <h2>Answers</h2>
 *
 * {@link #mayAnswer} is true only while the view is live, the last pulse allowed answers
 * ({@code views_may_answer}; false before the first pulse), and the copy has reached every own write that
 * can change reads of that kind. A {@link ViewInput.WriteReturned} at epoch E and revision R is reached when
 * the copy's epoch is above E, or equal to E with local at or above R. A write of a kind can change these
 * reads:
 * <ul>
 *   <li>a server: servers, server groups (their stats) and persistent servers (their player count);</li>
 *   <li>a server group: server groups and servers (a server contains its group);</li>
 *   <li>a persistent server: persistent servers and servers;</li>
 *   <li>a blueprint: blueprints, server groups and servers.</li>
 * </ul>
 */
public interface NetworkView {

    /**
     * Hands one input to the view and returns the next view with the effects to carry out.
     */
    ViewStep step(ViewInput input, long nowMillis);

    /**
     * Returns whether the view is live at {@code nowMillis} (rule 5). Time alone can end it: a view whose last
     * good pulse turns 15 s old stops being live without a step.
     */
    boolean isLive(long nowMillis);

    /**
     * Returns whether reads of {@code kind} may be answered from {@link #copy()} at {@code nowMillis}, instead
     * of over HTTP.
     */
    boolean mayAnswer(ItemKind kind, long nowMillis);

    /**
     * Returns the copy, or empty before the first snapshot is taken. A view keeps its copy while it is not
     * live, so the copy alone does not mean it may be read.
     */
    Optional<NetworkCopy> copy();
}
