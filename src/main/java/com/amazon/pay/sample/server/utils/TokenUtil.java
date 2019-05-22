package com.amazon.pay.sample.server.utils;

import com.amazon.pay.sample.server.storage.CacheMock;

import java.util.UUID;

public class TokenUtil {

    public static String storeByToken(String value) {
        String token = createToken();
        CacheMock.put(token, value);
        return token;
    }

    public static String get(String token) {
        return CacheMock.get(token);
    }

    public static String remove(String token) {
        return CacheMock.remove(token);
    }

    private static String createToken() {
        return UUID.randomUUID().toString();
    }
}
