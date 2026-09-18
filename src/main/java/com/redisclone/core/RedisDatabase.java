package com.redisclone.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the Redis keyspace.
 * Accessed ONLY by the single-threaded CommandExecutor.
 */
public class RedisDatabase {
    private final Map<String, RedisObject> dict = new HashMap<>();

    public RedisObject getObject(String key) {
        RedisObject obj = dict.get(key);
        if (obj != null && obj.isExpired(System.currentTimeMillis())) {
            dict.remove(key); // lazy expiry
            return null;
        }
        return obj;
    }

    public void setObject(String key, RedisObject obj) {
        dict.put(key, obj);
    }

    public boolean delete(String key) {
        return dict.remove(key) != null;
    }
    
    public int size() {
        return dict.size();
    }
}
