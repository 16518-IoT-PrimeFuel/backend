package com.primefuel.fulltank.platform.iam.domain.model.commands;

/** {@code rememberMe} null keeps the long-lived (7 days) token for clients that do not send it. */
public record SignInCommand(String username, String password, Boolean rememberMe) {

    public SignInCommand(String username, String password) {
        this(username, password, null);
    }

    public boolean persistentSession() {
        return rememberMe == null || rememberMe;
    }
}
