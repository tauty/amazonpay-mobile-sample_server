package com.amazon.pay.sample.server.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 受注Objectアクセス用tokenと受注IDを紐づけるのに使用するCacheプロダクトのMock.
 * 実際にはRedisやMemcache、またはOracle・MySQL等のRDBMSを使用することを想定している.
 * Note: サンプルなので、メモリ上にデータを保持しています.よってサーバーを再起動したら情報が消えるのでご注意ください。
 */
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
