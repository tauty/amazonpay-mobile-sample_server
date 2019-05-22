package com.amazon.pay.sample.server.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheMock {

    private static final Map<String, String> map = new ConcurrentHashMap<>();

    public static String get(String key) {
        return map.get(key);
    }

    public static void put(String key, String value) {
        map.put(key, value);
    }

    public static String remove(String key) {
        return map.remove(key);
    }
}
