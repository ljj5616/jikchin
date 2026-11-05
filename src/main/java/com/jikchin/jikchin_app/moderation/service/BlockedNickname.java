package com.jikchin.jikchin_app.moderation.service;

public final class BlockedNickname {
    private BlockedNickname() {}

    public static String format(String nickname, boolean blocked) {
        return nickname != null && blocked ? nickname + "(차단)" : nickname;
    }
}
