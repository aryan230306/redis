# Java Redis Clone

A high-performance, portfolio-ready Redis clone built from scratch in pure Java (no external libraries — no Netty, no Jackson).

## Architecture Highlights

This project implements a **Multi-Reactor NIO Server** paired with a **Single-Threaded Command Executor**, mirroring Redis's own hybrid threading model introduced in Redis 6.0.

```
Incoming Connections
       │
  BossEventLoop (1 thread)
       │  accept() → round-robin
  ┌────┴────┐
  Worker-0  Worker-1  ... Worker-N   (NIO, one per CPU core)
       │
  ConcurrentQueue
       │
  CommandExecutor (1 thread) ← no locks needed on keyspace
       │
  RedisDatabase
```

## Features

### Networking
- `java.nio` non-blocking I/O, zero blocking threads
- Multi-Reactor Boss/Worker pattern
- `OP_ACCEPT`, `OP_READ`, `OP_WRITE` via `Selector`

### RESP Protocol
- State-machine based streaming parser (handles partial NIO reads correctly)
- Full RESP encoder (Simple Strings, Errors, Integers, Bulk Strings, Arrays)

### Commands Implemented
| Command | Type |
|---------|------|
| `PING`, `ECHO` | Server |
| `GET`, `SET`, `DEL` | Strings |
| `LPUSH`, `LPOP`, `LRANGE` | Lists |
| `HSET`, `HGET` | Hashes |
| `SADD`, `SMEMBERS` | Sets |
| `ZADD`, `ZRANGE` | Sorted Sets (Skip List) |

### Data Structures
- **Strings**: Raw Java `String`
- **Lists**: `LinkedList<String>`
- **Hashes**: `HashMap<String, String>`
- **Sets**: `HashSet<String>`
- **Sorted Sets**: Custom **Skip List** implementation (not TreeMap — genuine O(log n) probabilistic structure, just like real Redis)

### Persistence
- **AOF (Append-Only File)**: Background thread writes all mutating commands to `appendonly.aof`, with `fsync` on every write
- **Lazy Expiry**: TTL is checked on key access
- **Active Expiry**: Background worker periodically sweeps for expired keys

## Running Locally

**Requirements:** Java 21+

```bash
# Clone the repo
git clone https://github.com/YOUR_USERNAME/java-redis-clone.git
cd java-redis-clone

# Build and run
./build_and_run.sh
```

The server starts on **port 6379**.

Test it with `redis-cli` or any Redis client:
```bash
redis-cli -p 6379 PING
redis-cli -p 6379 SET name "Aryan"
redis-cli -p 6379 GET name
redis-cli -p 6379 ZADD leaderboard 100 "alice" 200 "bob"
redis-cli -p 6379 ZRANGE leaderboard 0 -1
```

## Deploying to Railway

1. Push this repo to GitHub
2. Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub
3. Set the **Start Command** to: `./build_and_run.sh`
4. Expose port **6379** in the Railway dashboard

## Known Deviations from Real Redis

| Feature | Real Redis | This Clone |
|---------|-----------|------------|
| Inline command parsing | ✅ Supported | ❌ RESP arrays only |
| `EXPIRE` / `TTL` commands | ✅ | ❌ Not yet implemented |
| `RPUSH`, `RPOP` | ✅ | ❌ Left end only |
| `HGETALL`, `HDEL` | ✅ | ❌ Basic HSET/HGET only |
| Active expiry scan | Full probabilistic | Placeholder (lazy only) |
| Pub/Sub | ✅ | ❌ Not yet |
| RDB snapshotting | ✅ | ❌ AOF only |
| Cluster mode | ✅ | ❌ Single node |

## License

MIT
