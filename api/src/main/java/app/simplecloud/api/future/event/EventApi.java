package app.simplecloud.api.future.event;

import java.util.function.Consumer;

public interface EventApi {

    <T extends CloudEvent> void subscribe(Class<T> cloudEventClass, Consumer<T> on);

    void publish(CloudEvent event);

}
