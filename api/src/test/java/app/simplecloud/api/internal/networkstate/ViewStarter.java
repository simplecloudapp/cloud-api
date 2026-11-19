package app.simplecloud.api.internal.networkstate;

/**
 * Starts a fresh view of the machine under check, so that the same checks run against every machine.
 */
@FunctionalInterface
interface ViewStarter {

    NetworkView start(RandomSequence random);
}
