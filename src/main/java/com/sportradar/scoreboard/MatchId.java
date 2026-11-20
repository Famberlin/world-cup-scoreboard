package com.sportradar.scoreboard;

import java.util.Objects;
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
     * @throws NullPointerException if {@code value} is null
     */
    public MatchId {
        Objects.requireNonNull(value, "value");
    }

    static MatchId random() {
        return new MatchId(UUID.randomUUID());
    }
}
