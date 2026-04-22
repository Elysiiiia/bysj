package com.jy26n139.phonerecommend.config;

public final class AuthContext {
    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        CURRENT.set(user);
    }

    public static AuthUser get() {
        return CURRENT.get();
    }

    public static Long userId() {
        AuthUser user = CURRENT.get();
        return user == null ? null : user.userId();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record AuthUser(Long userId, String username, String role) {
    }
}
