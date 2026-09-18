package com.redisclone.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * Represents a single client connection.
 * Buffers partial reads and writes.
 */
public class Connection {
    private final SocketChannel channel;
    private final WorkerEventLoop eventLoop;
    private final SelectionKey key;

    // Buffer for reading incoming data
    private final ByteBuffer readBuffer = ByteBuffer.allocate(4096);
    
    // For now, a simple buffer for writing. Later, we'll use a queue of buffers for pipelining.
    private ByteBuffer writeBuffer = null;

    // The parser state for this connection
    private final com.redisclone.protocol.RespParser parser = new com.redisclone.protocol.RespParser();

    public Connection(SocketChannel channel, WorkerEventLoop eventLoop, SelectionKey key) {
        this.channel = channel;
        this.eventLoop = eventLoop;
        this.key = key;
    }

    public void handleRead() {
        try {
            int bytesRead = channel.read(readBuffer);
            if (bytesRead == -1) {
                close();
                return;
            }

            if (bytesRead > 0) {
                processReadBuffer();
            }
        } catch (IOException e) {
            close();
        }
    }

    private void processReadBuffer() {
        // Prepare buffer for reading
        readBuffer.flip();
        
        // Parse commands
        java.util.List<com.redisclone.protocol.RedisCommand> commands = parser.parse(readBuffer);
        
        for (com.redisclone.protocol.RedisCommand cmd : commands) {
            // Submit to the single-threaded executor
            eventLoop.getExecutor().submit(() -> {
                try {
                    byte[] response = eventLoop.getRegistry().execute(eventLoop.getDb(), cmd);
                    eventLoop.getAofWriter().append(cmd);
                    send(response);
                } catch (Exception e) {
                    send(com.redisclone.protocol.RespEncoder.encodeError("ERR internal error"));
                    e.printStackTrace();
                }
            });
        }

        // Compact buffer (move unread bytes to start) so we can read more
        readBuffer.compact();
    }

    public void send(byte[] data) {
        // In a real scenario, we'd enqueue data and register OP_WRITE if we can't write it all immediately.
        // For simplicity right now, we'll try to write it directly or queue it if it fails.
        // We ensure we do this on the worker's thread.
        eventLoop.execute(() -> {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                
                // If there's already pending data, we shouldn't interleave. 
                // We'll build a proper write queue later.
                if (writeBuffer != null && writeBuffer.hasRemaining()) {
                    // (Simplified) Just overwrite or drop if busy in this skeleton
                    System.err.println("Dropping write due to pending data (to be fixed)");
                } else {
                    int written = channel.write(buffer);
                    if (buffer.hasRemaining()) {
                        // Not all data was written, save it and register OP_WRITE
                        this.writeBuffer = buffer;
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    }
                }
            } catch (IOException e) {
                close();
            }
        });
    }

    public void handleWrite() {
        if (writeBuffer != null && writeBuffer.hasRemaining()) {
            try {
                channel.write(writeBuffer);
                if (!writeBuffer.hasRemaining()) {
                    // Done writing, remove OP_WRITE interest
                    writeBuffer = null;
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
            } catch (IOException e) {
                close();
            }
        } else {
            // Nothing to write, remove OP_WRITE just in case
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }

    private void close() {
        try {
            System.out.println("Connection closed: " + channel.getRemoteAddress());
            key.cancel();
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
