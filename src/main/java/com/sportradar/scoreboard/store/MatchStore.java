package com.sportradar.scoreboard.store;

import com.sportradar.scoreboard.Match;
import com.sportradar.scoreboard.MatchId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Storage of matches in progress and of finished match results. Implement it to keep the scoreboard in custom storage and pass the implementation
 * to {@link com.sportradar.scoreboard.Scoreboard#Scoreboard(MatchStore)}.
 *
 * <p>A store only keeps and returns matches. Validation, uniqueness of teams and the summary order are enforced by
 * the scoreboard, so a store cannot change how the scoreboard behaves.
 *
 * <p>Thread safety: the scoreboard calls its store only while holding its own lock, so a store used by a single
 * scoreboard does not need to be thread-safe. A store shared by several scoreboards or processes is responsible for
 * its own consistency.
 */
public interface MatchStore {

    /**
     * Next value of a strictly increasing sequence, used to order matches by start. The sequence must keep
     * increasing for the lifetime of the stored data; a persistent store must persist it as well.
     *
     * @return a value greater than any value returned before
     */
    long nextSequence();

    /**
     * Inserts the match, or replaces the stored match with the same id.
     *
     * @param match the match to store
     */
    void save(Match match);

    /**
     * Looks up a match in progress.
     *
     * @param id the match id
     * @return the stored match, or empty if no match has this id
     */
    Optional<Match> findById(MatchId id);

    /**
     * Removes a match.
     *
     * @param id the match id
     * @return whether a match was removed
     */
    boolean delete(MatchId id);

    /**
     * All matches in progress.
     *
     * @return the stored matches, in no particular order
     */
    Collection<Match> findAll();

    /**
     * Whether the team plays, home or away, in a match in progress. Team names are compared ignoring case. The
     * default implementation scans {@link #findAll()}; stores with an index should override it.
     *
     * @param team the team name
     * @return whether the team is in a stored match
     */
    default boolean isTeamPlaying(String team) {
        return findAll().stream()
                .anyMatch(match -> match.homeTeam().equalsIgnoreCase(team)
                        || match.awayTeam().equalsIgnoreCase(team));
    }

    /**
     * Appends the final snapshot of a finished match to the results. Results are kept apart from matches in
     * progress: they never appear in {@link #findAll()} or count for {@link #isTeamPlaying(String)}.
     *
     * @param match the finished match
     */
    void appendFinished(Match match);

    /**
     * Finished match results.
     *
     * @return the results in the order they were appended, oldest first
     */
    List<Match> findFinished();

    /**
     * Removes the oldest results until at most {@code maxResults} remain.
     *
     * @param maxResults number of most recent results to keep, at least 1
     */
    void trimFinished(int maxResults);
}
