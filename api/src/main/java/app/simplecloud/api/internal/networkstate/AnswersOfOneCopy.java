package app.simplecloud.api.internal.networkstate;

import app.simplecloud.api.internal.networkstate.read.CopyList;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The answers one list rendered from the copy it was last asked about. A copy never changes, so a read repeated
 * on the same copy needs no new rendering; the view makes a new copy for each batch and pulse, and asking about
 * another copy drops them all. An answer that throws is not kept, so the next read renders it again.
 */
final class AnswersOfOneCopy<P, R> {

    /**
     * Small, because only reads repeated while one copy is current gain from it. Lookups by many different ids
     * would otherwise hold an answer for each.
     */
    private static final int MAX_KEPT_PARAMETERS = 64;

    private record KeptAnswers<P, R>(NetworkCopy copy, Map<P, Optional<R>> byParameters) {
    }

    private final CopyList<P, R> copyList;
    private final AtomicReference<KeptAnswers<P, R>> current = new AtomicReference<>();

    AnswersOfOneCopy(CopyList<P, R> copyList) {
        this.copyList = copyList;
    }

    Optional<R> answer(NetworkCopy copy, P parameters) {
        Map<P, Optional<R>> kept = answersOf(copy);
        Optional<R> keptAnswer = kept.get(parameters);
        if (keptAnswer != null) {
            return keptAnswer;
        }
        Optional<R> answer = copyList.answer(copy, parameters);
        keep(kept, parameters, answer);
        return answer;
    }

    /**
     * Compares copies by identity: equals would compare every item of both. A reader that still holds an older
     * copy may replace the newer copy's answers; that costs a rendering, never a wrong answer, since an answer is
     * only reused for the copy it was rendered from.
     */
    private Map<P, Optional<R>> answersOf(NetworkCopy copy) {
        KeptAnswers<P, R> kept = current.get();
        if (kept != null && kept.copy() == copy) {
            return kept.byParameters();
        }
        KeptAnswers<P, R> fresh = new KeptAnswers<>(copy, new ConcurrentHashMap<>());
        current.set(fresh);
        return fresh.byParameters();
    }

    private static <P, R> void keep(Map<P, Optional<R>> kept, P parameters, Optional<R> answer) {
        if (kept.size() >= MAX_KEPT_PARAMETERS) {
            kept.clear();
        }
        kept.put(parameters, answer);
    }
}
