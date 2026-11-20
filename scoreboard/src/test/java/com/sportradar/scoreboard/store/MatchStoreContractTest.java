package com.sportradar.scoreboard.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportradar.scoreboard.Match;
import com.sportradar.scoreboard.MatchId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Rules every {@link MatchStore} must follow. A custom store gets the same checks by extending this class and
 * returning a new, empty store from {@link #createStore()}.
 */
abstract class MatchStoreContractTest {

    private MatchStore store;

    protected abstract MatchStore createStore();

    @BeforeEach
    void setUp() {
        store = createStore();
    }

    @Test
    void sequenceIsStrictlyIncreasing() {
        long first = store.nextSequence();
        long second = store.nextSequence();
        long third = store.nextSequence();

        assertThat(second).isGreaterThan(first);
        assertThat(third).isGreaterThan(second);
    }

    @Test
    void savedMatchCanBeFoundById() {
        Match match = match("Spain", "Brazil");

        store.save(match);

        assertThat(store.findById(match.id())).contains(match);
    }

    @Test
    void unknownIdIsNotFound() {
        assertThat(store.findById(new MatchId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void savingSameIdReplacesStoredMatch() {
        Match match = match("Spain", "Brazil");
        store.save(match);

        store.save(match.withScore(1, 0));

        assertThat(store.findById(match.id())).contains(match.withScore(1, 0));
        assertThat(store.findAll()).hasSize(1);
    }

    @Test
    void deleteRemovesMatchAndReportsWhetherItExisted() {
        Match match = match("Spain", "Brazil");
        store.save(match);

        assertThat(store.delete(match.id())).isTrue();
        assertThat(store.delete(match.id())).isFalse();
        assertThat(store.findById(match.id())).isEmpty();
    }

    @Test
    void findAllReturnsEverySavedMatch() {
        Match first = match("Spain", "Brazil");
        Match second = match("Germany", "France");
        store.save(first);
        store.save(second);

        assertThat(store.findAll()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void findAllIsEmptyForNewStore() {
        assertThat(store.findAll()).isEmpty();
    }

    @Test
    void teamIsPlayingOnEitherSideIgnoringCase() {
        store.save(match("Spain", "Brazil"));

        assertThat(store.isTeamPlaying("spain")).isTrue();
        assertThat(store.isTeamPlaying("BRAZIL")).isTrue();
        assertThat(store.isTeamPlaying("Italy")).isFalse();
    }

    @Test
    void teamIsNoLongerPlayingAfterDelete() {
        Match match = match("Spain", "Brazil");
        store.save(match);

        store.delete(match.id());

        assertThat(store.isTeamPlaying("Spain")).isFalse();
    }

    @Test
    void finishedMatchesKeepAppendOrder() {
        Match first = match("Spain", "Brazil");
        Match second = match("Germany", "France");

        store.appendFinished(first);
        store.appendFinished(second);

        assertThat(store.findFinished()).containsExactly(first, second);
    }

    @Test
    void finishedMatchesAreSeparateFromMatchesInProgress() {
        Match match = match("Spain", "Brazil");

        store.appendFinished(match);

        assertThat(store.findAll()).isEmpty();
        assertThat(store.isTeamPlaying("Spain")).isFalse();
    }

    @Test
    void trimRemovesOldestFinishedMatches() {
        Match first = match("Spain", "Brazil");
        Match second = match("Germany", "France");
        Match third = match("Uruguay", "Italy");
        store.appendFinished(first);
        store.appendFinished(second);
        store.appendFinished(third);

        store.trimFinished(2);

        assertThat(store.findFinished()).containsExactly(second, third);
    }

    @Test
    void trimKeepsEverythingWithinLimit() {
        Match match = match("Spain", "Brazil");
        store.appendFinished(match);

        store.trimFinished(5);

        assertThat(store.findFinished()).containsExactly(match);
    }

    private Match match(String home, String away) {
        return new Match(new MatchId(UUID.randomUUID()), home, away, 0, 0, store.nextSequence());
    }
}
