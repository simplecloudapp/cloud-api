package app.simplecloud.api.internal.networkstate;

/**
 * A check's set-up did not bring the view into the state the check starts from. It is not an
 * {@link AssertionError}, so {@link WrongMachinesTest} does not count a wrong machine as caught by a check that
 * never reached its point.
 */
final class SetUpFailed extends RuntimeException {

    SetUpFailed(String message) {
        super(message);
    }
}
