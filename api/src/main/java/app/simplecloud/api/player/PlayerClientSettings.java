package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PlayerClientSettings {
    private ChatMode chatMode = ChatMode.UNKNOWN;
    @Nullable
    private String locale;
    private MainHand mainHand = MainHand.UNKNOWN;
    private int viewDistance;
    private boolean chatColors;
    private boolean clientListingAllowed;
    private List<String> skinParts = List.of();

    public PlayerClientSettings() {
    }

    public ChatMode getChatMode() {
        return chatMode;
    }

    public void setChatMode(ChatMode chatMode) {
        this.chatMode = chatMode != null ? chatMode : ChatMode.UNKNOWN;
    }

    @Nullable
    public String getLocale() {
        return locale;
    }

    public void setLocale(@Nullable String locale) {
        this.locale = locale;
    }

    public MainHand getMainHand() {
        return mainHand;
    }

    public void setMainHand(MainHand mainHand) {
        this.mainHand = mainHand != null ? mainHand : MainHand.UNKNOWN;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
    }

    public boolean hasChatColors() {
        return chatColors;
    }

    public void setChatColors(boolean chatColors) {
        this.chatColors = chatColors;
    }

    public boolean isClientListingAllowed() {
        return clientListingAllowed;
    }

    public void setClientListingAllowed(boolean clientListingAllowed) {
        this.clientListingAllowed = clientListingAllowed;
    }

    public List<String> getSkinParts() {
        return skinParts;
    }

    public void setSkinParts(@Nullable List<String> skinParts) {
        this.skinParts = skinParts != null ? List.copyOf(skinParts) : List.of();
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
