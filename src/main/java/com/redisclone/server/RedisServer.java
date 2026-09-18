package com.redisclone.server;

import com.redisclone.core.CommandExecutor;
import com.redisclone.core.RedisDatabase;
import com.redisclone.command.CommandRegistry;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;

public class RedisServer {
    private static final int PORT = 6379;
    private static final int NUM_WORKERS = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) {
        System.out.println("Starting Java Redis Clone (Portfolio Edition) on port " + PORT + "...");
        System.out.println("Using 1 Boss thread and " + NUM_WORKERS + " Worker threads for I/O.");

        try {
            // Core Components
            com.redisclone.core.RedisDatabase db = new com.redisclone.core.RedisDatabase();
            com.redisclone.command.CommandRegistry registry = new com.redisclone.command.CommandRegistry();
            com.redisclone.core.CommandExecutor executor = new com.redisclone.core.CommandExecutor();
            executor.start();
            
            com.redisclone.core.AofWriter aofWriter = new com.redisclone.core.AofWriter("appendonly.aof");
            aofWriter.start();
            
            com.redisclone.core.ActiveExpiryWorker expiryWorker = new com.redisclone.core.ActiveExpiryWorker(db);
            expiryWorker.start();

            // HTTP dashboard API (port 8080)
            com.redisclone.http.HttpApiServer httpApi = new com.redisclone.http.HttpApiServer(db, registry);
            httpApi.start();

            // Create Workers
            List<WorkerEventLoop> workers = new ArrayList<>();
            for (int i = 0; i < NUM_WORKERS; i++) {
                WorkerEventLoop worker = new WorkerEventLoop("Worker-EventLoop-" + i, executor, db, registry, aofWriter);
                workers.add(worker);
                worker.start();
            }

            // Create and bind ServerSocket
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(PORT));
            serverSocket.configureBlocking(false);

            // Create Boss
            BossEventLoop boss = new BossEventLoop(serverSocket, workers);
            boss.start();

            System.out.println("Server is running. Press Ctrl+C to stop.");
            
            // Wait forever
            Thread.currentThread().join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
