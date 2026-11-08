package app.simplecloud.api.plugin;

import org.jetbrains.annotations.Nullable;

/**
 * A plugin to install on a group or persistent server when it is created.
 *
 * <p>A reference either points to an existing plugin by ID ({@link #ofId(String)}) or describes it by name and
 * source ({@link #ofSource(String, PluginSourceType, String)}). The controller looks up a plugin with that name and
 * source and creates it if it does not exist yet.
 *
 * @param pluginId the ID of an existing plugin, or null for a source reference
 * @param pluginName the plugin name (at most 100 characters), or null for an ID reference
 * @param sourceType where the plugin comes from, or null for an ID reference
 * @param sourceValue the URL or project ID at the source, or null for an ID reference
 * @param version the version to install, or null for the latest
 * @param required whether the plugin is marked as required for the server
 */
public record PluginReference(
        @Nullable String pluginId,
        @Nullable String pluginName,
        @Nullable PluginSourceType sourceType,
        @Nullable String sourceValue,
        @Nullable String version,
        boolean required) {

    public PluginReference {
        boolean byId = pluginId != null && !pluginId.isBlank();
        boolean bySource = pluginName != null
                && !pluginName.isBlank()
                && sourceType != null
                && sourceValue != null
                && !sourceValue.isBlank();
        if (byId == bySource) {
            throw new IllegalArgumentException(
                    "Either pluginId or pluginName, sourceType and sourceValue must be set, not both");
        }
    }

    /**
     * References an existing plugin by its ID.
     *
     * @param pluginId the plugin ID
     * @return the reference
     */
    public static PluginReference ofId(String pluginId) {
        return new PluginReference(pluginId, null, null, null, null, false);
    }

    /**
     * References a plugin by name and source. The controller creates the plugin if it does not exist yet.
     *
     * @param pluginName the plugin name
     * @param sourceType where the plugin comes from
     * @param sourceValue the URL or project ID at the source
     * @return the reference
     */
    public static PluginReference ofSource(String pluginName, PluginSourceType sourceType, String sourceValue) {
        return new PluginReference(null, pluginName, sourceType, sourceValue, null, false);
    }

    /**
     * Returns a copy that installs the given version.
     *
     * @param version the version, or null for the latest
     * @return the new reference
     */
    public PluginReference withVersion(@Nullable String version) {
        return new PluginReference(pluginId, pluginName, sourceType, sourceValue, version, required);
    }

    /**
     * Returns a copy with the given required flag.
     *
     * @param required whether the plugin is marked as required for the server
     * @return the new reference
     */
    public PluginReference withRequired(boolean required) {
        return new PluginReference(pluginId, pluginName, sourceType, sourceValue, version, required);
    }
}
