package com.redisclone.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A state-machine based RESP parser designed to handle partial reads from NIO buffers.
 * Clients typically send requests as an Array of Bulk Strings.
 */
public class RespParser {
    
    private enum State {
        READ_ARRAY_HEADER,
        READ_ARRAY_LENGTH,
        READ_BULK_STRING_HEADER,
        READ_BULK_STRING_LENGTH,
        READ_BULK_STRING_DATA
    }

    private State state = State.READ_ARRAY_HEADER;
    private int expectedArgs = 0;
    private int currentArgLength = 0;
    private RedisCommand currentCommand = new RedisCommand();

    /**
     * Parses the buffer and returns a list of fully parsed commands.
     * The buffer's position is advanced only for fully parsed tokens.
     */
    public List<RedisCommand> parse(ByteBuffer buffer) {
        List<RedisCommand> commands = new ArrayList<>();

        while (buffer.hasRemaining()) {
            buffer.mark(); // Mark current position to rollback if data is incomplete

            switch (state) {
                case READ_ARRAY_HEADER:
                    if (buffer.get() == '*') {
                        state = State.READ_ARRAY_LENGTH;
                    } else {
                        // Redis handles inline commands like "PING\r\n" but for simplicity
                        // and adherence to RESP2/3, we assume standard arrays for now.
                        // A full implementation would handle inline commands here.
                        System.err.println("Expected array header '*'");
                        buffer.position(buffer.limit()); // Consume all to recover
                        reset();
                        return commands;
                    }
                    break;

                case READ_ARRAY_LENGTH:
                    Integer arrayLen = readInteger(buffer);
                    if (arrayLen == null) {
                        buffer.reset(); // Not enough data
                        return commands;
                    }
                    expectedArgs = arrayLen;
                    if (expectedArgs <= 0) {
                        reset(); // Empty array
                    } else {
                        state = State.READ_BULK_STRING_HEADER;
                    }
                    break;

                case READ_BULK_STRING_HEADER:
                    if (buffer.get() == '$') {
                        state = State.READ_BULK_STRING_LENGTH;
                    } else {
                        System.err.println("Expected bulk string header '$'");
                        reset();
                    }
                    break;

                case READ_BULK_STRING_LENGTH:
                    Integer strLen = readInteger(buffer);
                    if (strLen == null) {
                        buffer.reset();
                        return commands;
                    }
                    currentArgLength = strLen;
                    state = State.READ_BULK_STRING_DATA;
                    break;

                case READ_BULK_STRING_DATA:
                    // Need strLen bytes + 2 for \r\n
                    if (buffer.remaining() < currentArgLength + 2) {
                        buffer.reset();
                        return commands;
                    }
                    
                    byte[] data = new byte[currentArgLength];
                    buffer.get(data);
                    
                    // consume \r\n
                    byte cr = buffer.get();
                    byte lf = buffer.get();
                    
                    if (cr != '\r' || lf != '\n') {
                        System.err.println("Expected CRLF after bulk string");
                        reset();
                        break;
                    }

                    currentCommand.addArg(data);
                    expectedArgs--;

                    if (expectedArgs == 0) {
                        commands.add(currentCommand);
                        reset(); // Ready for next command
                    } else {
                        state = State.READ_BULK_STRING_HEADER;
                    }
                    break;
            }
        }
        return commands;
    }

    private void reset() {
        state = State.READ_ARRAY_HEADER;
        expectedArgs = 0;
        currentArgLength = 0;
        currentCommand = new RedisCommand();
    }

    /**
     * Reads an integer terminated by \r\n. Returns null if \r\n is not yet present.
     */
    private Integer readInteger(ByteBuffer buffer) {
        int startPos = buffer.position();
        int value = 0;
        boolean foundCr = false;
        boolean foundLf = false;
        boolean negative = false;

        if (buffer.hasRemaining() && buffer.get(buffer.position()) == '-') {
            negative = true;
            buffer.get(); // consume '-'
        }

        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\r') {
                foundCr = true;
            } else if (b == '\n' && foundCr) {
                foundLf = true;
                break;
            } else {
                value = value * 10 + (b - '0');
            }
        }

        if (foundCr && foundLf) {
            return negative ? -value : value;
        } else {
            // Rollback to startPos, not enough data
            buffer.position(startPos);
            return null;
        }
    }
}
