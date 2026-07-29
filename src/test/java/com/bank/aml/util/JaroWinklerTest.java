package com.bank.aml.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JaroWinklerTest {

    @Test
    @DisplayName("identical names score 1.0 regardless of case or surrounding whitespace")
    void identicalNamesAreExact() {
        assertThat(JaroWinkler.similarity("Vladimir Petrov", "  vladimir petrov ")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("near-miss spellings stay above the 0.85 sanctions threshold")
    void nearMissStaysAboveThreshold() {
        // The demo hold uses "Vladmir" (missing 'i') against listed "Vladimir".
        assertThat(JaroWinkler.similarity("Vladmir Petrov", "Vladimir Petrov"))
                .isGreaterThanOrEqualTo(0.85);
    }

    @Test
    @DisplayName("unrelated names stay well below the match threshold")
    void unrelatedNamesDoNotMatch() {
        assertThat(JaroWinkler.similarity("Acme Trading Ltd", "Vladimir Petrov"))
                .isLessThan(0.85);
    }

    @Test
    @DisplayName("null or blank inputs score 0 so screening never false-matches on missing data")
    void nullAndBlankScoreZero() {
        assertThat(JaroWinkler.similarity(null, "Vladimir")).isZero();
        assertThat(JaroWinkler.similarity("Vladimir", null)).isZero();
        assertThat(JaroWinkler.similarity("", "Vladimir")).isZero();
        assertThat(JaroWinkler.similarity("Vladimir", "   ")).isZero();
    }

    @Test
    @DisplayName("shared prefixes raise the Jaro-Winkler boost above plain Jaro")
    void prefixBoostApplies() {
        double withPrefix = JaroWinkler.similarity("Martha", "Marhta");
        assertThat(withPrefix).isCloseTo(0.961, within(0.01));
    }
}
