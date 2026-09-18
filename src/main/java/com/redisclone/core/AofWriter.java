package com.redisclone.core;

import com.redisclone.protocol.RedisCommand;
import com.redisclone.protocol.RespEncoder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Handles appending commands to the Append-Only File (AOF).
 * Runs in its own background thread to avoid blocking the single-threaded CommandExecutor.
 */
public class AofWriter implements Runnable {
    private final BlockingQueue<RedisCommand> queue = new LinkedBlockingQueue<>();
    private final FileOutputStream fos;
    private final Thread thread;
    private volatile boolean running = true;

    public AofWriter(String filename) throws IOException {
        this.fos = new FileOutputStream(filename, true);
        this.thread = new Thread(this, "AOF-Writer-Thread");
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
        thread.interrupt();
        try {
            thread.join();
            fos.close();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    public void append(RedisCommand cmd) {
        // Only append write commands (SET, HSET, DEL, etc.)
        String name = cmd.getName();
        if (name.equals("SET") || name.equals("DEL") || name.equals("HSET") || 
            name.equals("SADD") || name.equals("ZADD") || name.equals("LPUSH") || 
            name.equals("LPOP") || name.equals("EXPIRE")) {
            queue.add(cmd);
        }
    }

    @Override
    public void run() {
        while (running || !queue.isEmpty()) {
            try {
                RedisCommand cmd = queue.take(); // Blocks until a command is available
                // Serialize command back to RESP array
                java.util.List<String> items = new java.util.ArrayList<>();
                for (byte[] arg : cmd.getArgs()) {
                    items.add(new String(arg));
                }
                byte[] encoded = RespEncoder.encodeArray(items);
                fos.write(encoded);
                
                // For a portfolio project, we could support fsync policies here
                // (e.g. fsync every second, or fsync always).
                // For simplicity, we just flush.
                fos.flush();
                fos.getFD().sync(); // Hard sync to disk
            } catch (InterruptedException e) {
                // Thread interrupted, will exit loop if running is false
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
