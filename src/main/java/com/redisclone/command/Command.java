package com.redisclone.command;

import com.redisclone.core.RedisDatabase;
import com.redisclone.protocol.RedisCommand;

/**
 * Interface for all Redis commands.
 */
public interface Command {
    /**
     * Executes the command against the database and returns the RESP encoded response.
     */
    byte[] execute(RedisDatabase db, RedisCommand cmd);
}
