package app.simplecloud.api.testing.internal.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.util.ArrayList;
import java.util.List;

public final class Inbox {

    private final List<Component> messages = new ArrayList<>();
    private final List<Component> actionBars = new ArrayList<>();
    private final List<Title> titles = new ArrayList<>();

    public synchronized void message(Component message) {
        messages.add(message);
    }

    public synchronized void actionBar(Component message) {
        actionBars.add(message);
    }

    public synchronized void title(Title title) {
        titles.add(title);
    }

    public synchronized List<Component> messages() {
        return List.copyOf(messages);
    }

    public synchronized List<Component> actionBars() {
        return List.copyOf(actionBars);
    }

    public synchronized List<Title> titles() {
        return List.copyOf(titles);
    }

    public synchronized void clear() {
        messages.clear();
        actionBars.clear();
        titles.clear();
    }
}
