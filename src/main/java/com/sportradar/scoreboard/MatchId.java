package com.sportradar.scoreboard;

import java.util.UUID;

/**
 * Opaque identifier of a match, returned by {@link Scoreboard#startMatch(String, String)} and used by the other
 * scoreboard operations. Two ids are equal when their values are equal.
 *
 * @param value the underlying unique value
 */
public record MatchId(UUID value) {

    /**
     * Creates an id.
     *
     * @throws IllegalArgumentException if {@code value} is null
     */
    public MatchId {
        Arguments.requireNotNull(value, "value");
    }

    static MatchId random() {
        return new MatchId(UUID.randomUUID());
    }
}
