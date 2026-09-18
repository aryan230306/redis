package com.redisclone.server;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Base class for a single-threaded NIO event loop.
 */
public abstract class EventLoop implements Runnable {
    protected final Selector selector;
    private final Thread thread;
    private volatile boolean running = true;
    
    // Tasks to be executed in this event loop's thread
    private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();

    public EventLoop(String name) throws Exception {
        this.selector = Selector.open();
        this.thread = new Thread(this, name);
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
        selector.wakeup();
    }
    
    public void execute(Runnable task) {
        taskQueue.add(task);
        selector.wakeup(); // Wake up the selector to process the task immediately
    }

    @Override
    public void run() {
        while (running) {
            try {
                // Run pending tasks
                Runnable task;
                while ((task = taskQueue.poll()) != null) {
                    task.run();
                }

                selector.select();
                
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (!key.isValid()) continue;

                    processKey(key);
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("Error in event loop: " + thread.getName());
                    e.printStackTrace();
                }
            }
        }
        
        try {
            selector.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract void processKey(SelectionKey key) throws Exception;
}
