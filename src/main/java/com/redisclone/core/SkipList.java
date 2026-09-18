package com.redisclone.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A basic SkipList implementation for Redis Sorted Sets.
 * Elements are sorted by score (double), then by member (String) lexicographically.
 */
public class SkipList {
    private static final int MAX_LEVEL = 32;
    private static final double P = 0.25;

    private class Node {
        String member;
        double score;
        Node[] forward;

        Node(String member, double score, int level) {
            this.member = member;
            this.score = score;
            this.forward = new Node[level];
        }
    }

    private Node header;
    private int level;
    private int length;
    private Random random;

    public SkipList() {
        this.level = 1;
        this.length = 0;
        this.header = new Node(null, Double.NEGATIVE_INFINITY, MAX_LEVEL);
        this.random = new Random();
    }

    private int randomLevel() {
        int lvl = 1;
        while (random.nextDouble() < P && lvl < MAX_LEVEL) {
            lvl++;
        }
        return lvl;
    }

    // Returns true if added, false if updated
    public boolean add(String member, double score) {
        Node[] update = new Node[MAX_LEVEL];
        Node x = header;

        // Find insertion point
        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null && 
                  (x.forward[i].score < score || 
                  (x.forward[i].score == score && x.forward[i].member.compareTo(member) < 0))) {
                x = x.forward[i];
            }
            update[i] = x;
        }

        x = x.forward[0];

        // If member already exists, update score
        if (x != null && x.member.equals(member)) {
            if (x.score == score) return false;
            // Actually, in Redis, updating a score means removing and re-inserting
            remove(member, x.score);
            return insert(member, score, update); // recalculate update vector
        } else {
            return insert(member, score, update);
        }
    }

    private boolean insert(String member, double score, Node[] update) {
        int lvl = randomLevel();
        if (lvl > level) {
            for (int i = level; i < lvl; i++) {
                update[i] = header;
            }
            level = lvl;
        }

        Node x = new Node(member, score, lvl);
        for (int i = 0; i < lvl; i++) {
            x.forward[i] = update[i].forward[i];
            update[i].forward[i] = x;
        }
        length++;
        return true;
    }

    public boolean remove(String member, double score) {
        Node[] update = new Node[MAX_LEVEL];
        Node x = header;

        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null && 
                  (x.forward[i].score < score || 
                  (x.forward[i].score == score && x.forward[i].member.compareTo(member) < 0))) {
                x = x.forward[i];
            }
            update[i] = x;
        }

        x = x.forward[0];
        if (x != null && x.member.equals(member) && x.score == score) {
            for (int i = 0; i < level; i++) {
                if (update[i].forward[i] != x) break;
                update[i].forward[i] = x.forward[i];
            }
            while (level > 1 && header.forward[level - 1] == null) {
                level--;
            }
            length--;
            return true;
        }
        return false;
    }

    public List<String> range(int start, int stop) {
        if (start < 0) start = length + start;
        if (stop < 0) stop = length + stop;
        if (start < 0) start = 0;
        if (stop < 0) stop = 0;
        if (stop >= length) stop = length - 1;
        
        List<String> result = new ArrayList<>();
        if (start > stop || start >= length) {
            return result;
        }

        Node x = header.forward[0];
        int count = 0;
        while (x != null && count <= stop) {
            if (count >= start) {
                result.add(x.member);
            }
            x = x.forward[0];
            count++;
        }
        return result;
    }
}
