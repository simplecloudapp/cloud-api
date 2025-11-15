package app.simplecloud.api.blueprint;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a server blueprint configuration.
 * 
 * <p>Blueprints are reusable templates that define the base software configuration
 * for server instances, including the Minecraft version, server software type,
 * runtime environment, and workflow steps to execute during server lifecycle.
 */
public interface Blueprint {
    
    /**
     * Returns the unique identifier of this blueprint.
     * 
     * @return the blueprint ID
     */
    String getBlueprintId();
    
    /**
     * Returns the human-readable name of this blueprint.
     * 
     * @return the blueprint name
     */
    String getName();
    
    /**
     * Returns the configurator used to set up this blueprint.
     * 
     * @return the configurator name, or null if not set
     */
    @Nullable String getConfigurator();
    
    /**
     * Returns the Minecraft version this blueprint is configured for.
     * 
     * @return the Minecraft version, or null if not applicable
     */
    @Nullable String getMinecraftVersion();
    
    /**
     * Returns the server software type (e.g., "paper", "spigot", "velocity").
     * 
     * @return the server software, or null if not set
     */
    @Nullable String getServerSoftware();
    
    /**
     * Returns the download URL for the server software.
     * 
     * @return the server URL, or null if not set
     */
    @Nullable String getServerUrl();
    
    /**
     * Returns the specific version of the server software.
     * 
     * @return the software version, or null if not set
     */
    @Nullable String getSoftwareVersion();
    
    /**
     * Returns the runtime configuration for servers using this blueprint.
     * 
     * <p>Defines the runtime type (e.g., Java) and associated configuration.
     * 
     * @return the runtime config, or null if not set
     */
    @Nullable RuntimeConfig getRuntimeConfig();
    
    /**
     * Returns the workflow steps to execute during server lifecycle events.
     * 
     * @return the list of workflow step IDs, or null if none defined
     */
    @Nullable List<String> getWorkflowSteps();
    
    /**
     * Returns the timestamp when this blueprint was created (ISO 8601 format).
     * 
     * @return the creation timestamp
     */
    String getCreatedAt();
    
    /**
     * Returns the timestamp when this blueprint was last updated (ISO 8601 format).
     * 
     * @return the update timestamp
     */
    String getUpdatedAt();
}
