package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

/**
 * A host listed in a {@link DeploymentConfig}, referenced by its name.
 *
 * @param name the name of the serverhost
 * @param priority the priority of this host
 */
public record DeploymentHost(@Nullable String name, int priority) {}
