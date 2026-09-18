package com.redisclone.core;

import com.redisclone.server.Connection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The single-threaded engine that executes commands against the keyspace.
 * This guarantees thread-safety for all data structure operations, 
 * mimicking Redis's single-threaded nature while I/O is multi-threaded.
 */
public class CommandExecutor implements Runnable {
    
    private final Thread thread;
    private volatile boolean running = true;
    
    // A queue of tasks submitted by Worker threads after parsing RESP
    private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();
    
    // A simple monitor object to wake up the executor when a task is submitted
    private final Object monitor = new Object();

    public CommandExecutor() {
        this.thread = new Thread(this, "Command-Executor-Thread");
    }
    
    public void start() {
        thread.start();
    }
    
    public void stop() {
        running = false;
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    /**
     * Called by WorkerEventLoops to submit a command for execution.
     */
    public void submit(Runnable task) {
        taskQueue.add(task);
        synchronized (monitor) {
            monitor.notify();
        }
    }

    @Override
    public void run() {
        while (running) {
            Runnable task = taskQueue.poll();
            
            if (task != null) {
                try {
                    task.run();
                } catch (Exception e) {
                    System.err.println("Error executing command: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // If queue is empty, wait for a new task
                synchronized (monitor) {
                    try {
                        // Double check before waiting to avoid race conditions
                        if (taskQueue.isEmpty()) {
                            monitor.wait();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        running = false;
                    }
                }
            }
        }
    }
}
