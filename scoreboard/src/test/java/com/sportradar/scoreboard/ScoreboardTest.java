package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportradar.scoreboard.store.InMemoryMatchStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoreboardTest {

    private final Scoreboard scoreboard = new Scoreboard();

    @Test
    void startedMatchHasZeroScoreAndAppearsInSummary() {
        Match match = scoreboard.startMatch("Mexico", "Canada");

        assertThat(match.homeTeam()).isEqualTo("Mexico");
        assertThat(match.awayTeam()).isEqualTo("Canada");
        assertThat(match.totalScore()).isZero();
        assertThat(scoreboard.getSummary()).containsExactly(match);
    }

    @Test
    void startStripsTeamNames() {
        Match match = scoreboard.startMatch(" Mexico ", "Canada\n");

        assertThat(match).hasToString("Mexico 0 - Canada 0");
    }

    @Test
    void startRejectsNullOrBlankTeams() {
        assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.startMatch(null, "Canada"));
        assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.startMatch("Mexico", null));
        assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.startMatch("  ", "Canada"));
        assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.startMatch("Mexico", ""));
        assertThat(scoreboard.getSummary()).isEmpty();
    }

    @Test
    void startRejectsSameTeamOnBothSidesIgnoringCase() {
        assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.startMatch("Spain", "spain "));
    }

    @Test
    void startRejectsTeamAlreadyPlayingOnEitherSide() {
        scoreboard.startMatch("Spain", "Brazil");

        assertThatThrownBy(() -> scoreboard.startMatch("Spain", "Brazil"))
                .isInstanceOf(TeamAlreadyPlayingException.class)
                .hasMessageContaining("Spain");
        assertThatThrownBy(() -> scoreboard.startMatch("Italy", "SPAIN"))
                .isInstanceOf(TeamAlreadyPlayingException.class);
        assertThatThrownBy(() -> scoreboard.startMatch("brazil", "Italy"))
                .isInstanceOf(TeamAlreadyPlayingException.class);
        assertThat(scoreboard.getSummary()).hasSize(1);
    }

    @Test
    void teamCanStartAgainAfterItsMatchFinished() {
        Match first = scoreboard.startMatch("Spain", "Brazil");
        scoreboard.finishMatch(first.id());

        Match second = scoreboard.startMatch("Spain", "Italy");

        assertThat(scoreboard.getSummary()).containsExactly(second);
    }

    @Test
    void updateReplacesScoreWithAbsoluteValues() {
        Match match = scoreboard.startMatch("Mexico", "Canada");

        scoreboard.updateScore(match.id(), 0, 2);
        Match updated = scoreboard.updateScore(match.id(), 0, 5);

        assertThat(updated).hasToString("Mexico 0 - Canada 5");
        assertThat(updated.id()).isEqualTo(match.id());
        assertThat(scoreboard.getSummary()).containsExactly(updated);
    }

    @Test
    void updateAllowsLoweringScoreForCorrections() {
        Match match = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(match.id(), 1, 1);

        Match corrected = scoreboard.updateScore(match.id(), 1, 0);

        assertThat(corrected.totalScore()).isEqualTo(1);
    }

    @Test
    void updateRejectsNegativeScores() {
        Match match = scoreboard.startMatch("Mexico", "Canada");

        assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.updateScore(match.id(), -1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.updateScore(match.id(), 0, -1));
        assertThat(scoreboard.getSummary()).containsExactly(match);
    }

    @Test
    void updateRejectsNullId() {
        assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.updateScore(null, 1, 0));
    }

    @Test
    void updateOfUnknownOrFinishedMatchFails() {
        Match match = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.finishMatch(match.id());

        assertThatThrownBy(() -> scoreboard.updateScore(match.id(), 1, 0))
                .isInstanceOf(MatchNotFoundException.class)
                .hasMessageContaining(match.id().value().toString());
        assertThatThrownBy(() -> scoreboard.updateScore(new MatchId(UUID.randomUUID()), 1, 0))
                .isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void finishedMatchIsRemovedFromSummaryAndReturnedWithFinalScore() {
        Match match = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(match.id(), 0, 5);

        Match finished = scoreboard.finishMatch(match.id());

        assertThat(finished).hasToString("Mexico 0 - Canada 5");
        assertThat(scoreboard.getSummary()).isEmpty();
    }

    @Test
    void finishingTwiceFails() {
        Match match = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.finishMatch(match.id());

        assertThatThrownBy(() -> scoreboard.finishMatch(match.id())).isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void finishRejectsNullId() {
        assertThatIllegalArgumentException().isThrownBy(() -> scoreboard.finishMatch(null));
    }

    @Test
    void summaryMatchesAssignmentExample() {
        start("Mexico", "Canada", 0, 5);
        start("Spain", "Brazil", 10, 2);
        start("Germany", "France", 2, 2);
        start("Uruguay", "Italy", 6, 6);
        start("Argentina", "Australia", 3, 1);

        assertThat(scoreboard.getSummary())
                .map(Match::toString)
                .containsExactly(
                        "Uruguay 6 - Italy 6",
                        "Spain 10 - Brazil 2",
                        "Mexico 0 - Canada 5",
                        "Argentina 3 - Australia 1",
                        "Germany 2 - France 2");
    }

    @Test
    void summaryBreaksTiesByMostRecentlyStartedEvenWithoutGoals() {
        Match first = scoreboard.startMatch("Mexico", "Canada");
        Match second = scoreboard.startMatch("Spain", "Brazil");
        Match third = scoreboard.startMatch("Germany", "France");

        assertThat(scoreboard.getSummary()).containsExactly(third, second, first);
    }

    @Test
    void summaryOrderUsesStartOrderNotUpdateOrder() {
        Match older = start("Mexico", "Canada", 1, 0);
        Match newer = start("Spain", "Brazil", 1, 0);

        Match olderUpdatedLast = scoreboard.updateScore(older.id(), 0, 1);

        assertThat(scoreboard.getSummary()).containsExactly(newer, olderUpdatedLast);
    }

    @Test
    void summaryIsUnmodifiableSnapshot() {
        Match match = scoreboard.startMatch("Mexico", "Canada");
        List<Match> summary = scoreboard.getSummary();

        scoreboard.finishMatch(match.id());

        assertThat(summary).containsExactly(match);
        assertThatThrownBy(summary::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void usesTheGivenStore() {
        InMemoryMatchStore store = new InMemoryMatchStore();
        Scoreboard custom = new Scoreboard(store);

        Match match = custom.startMatch("Mexico", "Canada");

        assertThat(store.findById(match.id())).contains(match);
    }

    @Test
    void rejectsNullStore() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Scoreboard(null));
    }

    @Test
    void finishedMatchesAreKeptWithFinalScoreInFinishOrder() {
        Match mexico = start("Mexico", "Canada", 0, 5);
        Match spain = start("Spain", "Brazil", 10, 2);
        start("Germany", "France", 2, 2);

        scoreboard.finishMatch(spain.id());
        scoreboard.finishMatch(mexico.id());

        assertThat(scoreboard.getFinishedMatches())
                .map(Match::toString)
                .containsExactly("Spain 10 - Brazil 2", "Mexico 0 - Canada 5");
        assertThat(scoreboard.getSummary()).map(Match::toString).containsExactly("Germany 2 - France 2");
    }

    @Test
    void noFinishedMatchesInitially() {
        scoreboard.startMatch("Mexico", "Canada");

        assertThat(scoreboard.getFinishedMatches()).isEmpty();
    }

    @Test
    void oldestFinishedMatchesAreCleanedUpBeyondLimit() {
        Scoreboard limited = new Scoreboard(new InMemoryMatchStore(), 2);

        for (String team : List.of("Mexico", "Spain", "Germany")) {
            limited.finishMatch(limited.startMatch(team, "Opponent").id());
        }

        assertThat(limited.getFinishedMatches()).map(Match::homeTeam).containsExactly("Spain", "Germany");
    }

    @Test
    void defaultResultsLimitHoldsAWholeTournament() {
        for (int i = 0; i < Scoreboard.DEFAULT_RESULTS_LIMIT + 1; i++) {
            scoreboard.finishMatch(scoreboard.startMatch("Home", "Away").id());
        }

        assertThat(scoreboard.getFinishedMatches()).hasSize(104);
    }

    @Test
    void rejectsResultsLimitBelowOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Scoreboard(new InMemoryMatchStore(), 0));
    }

    @Test
    void finishedMatchesAreUnmodifiableSnapshot() {
        scoreboard.finishMatch(scoreboard.startMatch("Mexico", "Canada").id());
        List<Match> finished = scoreboard.getFinishedMatches();

        scoreboard.finishMatch(scoreboard.startMatch("Spain", "Brazil").id());

        assertThat(finished).hasSize(1);
        assertThatThrownBy(finished::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    private Match start(String home, String away, int homeScore, int awayScore) {
        Match match = scoreboard.startMatch(home, away);
        return scoreboard.updateScore(match.id(), homeScore, awayScore);
    }
}
