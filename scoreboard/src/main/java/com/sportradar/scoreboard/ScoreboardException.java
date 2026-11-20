package com.sportradar.scoreboard;

/** Base class of the exceptions thrown when an operation conflicts with the state of the scoreboard. */
public class ScoreboardException extends RuntimeException {

    /**
     * Creates an exception.
     *
     * @param message description of the conflict
     */
    public ScoreboardException(String message) {
        super(message);
    }
}
