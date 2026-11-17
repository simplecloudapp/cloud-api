package app.simplecloud.api.internal.networkstate;

import java.util.List;

/**
 * The result of handing one input to a view.
 *
 * @param view    the view after the input; the shell replaces its current view with it
 * @param effects what the shell must do or report, in any order
 */
public record ViewStep(NetworkView view, List<ViewEffect> effects) {

    public ViewStep {
        effects = List.copyOf(effects);
    }
}
