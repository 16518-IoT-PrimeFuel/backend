package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

public record SignInResource(String username, String password, Boolean rememberMe) {

    public SignInResource(String username, String password) {
        this(username, password, null);
    }
}
