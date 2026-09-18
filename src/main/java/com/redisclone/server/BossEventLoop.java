package com.redisclone.server;

import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BossEventLoop accepts new incoming TCP connections and distributes them
 * in a round-robin fashion to a pool of WorkerEventLoops.
 */
public class BossEventLoop extends EventLoop {
    private final ServerSocketChannel serverChannel;
    private final List<WorkerEventLoop> workers;
    private final AtomicInteger nextWorkerIndex = new AtomicInteger(0);

    public BossEventLoop(ServerSocketChannel serverChannel, List<WorkerEventLoop> workers) throws Exception {
        super("Boss-EventLoop");
        this.serverChannel = serverChannel;
        this.workers = workers;
        
        // Register the server channel for OP_ACCEPT
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    protected void processKey(SelectionKey key) throws Exception {
        if (key.isAcceptable()) {
            SocketChannel clientChannel = serverChannel.accept();
            if (clientChannel != null) {
                clientChannel.configureBlocking(false);
                
                // Select a worker event loop round-robin
                WorkerEventLoop worker = workers.get(Math.abs(nextWorkerIndex.getAndIncrement() % workers.size()));
                
                // Offload the registration to the worker's thread
                worker.registerConnection(clientChannel);
            }
        }
    }
}
