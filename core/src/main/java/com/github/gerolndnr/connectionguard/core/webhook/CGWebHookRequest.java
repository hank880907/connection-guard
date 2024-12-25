package com.github.gerolndnr.connectionguard.core.webhook;

public class CGWebHookRequest {
    private String content;

    public CGWebHookRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}
