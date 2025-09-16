package com.jikchin.jikchin_app.adapter.in.web.security;

public record CurrentUser(Long userId, boolean needProfile, String tokenType)
{}
