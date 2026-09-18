package com.redisclone.core;

public class RedisObject {
    public enum Type {
        STRING, LIST, HASH, SET, ZSET
    }

    private final Type type;
    private final Object value;
    private long expireAtMs = -1; // -1 means no expiry

    public RedisObject(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public void setExpireAtMs(long expireAtMs) {
        this.expireAtMs = expireAtMs;
    }

    public long getExpireAtMs() {
        return expireAtMs;
    }
    
    public boolean isExpired(long nowMs) {
        if (expireAtMs == -1) return false;
        return nowMs > expireAtMs;
    }
}
