package com.jikchin.jikchin_app.auth.security;

public record CurrentUser(Long userId, boolean needProfile, String tokenType)
{}
