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
 * that made this view. A deadline acts in the first step at or after it. In a step, the input acts first
 * and the deadlines after it, so an input that arrives exactly at a deadline is on time: a missing batch
 * that arrives exactly 3 s after a batch was held closes the gap, and a copy that reaches the audit's
 * revision exactly 3 s after the audit snapshot arrived is compared. When the audit's deadline and a 3 s
 * deadline of rule 2 or 3 fall in one step, the audit's acts first. Revisions, and epochs where an own
 * write orders them, are uint64 values held in a {@code long}, so they compare unsigned; everywhere else
 * epochs compare only for equality. A random delay is cut to whole milliseconds, and each one draws the
 * next number from the sequence. "Local" is the revision of the copy. A view is "synced" while it holds a
 * copy, its connection is up, and it neither wants nor runs a reload.
 *
 * <h2>Rules</h2>
 *
 * <ol>
 *   <li><b>Start.</b> A view starts with no copy and no connection, and waits for
 *   {@link ViewInput.SubscriptionsActive}. Then it reloads (rule 6). A second
 *   {@link ViewInput.SubscriptionsActive} without a {@link ViewInput.ConnectionLost} in between is outside
 *   this contract. Batches that arrive while that load
 *   runs wait; once the snapshot is taken, they go through rule 2, with local = the snapshot's revision.
 *   Batches that arrived before the load started are in the snapshot already, so the view need not keep
 *   them.</li>
 *
 *   <li><b>Batch.</b> In a synced view, a batch from another epoch than the copy's means reload, also from a
 *   lower one: after a restore without a raised generation, a lower epoch can be the newer one. In the same
 *   epoch:
 *   <ul>
 *     <li>{@code revision <= local}: drop it, a duplicate or a former owner's;</li>
 *     <li>{@code base_revision <= local < revision}: apply it and set local = {@code revision}. Each item in
 *     the batch replaces the copy's item with the same id, or adds it; each deleted id removes its item,
 *     and an unknown id is no error. Then the held batches, oldest first, go through this rule again;</li>
 *     <li>{@code base_revision > local}: hold it. A batch held for 3 s means reload. The 3 s count from
 *     the batch's own arrival, also when it arrived during a load.</li>
 *   </ul>
 *   Every reference in the copy a batch would make must resolve: a server's group and its persistent
 *   server, each if the server names one, and a group's blueprint, if the group names one. A batch with a
 *   missing item is not applied: the copy stays as it was, and the view reloads. The check runs on the whole
 *   batch, because a new group and its first server can come in one batch.</li>
 *
 *   <li><b>Pulse.</b> Every pulse, in every state, sets the "views may answer" flag and replaces the
 *   copy's metrics. In a synced view, the first of these that holds decides:
 *   <ul>
 *     <li>the last good pulse is 15 s old or older, or there was none since the last snapshot was taken and
 *     that is 15 s old or older: reload, whatever the pulse says;</li>
 *     <li>another epoch than the copy's, lower or higher: reload;</li>
 *     <li>{@code revision > local}: the copy is behind, and the view is not live until local reaches the
 *     highest revision such a pulse showed. If the copy is still behind 3 s after the first of these
 *     pulses, the view reloads;</li>
 *     <li>{@code revision == local}: a good pulse;</li>
 *     <li>{@code revision < local}: nothing more. It is a former owner's, or the snapshot was newer than
 *     the relay's last round.</li>
 *   </ul></li>
 *
 *   <li><b>Connection.</b> After {@link ViewInput.ConnectionLost} the view is not live at once. It drops
 *   held batches, a wanted reload, and a running audit, also one that waits for the copy to reach its
 *   revision. It starts no load and ignores batches until the next {@link ViewInput.SubscriptionsActive},
 *   which means reload.</li>
 *
 *   <li><b>Live.</b> A view is live only while it is synced, no batch is held, the copy is not behind a
 *   pulse, and a good pulse arrived after the last snapshot was taken and is under 15 s old.</li>
 *
 *   <li><b>Reload.</b> A reload drops held batches and the pulse the copy was behind, and abandons a running
 *   audit. Until the load starts, batches need not be kept, as in rule 1. The load starts at the latest of:
 *   the time the reload was wanted; 2 s after the previous load started; and, after n failed loads in a
 *   row, min(30 s, 2 s &times; 2^(n-1)) after the last failure. Then it waits 2 s &times; a number from the
 *   sequence more, so that an epoch change does not reload every process in the same second. A view runs one
 *   load at a time, and every load starts at least 2 s after the previous one. A failed audit load does not
 *   count as a failed load. A load that is no longer wanted when it ends, because the connection was lost or
 *   a new reload was wanted while it ran, is waited for and its answer ignored; a failure of such a load is
 *   no failed load.
 *   <p>Taking a snapshot: the copy becomes the snapshot, items and metrics; the good pulse and the pulse the
 *   copy was behind are cleared, so the view is not live until the next good pulse; held batches go through
 *   rule 2; and the next audit is due 20 min + 10 min &times; a number from the sequence later. A taken
 *   snapshot ends a row of failed loads. A snapshot in which a reference does not resolve, as in rule 2, is
 *   not taken: the view keeps its last copy, counts a failed load, and reloads.</li>
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
 * {@link #routeRead} says where a read of a kind goes: it is answered from the copy, it waits for the copy,
 * or it goes over HTTP. At time T, for a read that started at S, the first of these that holds decides:
 * <ul>
 *   <li>the view is not live (rule 5; time alone ends it, when the last good pulse turns 15 s old), or the
 *   last pulse did not allow answers ({@code views_may_answer}; false before the first pulse): over HTTP;</li>
 *   <li>the copy has reached every own write that can change reads of that kind: from the copy;</li>
 *   <li>T is under 3 s after S: wait;</li>
 *   <li>otherwise: over HTTP.</li>
 * </ul>
 * So a read that the copy may answer is answered from it, also exactly 3 s after it started. A read waits
 * instead of going over HTTP because the controller's list cache can answer it from before the write for up to
 * 2 s, while the copy gets the write with its batch.
 * <p>A {@link ViewInput.WriteReturned} at epoch E and revision R is reached while the copy's epoch is above E,
 * or equal to E with local at or above R. It is also reached for good once the view takes a snapshot whose
 * load started in the step that handed over the write or in a later step. That is a reload's snapshot (rule
 * 6), or an audit's snapshot that the view takes because it differed or was not reached (rule 7); for the
 * audit, its own load counts. The controller reads that snapshot after the write's request arrived, so it
 * holds the write whatever its epoch, also when a restore made the epoch lower (rule 2). In a step, the input
 * acts before a load starts, so a load that starts in the step that hands over the write counts as after it.
 * A load that already runs when the write is handed over does not reach it, also when it started at the same
 * millisecond in an earlier step. A snapshot that is not taken reaches nothing: one in which a reference does
 * not resolve, the answer of an abandoned load, and an audit snapshot that matched or that the copy went past.
 * Each write is reached on its own, so a write after the snapshot makes reads wait again until it is reached.
 * A write of a kind can change these reads:
 * <ul>
 *   <li>a server: servers, server groups (their stats) and persistent servers (their player count);</li>
 *   <li>a server group: server groups and servers (a server contains its group);</li>
 *   <li>a persistent server: persistent servers and servers;</li>
 *   <li>a blueprint: server groups and servers (a group contains its blueprint).</li>
 * </ul>
 * <p>A {@link ViewInput.WriteRevisionUnknown} means reload in a synced view, and in a view whose
 * reload's load runs, because that copy or that load may be from before the write. The controller reads a
 * snapshot after the request arrived, so a load that starts after a successful response holds the write, and
 * the view answers nothing until it is live again (rule 5). After a 5xx or a call that threw, the controller
 * may still be handling the write, so a load can miss it; the copy then gets the write from its batch. In a
 * view that wants a reload or is disconnected, the input does nothing: its next load starts after the write
 * anyway.
 */
public interface NetworkView {

    /**
     * Hands one input to the view and returns the next view with the effects to carry out.
     */
    ViewStep step(ViewInput input, long nowMillis);

    /**
     * Returns where a read of {@code kind} that started at {@code readStartedAtMillis} goes at
     * {@code nowMillis} (Answers). The start is at or before {@code nowMillis}.
     */
    ReadRoute routeRead(ReadKind kind, long readStartedAtMillis, long nowMillis);

    /**
     * Returns the copy, or empty before the first snapshot is taken. A view keeps its copy while it is not
     * live, so the copy alone does not mean it may be read.
     */
    Optional<NetworkCopy> copy();
}
