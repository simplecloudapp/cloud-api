package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UpdateClientSettingsRequest {
    @Nullable
    private PlayerClientSettings.ChatMode chatMode;
    @Nullable
    private String locale;
    @Nullable
    private PlayerClientSettings.MainHand mainHand;
    @Nullable
    private Integer viewDistance;
    @Nullable
    private Boolean chatColors;
    @Nullable
    private Boolean clientListingAllowed;
    @Nullable
    private List<String> skinParts;

    public UpdateClientSettingsRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public PlayerClientSettings.ChatMode getChatMode() {
        return chatMode;
    }

    public void setChatMode(@Nullable PlayerClientSettings.ChatMode chatMode) {
        if (chatMode == PlayerClientSettings.ChatMode.UNKNOWN) {
            throw new IllegalArgumentException("chatMode must not be UNKNOWN");
        }
        this.chatMode = chatMode;
    }

    @Nullable
    public String getLocale() {
        return locale;
    }

    public void setLocale(@Nullable String locale) {
        this.locale = locale;
    }

    @Nullable
    public PlayerClientSettings.MainHand getMainHand() {
        return mainHand;
    }

    public void setMainHand(@Nullable PlayerClientSettings.MainHand mainHand) {
        if (mainHand == PlayerClientSettings.MainHand.UNKNOWN) {
            throw new IllegalArgumentException("mainHand must not be UNKNOWN");
        }
        this.mainHand = mainHand;
    }

    @Nullable
    public Integer getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(@Nullable Integer viewDistance) {
        this.viewDistance = viewDistance;
    }

    @Nullable
    public Boolean getChatColors() {
        return chatColors;
    }

    public void setChatColors(@Nullable Boolean chatColors) {
        this.chatColors = chatColors;
    }

    @Nullable
    public Boolean getClientListingAllowed() {
        return clientListingAllowed;
    }

    public void setClientListingAllowed(@Nullable Boolean clientListingAllowed) {
        this.clientListingAllowed = clientListingAllowed;
    }

    @Nullable
    public List<String> getSkinParts() {
        return skinParts;
    }

    public void setSkinParts(@Nullable List<String> skinParts) {
        this.skinParts = skinParts == null ? null : List.copyOf(skinParts);
    }

    public static class Builder {
        private PlayerClientSettings.ChatMode chatMode;
        private String locale;
        private PlayerClientSettings.MainHand mainHand;
        private Integer viewDistance;
        private Boolean chatColors;
        private Boolean clientListingAllowed;
        private List<String> skinParts;

        public Builder chatMode(PlayerClientSettings.ChatMode chatMode) {
            this.chatMode = chatMode;
            return this;
        }

        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        public Builder mainHand(PlayerClientSettings.MainHand mainHand) {
            this.mainHand = mainHand;
            return this;
        }

        public Builder viewDistance(Integer viewDistance) {
            this.viewDistance = viewDistance;
            return this;
        }

        public Builder chatColors(Boolean chatColors) {
            this.chatColors = chatColors;
            return this;
        }

        public Builder clientListingAllowed(Boolean clientListingAllowed) {
            this.clientListingAllowed = clientListingAllowed;
            return this;
        }

        public Builder skinParts(List<String> skinParts) {
            this.skinParts = skinParts;
            return this;
        }

        public UpdateClientSettingsRequest build() {
            UpdateClientSettingsRequest request = new UpdateClientSettingsRequest();
            request.setChatMode(chatMode);
            request.setLocale(locale);
            request.setMainHand(mainHand);
            request.setViewDistance(viewDistance);
            request.setChatColors(chatColors);
            request.setClientListingAllowed(clientListingAllowed);
            request.setSkinParts(skinParts);
            return request;
        }
    }
}
