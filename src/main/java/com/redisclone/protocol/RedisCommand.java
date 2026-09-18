package com.redisclone.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed Redis Command (an Array of Bulk Strings in RESP).
 */
public class RedisCommand {
    private final List<byte[]> args = new ArrayList<>();

    public void addArg(byte[] arg) {
        args.add(arg);
    }

    public List<byte[]> getArgs() {
        return args;
    }

    public String getName() {
        if (args.isEmpty()) return "";
        return new String(args.get(0)).toUpperCase();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RedisCommand: [");
        for (int i = 0; i < args.size(); i++) {
            sb.append(new String(args.get(i)));
            if (i < args.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
