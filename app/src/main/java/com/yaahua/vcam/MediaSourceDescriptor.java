package com.yaahua.vcam;

public final class MediaSourceDescriptor {
    public enum Type {
        LOCAL_FILE,
        STREAM_URL
    }

    public final Type type;
    public final String localPath;
    public final String streamUrl;
    public final boolean useProviderPfd;
    public final boolean autoReconnect;
    public final boolean enableLocalFallback;
    public final String transportHint;
    public final long timeoutMs;

    private MediaSourceDescriptor(Builder builder) {
        this.type = builder.type;
        this.localPath = builder.localPath;
        this.streamUrl = builder.streamUrl;
        this.useProviderPfd = builder.useProviderPfd;
        this.autoReconnect = builder.autoReconnect;
        this.enableLocalFallback = builder.enableLocalFallback;
        this.transportHint = builder.transportHint;
        this.timeoutMs = builder.timeoutMs;
    }

    public boolean isStream() { return type == Type.STREAM_URL; }

    public boolean isValid() {
        if (type == Type.LOCAL_FILE) return localPath != null && !localPath.isEmpty();
        else return streamUrl != null && !streamUrl.isEmpty();
    }

    public static Builder localFile(String path) { return new Builder(Type.LOCAL_FILE).localPath(path); }
    public static Builder stream(String url) { return new Builder(Type.STREAM_URL).streamUrl(url); }

    public static class Builder {
        Type type;
        String localPath;
        String streamUrl;
        boolean useProviderPfd;
        boolean autoReconnect = true;
        boolean enableLocalFallback = true;
        String transportHint = "auto";
        long timeoutMs = 8000L;

        Builder(Type type) { this.type = type; }
        public Builder localPath(String v) { this.localPath = v; return this; }
        public Builder streamUrl(String v) { this.streamUrl = v; return this; }
        public Builder useProviderPfd(boolean v) { this.useProviderPfd = v; return this; }
        public Builder autoReconnect(boolean v) { this.autoReconnect = v; return this; }
        public Builder enableLocalFallback(boolean v) { this.enableLocalFallback = v; return this; }
        public Builder transportHint(String v) { this.transportHint = v; return this; }
        public Builder timeoutMs(long v) { this.timeoutMs = v; return this; }
        public MediaSourceDescriptor build() { return new MediaSourceDescriptor(this); }
    }
}