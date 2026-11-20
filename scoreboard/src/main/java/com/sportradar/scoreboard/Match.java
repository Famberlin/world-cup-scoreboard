package com.sportradar.scoreboard;

/**
 * Immutable snapshot of a match. Every change on the scoreboard produces a new snapshot, so instances can be shared
 * freely between threads.
 *
 * <p>The canonical constructor is public so that custom {@link com.sportradar.scoreboard.store.MatchStore}
 * implementations can rebuild matches from their own representation.
 *
 * @param id identifier of the match
 * @param homeTeam home team name, NFC-normalized and stripped of surrounding whitespace
 * @param awayTeam away team name, NFC-normalized and stripped of surrounding whitespace
 * @param homeScore goals of the home team, from 0 to {@link #MAX_SCORE}
 * @param awayScore goals of the away team, from 0 to {@link #MAX_SCORE}
 * @param startSequence position in start order; a higher value means the match started later
 */
public record Match(MatchId id, String homeTeam, String awayTeam, int homeScore, int awayScore, long startSequence) {

    /**
     * Highest score a team can have. Far above any real football score, and low enough that {@link #totalScore()} of
     * two maximum scores cannot overflow an {@code int}.
     */
    public static final int MAX_SCORE = 999;

    /**
     * Maximum length of a team name in Unicode code points, counted after stripping and NFC normalization. The
     * longest FIFA member name has about 30.
     */
    public static final int MAX_TEAM_NAME_LENGTH = 64;

    /**
     * Creates a snapshot.
     *
     * @throws IllegalArgumentException if {@code id} or a team name is null, a team name is blank, longer than
     *     {@link #MAX_TEAM_NAME_LENGTH} or contains control or invisible format characters, or a score is negative or
     *     above {@link #MAX_SCORE}
     */
    public Match {
        Arguments.requireNotNull(id, "id");
        homeTeam = Arguments.requireTeam(homeTeam, "homeTeam");
        awayTeam = Arguments.requireTeam(awayTeam, "awayTeam");
        Arguments.requireScore(homeScore, "homeScore");
        Arguments.requireScore(awayScore, "awayScore");
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
     * @throws IllegalArgumentException if a score is negative or above {@link #MAX_SCORE}
     */
    public Match withScore(int homeScore, int awayScore) {
        return new Match(id, homeTeam, awayTeam, homeScore, awayScore, startSequence);
    }

    /** Returns the match as shown on a scoreboard, for example {@code "Uruguay 6 - Italy 6"}. */
    @Override
    public String toString() {
        return homeTeam + " " + homeScore + " - " + awayTeam + " " + awayScore;
    }
}
