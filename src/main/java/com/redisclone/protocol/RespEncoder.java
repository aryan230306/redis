package com.redisclone.protocol;

import java.util.List;

/**
 * Encodes responses into RESP format.
 */
public class RespEncoder {
    
    private static final String CRLF = "\r\n";

    public static byte[] encodeSimpleString(String s) {
        return ("+" + s + CRLF).getBytes();
    }

    public static byte[] encodeError(String errMsg) {
        return ("-" + errMsg + CRLF).getBytes();
    }

    public static byte[] encodeInteger(long i) {
        return (":" + i + CRLF).getBytes();
    }

    public static byte[] encodeBulkString(String s) {
        if (s == null) {
            return "$-1\r\n".getBytes();
        }
        byte[] data = s.getBytes();
        return ("$" + data.length + CRLF + s + CRLF).getBytes();
    }

    public static byte[] encodeNull() {
        return "$-1\r\n".getBytes();
    }

    public static byte[] encodeArray(List<String> items) {
        if (items == null) {
            return "*-1\r\n".getBytes();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(items.size()).append(CRLF);
        for (String item : items) {
            if (item == null) {
                sb.append("$-1").append(CRLF);
            } else {
                byte[] data = item.getBytes();
                sb.append("$").append(data.length).append(CRLF).append(item).append(CRLF);
            }
        }
        return sb.toString().getBytes();
    }
}
