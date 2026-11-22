package app.simplecloud.api.player;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record PlayerClientSettings(
        ChatMode chatMode,
        @Nullable String locale,
        MainHand mainHand,
        int viewDistance,
        boolean chatColors,
        boolean clientListingAllowed,
        List<String> skinParts) {

    public PlayerClientSettings {
        chatMode = chatMode != null ? chatMode : ChatMode.UNKNOWN;
        mainHand = mainHand != null ? mainHand : MainHand.UNKNOWN;
        skinParts = skinParts != null ? List.copyOf(skinParts) : List.of();
    }

    /**
     * @deprecated use {@link #chatMode()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public ChatMode getChatMode() {
        return chatMode;
    }

    /**
     * @deprecated use {@link #locale()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getLocale() {
        return locale;
    }

    /**
     * @deprecated use {@link #mainHand()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public MainHand getMainHand() {
        return mainHand;
    }

    /**
     * @deprecated use {@link #viewDistance()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public int getViewDistance() {
        return viewDistance;
    }

    /**
     * @deprecated use {@link #chatColors()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public boolean hasChatColors() {
        return chatColors;
    }

    /**
     * @deprecated use {@link #clientListingAllowed()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public boolean isClientListingAllowed() {
        return clientListingAllowed;
    }

    /**
     * @deprecated use {@link #skinParts()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    public List<String> getSkinParts() {
        return skinParts;
    }

    public enum ChatMode {
        ENABLED,
        COMMANDS_ONLY,
        HIDDEN,
        UNKNOWN;

        public static ChatMode parse(@Nullable String chatMode) {
            if (chatMode == null) {
                return UNKNOWN;
            }
            try {
                return valueOf(chatMode.toUpperCase().replace("CHAT_MODE_", ""));
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }

    public enum MainHand {
        LEFT,
        RIGHT,
        UNKNOWN;

        public static MainHand parse(@Nullable String mainHand) {
            if (mainHand == null) {
                return UNKNOWN;
            }
            try {
                return valueOf(mainHand.toUpperCase().replace("MAIN_HAND_", ""));
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }
}
