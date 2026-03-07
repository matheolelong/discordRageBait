package com.ragebait;

import java.util.HashSet;
import java.util.Set;

public class QuiExclusionManager {

    private static QuiExclusionManager instance;
    private final Set<Long> excludedUsers = new HashSet<>();

    private QuiExclusionManager() {}

    public static QuiExclusionManager getInstance() {
        if (instance == null) {
            instance = new QuiExclusionManager();
        }
        return instance;
    }

    public void addExclusion(long userId) {
        excludedUsers.add(userId);
    }

    public void removeExclusion(long userId) {
        excludedUsers.remove(userId);
    }

    public boolean isExcluded(long userId) {
        return excludedUsers.contains(userId);
    }

    public Set<Long> getExcludedUsers() {
        return new HashSet<>(excludedUsers);
    }

    public void clearExclusions() {
        excludedUsers.clear();
    }
}
