package app.simplecloud.api.internal.networkstate;

import app.simplecloud.api.internal.networkstate.read.CopyList;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopyFirstListTest {

    private static final Executor CALLER_THREAD = Runnable::run;
    private static final String PARAMETERS = "all servers";
    private static final String OVER_HTTP = "over HTTP";

    private final RenderingList renderingList = new RenderingList();
    private final AtomicReference<NetworkCopy> currentCopy = new AtomicReference<>(copyAt(1));
    private final AnsweringCopy answeringCopy = this::currentCopyAnswers;
    private final CopyFirstList<String, String> list = new CopyFirstList<>(renderingList, answeringCopy,
            parameters -> OVER_HTTP, CALLER_THREAD);

    @Test
    void aReadRepeatedOnTheSameCopyIsRenderedOnce() {
        String first = read();
        String second = read();

        assertEquals("revision 1", first);
        assertEquals("revision 1", second);
        assertEquals(1, renderingList.renders);
    }

    @Test
    void aReadOnANewCopyIsRenderedAgain() {
        String onFirstCopy = read();
        currentCopy.set(copyAt(2));
        String onNewCopy = read();

        assertEquals("revision 1", onFirstCopy);
        assertEquals("revision 2", onNewCopy);
        assertEquals(2, renderingList.renders);
    }

    @Test
    void anAnswerThatThrewIsNotKeptSoTheNextReadRendersAgain() {
        renderingList.throwOnNextRender = true;

        String afterThrow = read();
        String nextRead = read();

        assertEquals(OVER_HTTP, afterThrow, "a copy that cannot answer leaves the read to HTTP");
        assertEquals("revision 1", nextRead);
        assertEquals(2, renderingList.renders);
    }

    private String read() {
        CompletableFuture<String> result = list.read(PARAMETERS, Function.identity(),
                fromController -> fromController.get());
        return result.join();
    }

    private CompletableFuture<Optional<NetworkCopy>> currentCopyAnswers() {
        Optional<NetworkCopy> copy = Optional.of(currentCopy.get());
        return CompletableFuture.completedFuture(copy);
    }

    private static NetworkCopy copyAt(long revision) {
        return new NetworkCopy(1, revision, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static final class RenderingList implements CopyList<String, String> {

        private int renders;
        private boolean throwOnNextRender;

        @Override
        public boolean refuses(String parameters) {
            return false;
        }

        @Override
        public Optional<String> answer(NetworkCopy copy, String parameters) {
            renders++;
            if (throwOnNextRender) {
                throwOnNextRender = false;
                throw new IllegalStateException("a body the HTTP client cannot read");
            }
            return Optional.of("revision " + copy.revision());
        }
    }
}
