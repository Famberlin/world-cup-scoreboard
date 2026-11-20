package com.sportradar.scoreboard;

/** Thrown when a match is started for a team that already plays in a match in progress. */
public class TeamAlreadyPlayingException extends ScoreboardException {

    /**
     * Creates an exception for the given team.
     *
     * @param team the team that is already playing
     */
    public TeamAlreadyPlayingException(String team) {
        super(team + " is already playing in a match in progress");
    }
}
