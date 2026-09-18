package com.redisclone.server;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * WorkerEventLoop handles OP_READ and OP_WRITE events for a subset of connected clients.
 */
public class WorkerEventLoop extends EventLoop {

    private final com.redisclone.core.CommandExecutor executor;
    private final com.redisclone.core.RedisDatabase db;
    private final com.redisclone.command.CommandRegistry registry;
    private final com.redisclone.core.AofWriter aofWriter;

    public WorkerEventLoop(String name, 
                           com.redisclone.core.CommandExecutor executor, 
                           com.redisclone.core.RedisDatabase db, 
                           com.redisclone.command.CommandRegistry registry,
                           com.redisclone.core.AofWriter aofWriter) throws Exception {
        super(name);
        this.executor = executor;
        this.db = db;
        this.registry = registry;
        this.aofWriter = aofWriter;
    }

    /**
     * Registers a new SocketChannel with this worker's selector.
     * This method must be executed on the worker's thread to avoid concurrency issues.
     */
    public void registerConnection(SocketChannel channel) {
        execute(() -> {
            try {
                // Register for OP_READ initially
                SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
                
                // Attach a Connection object to track state
                Connection connection = new Connection(channel, this, key);
                key.attach(connection);
                
                System.out.println("[" + Thread.currentThread().getName() + "] Accepted connection from " + channel.getRemoteAddress());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void processKey(SelectionKey key) throws Exception {
        Connection connection = (Connection) key.attachment();
        
        if (key.isReadable()) {
            connection.handleRead();
        } else if (key.isValid() && key.isWritable()) {
            connection.handleWrite();
        }
    }

    public com.redisclone.core.CommandExecutor getExecutor() { return executor; }
    public com.redisclone.core.RedisDatabase getDb() { return db; }
    public com.redisclone.command.CommandRegistry getRegistry() { return registry; }
    public com.redisclone.core.AofWriter getAofWriter() { return aofWriter; }
}
