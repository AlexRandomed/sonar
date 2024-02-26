package org.entcore.audience.reaction.model;

import java.util.Map;

public class ReactionCounters {
    private final Map<String, Integer> countByType;

    public ReactionCounters(Map<String, Integer> countByType) {
        this.countByType = countByType;
    }

    public Map<String, Integer> getCountByType() {
        return countByType;
    }

    public int getAllReactionsCounter() {
        return countByType.values().stream().mapToInt(Integer::valueOf).sum();
    }
}
