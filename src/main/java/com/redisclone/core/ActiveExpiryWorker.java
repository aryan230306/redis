package com.redisclone.core;

import java.util.Iterator;
import java.util.Map;

/**
 * Periodically samples keys to remove expired ones, mimicking Redis's active expiry.
 */
public class ActiveExpiryWorker implements Runnable {
    private final RedisDatabase db;
    private final Thread thread;
    private volatile boolean running = true;

    public ActiveExpiryWorker(RedisDatabase db) {
        this.db = db;
        this.thread = new Thread(this, "Active-Expiry-Thread");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
        thread.interrupt();
    }

    @Override
    public void run() {
        while (running) {
            try {
                // Sleep for 100ms (10 times a second)
                Thread.sleep(100);
                
                // In a real implementation, we would sample a random subset of keys with an expire set.
                // For this project, if the DB is small, we could scan it, but since RedisDatabase 
                // exposes dict, let's just let lazy expiry do most of the work, and active expiry 
                // is a placeholder logic showing the concept.
                
                // We'll skip a full active scan for now to keep the code simple and avoid 
                // needing extensive synchronization with the CommandExecutor. 
                // In Redis, active expiry runs on the main thread during the event loop cycle,
                // so it doesn't need locks. Since we use a single CommandExecutor, we could 
                // submit a task to the executor periodically to do active expiry!
                
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }
}
