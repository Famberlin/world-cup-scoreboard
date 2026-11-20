package com.sportradar.scoreboard;

import java.util.Objects;

/**
 * Immutable snapshot of a match. Every change on the scoreboard produces a new snapshot, so instances can be shared
 * freely between threads.
 *
 * <p>The canonical constructor is public so that custom {@link com.sportradar.scoreboard.store.MatchStore}
 * implementations can rebuild matches from their own representation.
 *
 * @param id identifier of the match
 * @param homeTeam home team name, stripped of surrounding whitespace
 * @param awayTeam away team name, stripped of surrounding whitespace
 * @param homeScore goals of the home team, never negative
 * @param awayScore goals of the away team, never negative
 * @param startSequence position in start order; a higher value means the match started later
 */
public record Match(MatchId id, String homeTeam, String awayTeam, int homeScore, int awayScore, long startSequence) {

    /**
     * Creates a snapshot.
     *
     * @throws NullPointerException if {@code id} or a team name is null
     * @throws IllegalArgumentException if a team name is blank or a score is negative
     */
    public Match {
        Objects.requireNonNull(id, "id");
        homeTeam = requireTeam(homeTeam, "homeTeam");
        awayTeam = requireTeam(awayTeam, "awayTeam");
        requireScore(homeScore, "homeScore");
        requireScore(awayScore, "awayScore");
    }

    /**
     * Total number of goals in the match.
     *
     * @return home score plus away score
     */
    public int totalScore() {
        return homeScore + awayScore;
    }

    /**
     * Snapshot of this match with a different score.
     *
     * @param homeScore new home score
     * @param awayScore new away score
     * @return a new snapshot with the same id, teams and start sequence
     * @throws IllegalArgumentException if a score is negative
     */
    public Match withScore(int homeScore, int awayScore) {
        return new Match(id, homeTeam, awayTeam, homeScore, awayScore, startSequence);
    }

    /** Returns the match as shown on a scoreboard, for example {@code "Uruguay 6 - Italy 6"}. */
    @Override
    public String toString() {
        return homeTeam + " " + homeScore + " - " + awayTeam + " " + awayScore;
    }

    private static String requireTeam(String team, String name) {
        Objects.requireNonNull(team, name);
        String stripped = team.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return stripped;
    }

    private static void requireScore(int score, String name) {
        if (score < 0) {
            throw new IllegalArgumentException(name + " must not be negative, was " + score);
        }
    }
}
