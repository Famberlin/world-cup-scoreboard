package com.sportradar.scoreboard;

/** Thrown when no match in progress has the given id, including a match that has already finished. */
public class MatchNotFoundException extends ScoreboardException {

    /**
     * Creates an exception for the given id.
     *
     * @param id the id that was not found
     */
    public MatchNotFoundException(MatchId id) {
        super("No match in progress with id " + id.value());
    }
}
