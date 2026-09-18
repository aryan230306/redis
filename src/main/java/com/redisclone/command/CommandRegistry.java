package com.redisclone.command;

import com.redisclone.core.RedisDatabase;
import com.redisclone.protocol.RedisCommand;
import com.redisclone.protocol.RespEncoder;

import java.util.HashMap;
import java.util.Map;

public class CommandRegistry {
    private final Map<String, Command> commands = new HashMap<>();

    public CommandRegistry() {
        commands.put("PING", (db, cmd) -> {
            if (cmd.getArgs().size() > 1) {
                return RespEncoder.encodeBulkString(new String(cmd.getArgs().get(1)));
            }
            return RespEncoder.encodeSimpleString("PONG");
        });

        commands.put("ECHO", (db, cmd) -> {
            if (cmd.getArgs().size() != 2) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'echo' command");
            }
            return RespEncoder.encodeBulkString(new String(cmd.getArgs().get(1)));
        });

        commands.put("GET", (db, cmd) -> {
            if (cmd.getArgs().size() != 2) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'get' command");
            }
            String key = new String(cmd.getArgs().get(1));
            com.redisclone.core.RedisObject obj = db.getObject(key);
            if (obj == null) {
                return RespEncoder.encodeNull();
            }
            if (obj.getType() != com.redisclone.core.RedisObject.Type.STRING) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            return RespEncoder.encodeBulkString((String) obj.getValue());
        });

        commands.put("SET", (db, cmd) -> {
            if (cmd.getArgs().size() < 3) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'set' command");
            }
            String key = new String(cmd.getArgs().get(1));
            String value = new String(cmd.getArgs().get(2));
            com.redisclone.core.RedisObject obj = new com.redisclone.core.RedisObject(com.redisclone.core.RedisObject.Type.STRING, value);
            db.setObject(key, obj);
            return RespEncoder.encodeSimpleString("OK");
        });

        commands.put("DEL", (db, cmd) -> {
            if (cmd.getArgs().size() < 2) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'del' command");
            }
            int count = 0;
            for (int i = 1; i < cmd.getArgs().size(); i++) {
                String key = new String(cmd.getArgs().get(i));
                if (db.delete(key)) {
                    count++;
                }
            }
            return RespEncoder.encodeInteger(count);
        });

        commands.put("LPUSH", (db, cmd) -> {
            if (cmd.getArgs().size() < 3) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'lpush' command");
            }
            String key = new String(cmd.getArgs().get(1));
            com.redisclone.core.RedisObject obj = db.getObject(key);
            java.util.LinkedList<String> list;
            
            if (obj == null) {
                list = new java.util.LinkedList<>();
                db.setObject(key, new com.redisclone.core.RedisObject(com.redisclone.core.RedisObject.Type.LIST, list));
            } else if (obj.getType() != com.redisclone.core.RedisObject.Type.LIST) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            } else {
                list = (java.util.LinkedList<String>) obj.getValue();
            }

            int count = 0;
            for (int i = 2; i < cmd.getArgs().size(); i++) {
                list.addFirst(new String(cmd.getArgs().get(i)));
                count++;
            }
            return RespEncoder.encodeInteger(list.size());
        });

        commands.put("LPOP", (db, cmd) -> {
            if (cmd.getArgs().size() != 2) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'lpop' command");
            }
            String key = new String(cmd.getArgs().get(1));
            com.redisclone.core.RedisObject obj = db.getObject(key);
            
            if (obj == null) {
                return RespEncoder.encodeNull();
            }
            if (obj.getType() != com.redisclone.core.RedisObject.Type.LIST) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            
            java.util.LinkedList<String> list = (java.util.LinkedList<String>) obj.getValue();
            if (list.isEmpty()) {
                return RespEncoder.encodeNull();
            }
            String val = list.removeFirst();
            if (list.isEmpty()) {
                db.delete(key);
            }
            return RespEncoder.encodeBulkString(val);
        });

        commands.put("LRANGE", (db, cmd) -> {
            if (cmd.getArgs().size() != 4) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'lrange' command");
            }
            String key = new String(cmd.getArgs().get(1));
            int start = Integer.parseInt(new String(cmd.getArgs().get(2)));
            int stop = Integer.parseInt(new String(cmd.getArgs().get(3)));
            
            com.redisclone.core.RedisObject obj = db.getObject(key);
            if (obj == null) {
                return RespEncoder.encodeArray(java.util.Collections.emptyList());
            }
            if (obj.getType() != com.redisclone.core.RedisObject.Type.LIST) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            
            java.util.LinkedList<String> list = (java.util.LinkedList<String>) obj.getValue();
            
            // Handle negative indices
            if (start < 0) start = list.size() + start;
            if (stop < 0) stop = list.size() + stop;
            
            if (start < 0) start = 0;
            if (stop < 0) stop = 0;
            if (stop >= list.size()) stop = list.size() - 1;
            
            if (start > stop || start >= list.size()) {
                return RespEncoder.encodeArray(java.util.Collections.emptyList());
            }
            
            java.util.List<String> result = new java.util.ArrayList<>();
            for (int i = start; i <= stop; i++) {
                result.add(list.get(i));
            }
            return RespEncoder.encodeArray(result);
        });

        commands.put("HSET", (db, cmd) -> {
            if (cmd.getArgs().size() < 4 || cmd.getArgs().size() % 2 != 0) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'hset' command");
            }
            String key = new String(cmd.getArgs().get(1));
            com.redisclone.core.RedisObject obj = db.getObject(key);
            java.util.Map<String, String> hash;
            
            if (obj == null) {
                hash = new java.util.HashMap<>();
                db.setObject(key, new com.redisclone.core.RedisObject(com.redisclone.core.RedisObject.Type.HASH, hash));
            } else if (obj.getType() != com.redisclone.core.RedisObject.Type.HASH) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            } else {
                hash = (java.util.Map<String, String>) obj.getValue();
            }

            int added = 0;
            for (int i = 2; i < cmd.getArgs().size(); i += 2) {
                String field = new String(cmd.getArgs().get(i));
                String value = new String(cmd.getArgs().get(i + 1));
                if (hash.put(field, value) == null) {
                    added++;
                }
            }
            return RespEncoder.encodeInteger(added);
        });

        commands.put("HGET", (db, cmd) -> {
            if (cmd.getArgs().size() != 3) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'hget' command");
            }
            String key = new String(cmd.getArgs().get(1));
            String field = new String(cmd.getArgs().get(2));
            
            com.redisclone.core.RedisObject obj = db.getObject(key);
            if (obj == null) return RespEncoder.encodeNull();
            
            if (obj.getType() != com.redisclone.core.RedisObject.Type.HASH) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            
            java.util.Map<String, String> hash = (java.util.Map<String, String>) obj.getValue();
            String value = hash.get(field);
            if (value == null) return RespEncoder.encodeNull();
            
            return RespEncoder.encodeBulkString(value);
        });

        commands.put("SADD", (db, cmd) -> {
            if (cmd.getArgs().size() < 3) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'sadd' command");
            }
            String key = new String(cmd.getArgs().get(1));
            com.redisclone.core.RedisObject obj = db.getObject(key);
            java.util.Set<String> set;
            
            if (obj == null) {
                set = new java.util.HashSet<>();
                db.setObject(key, new com.redisclone.core.RedisObject(com.redisclone.core.RedisObject.Type.SET, set));
            } else if (obj.getType() != com.redisclone.core.RedisObject.Type.SET) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            } else {
                set = (java.util.Set<String>) obj.getValue();
            }

            int added = 0;
            for (int i = 2; i < cmd.getArgs().size(); i++) {
                if (set.add(new String(cmd.getArgs().get(i)))) {
                    added++;
                }
            }
            return RespEncoder.encodeInteger(added);
        });

        commands.put("SMEMBERS", (db, cmd) -> {
            if (cmd.getArgs().size() != 2) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'smembers' command");
            }
            String key = new String(cmd.getArgs().get(1));
            com.redisclone.core.RedisObject obj = db.getObject(key);
            
            if (obj == null) return RespEncoder.encodeArray(java.util.Collections.emptyList());
            
            if (obj.getType() != com.redisclone.core.RedisObject.Type.SET) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            
            java.util.Set<String> set = (java.util.Set<String>) obj.getValue();
            return RespEncoder.encodeArray(new java.util.ArrayList<>(set));
        });

        commands.put("ZADD", (db, cmd) -> {
            if (cmd.getArgs().size() < 4 || cmd.getArgs().size() % 2 != 0) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'zadd' command");
            }
            String key = new String(cmd.getArgs().get(1));
            com.redisclone.core.RedisObject obj = db.getObject(key);
            com.redisclone.core.SkipList zset;
            
            if (obj == null) {
                zset = new com.redisclone.core.SkipList();
                db.setObject(key, new com.redisclone.core.RedisObject(com.redisclone.core.RedisObject.Type.ZSET, zset));
            } else if (obj.getType() != com.redisclone.core.RedisObject.Type.ZSET) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            } else {
                zset = (com.redisclone.core.SkipList) obj.getValue();
            }

            int added = 0;
            for (int i = 2; i < cmd.getArgs().size(); i += 2) {
                double score;
                try {
                    score = Double.parseDouble(new String(cmd.getArgs().get(i)));
                } catch (NumberFormatException e) {
                    return RespEncoder.encodeError("ERR value is not a valid float");
                }
                String member = new String(cmd.getArgs().get(i + 1));
                if (zset.add(member, score)) {
                    added++;
                }
            }
            return RespEncoder.encodeInteger(added);
        });

        commands.put("ZRANGE", (db, cmd) -> {
            if (cmd.getArgs().size() != 4) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'zrange' command");
            }
            String key = new String(cmd.getArgs().get(1));
            int start;
            int stop;
            try {
                start = Integer.parseInt(new String(cmd.getArgs().get(2)));
                stop = Integer.parseInt(new String(cmd.getArgs().get(3)));
            } catch (NumberFormatException e) {
                return RespEncoder.encodeError("ERR value is not an integer or out of range");
            }
            
            com.redisclone.core.RedisObject obj = db.getObject(key);
            if (obj == null) {
                return RespEncoder.encodeArray(java.util.Collections.emptyList());
            }
            if (obj.getType() != com.redisclone.core.RedisObject.Type.ZSET) {
                return RespEncoder.encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            
            com.redisclone.core.SkipList zset = (com.redisclone.core.SkipList) obj.getValue();
            java.util.List<String> result = zset.range(start, stop);
            return RespEncoder.encodeArray(result);
        });
    }

    public byte[] execute(RedisDatabase db, RedisCommand cmd) {
        String name = cmd.getName();
        Command command = commands.get(name);
        if (command == null) {
            return RespEncoder.encodeError("ERR unknown command '" + name + "'");
        }
        return command.execute(db, cmd);
    }
}
