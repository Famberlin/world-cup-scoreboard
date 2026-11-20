package com.sportradar.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArgumentsTest {

    private static final String COMPOSED = "C\u00f4te d'Ivoire";
    private static final String DECOMPOSED = "Co\u0302te d'Ivoire";

    @Test
    void scoreUpToMaximumIsAccepted() {
        Arguments.requireScore(0, "score");
        Arguments.requireScore(Match.MAX_SCORE, "score");
    }

    @Test
    void scoreAboveMaximumIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Arguments.requireScore(Match.MAX_SCORE + 1, "score"))
                .withMessageContaining(String.valueOf(Match.MAX_SCORE));
        assertThatIllegalArgumentException().isThrownBy(() -> Arguments.requireScore(Integer.MAX_VALUE, "score"));
    }

    @Test
    void maximumScoresCannotOverflowTotal() {
        assertThat(Match.MAX_SCORE).isLessThanOrEqualTo(Integer.MAX_VALUE / 2);
    }

    @Test
    void teamNameIsNormalizedToNfc() {
        assertThat(Arguments.requireTeam(DECOMPOSED, "team")).isEqualTo(COMPOSED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Spa\u0000in", "Spa\u0007in", "Spa\u001bin", "Spa\tin", "Spa\u007fin", "Spa\u0085in"})
    void teamNameWithControlCharacterIsRejected(String team) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Arguments.requireTeam(team, "team"))
                .withMessageContaining("U+");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Spa\u200bin", // zero-width space
                "Spa\u200din", // zero-width joiner
                "Spa\u00adin", // soft hyphen
                "\u202eSpain", // right-to-left override
                "Spain\u2060", // word joiner
                "\ufeffSpain" // byte order mark
            })
    void teamNameWithInvisibleFormatCharacterIsRejected(String team) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Arguments.requireTeam(team, "team"))
                .withMessageContaining("U+");
    }

    @Test
    void teamNameUpToMaximumLengthIsAccepted() {
        String longest = "A".repeat(Match.MAX_TEAM_NAME_LENGTH);

        assertThat(Arguments.requireTeam(" " + longest + " ", "team")).isEqualTo(longest);
    }

    @Test
    void teamNameAboveMaximumLengthIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Arguments.requireTeam("A".repeat(Match.MAX_TEAM_NAME_LENGTH + 1), "team"))
                .withMessageContaining(String.valueOf(Match.MAX_TEAM_NAME_LENGTH));
    }

    @Test
    void teamNameLengthIsCountedAfterNormalization() {
        String decomposed = "o\u0302".repeat(Match.MAX_TEAM_NAME_LENGTH);

        assertThat(Arguments.requireTeam(decomposed, "team")).hasSize(Match.MAX_TEAM_NAME_LENGTH);
    }

    @Test
    void teamNameLengthCountsCodePoints() {
        String emoji = "\ud83c\udff4"; // one code point, two chars
        String longest = emoji.repeat(Match.MAX_TEAM_NAME_LENGTH);

        assertThat(Arguments.requireTeam(longest, "team")).isEqualTo(longest);
        assertThatIllegalArgumentException().isThrownBy(() -> Arguments.requireTeam(longest + emoji, "team"));
    }
}
