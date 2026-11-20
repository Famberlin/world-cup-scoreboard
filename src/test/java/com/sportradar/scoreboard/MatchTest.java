package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchTest {

    private static final MatchId ID = new MatchId(UUID.randomUUID());

    @Test
    void totalScoreIsSumOfBothSides() {
        assertThat(new Match(ID, "Spain", "Brazil", 10, 2, 1).totalScore()).isEqualTo(12);
    }

    @Test
    void withScoreReturnsNewSnapshotKeepingIdentityAndOrder() {
        Match original = new Match(ID, "Spain", "Brazil", 0, 0, 7);

        Match updated = original.withScore(3, 1);

        assertThat(updated).isEqualTo(new Match(ID, "Spain", "Brazil", 3, 1, 7));
        assertThat(original.homeScore()).isZero();
    }

    @Test
    void teamNamesAreStripped() {
        Match match = new Match(ID, "  Spain ", "\tBrazil", 0, 0, 1);

        assertThat(match.homeTeam()).isEqualTo("Spain");
        assertThat(match.awayTeam()).isEqualTo("Brazil");
    }

    @Test
    void toStringShowsTeamsAndScore() {
        assertThat(new Match(ID, "Uruguay", "Italy", 6, 6, 1)).hasToString("Uruguay 6 - Italy 6");
    }

    @Test
    void rejectsNullIdOrTeams() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Match(null, "Spain", "Brazil", 0, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Match(ID, null, "Brazil", 0, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Match(ID, "Spain", null, 0, 0, 1));
    }

    @Test
    void rejectsBlankTeamNames() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Match(ID, " ", "Brazil", 0, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Match(ID, "Spain", "", 0, 0, 1));
    }

    @Test
    void rejectsNegativeScores() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Match(ID, "Spain", "Brazil", -1, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Match(ID, "Spain", "Brazil", 0, -1, 1));
    }

    @Test
    void matchIdRequiresValueAndRandomIdsDiffer() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MatchId(null));
        assertThat(MatchId.random()).isNotEqualTo(MatchId.random());
    }
}
