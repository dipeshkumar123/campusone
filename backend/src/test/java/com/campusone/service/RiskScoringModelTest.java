package com.campusone.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoringModelTest {
    private final RiskScoringModel model = new RiskScoringModel();

    @Test
    void healthyStudentScoresZero() {
        var result = model.assess(95.0, 85.0, 2.0);

        assertThat(result.score()).isZero();
        assertThat(result.band()).isEqualTo("LOW");
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void attendanceMarksAndNegativeTrendProduceHighRisk() {
        var result = model.assess(60.0, 50.0, -10.0);

        assertThat(result.score()).isEqualTo(88);
        assertThat(result.band()).isEqualTo("HIGH");
        assertThat(result.reasons()).hasSize(3);
        assertThat(result.reasons().get(0)).contains("60.0%");
        assertThat(result.reasons().get(1)).contains("50.0%");
        assertThat(result.reasons().get(2)).contains("10.0 points");
    }

    @Test
    void scoreIsCappedAtOneHundred() {
        var result = model.assess(0.0, 0.0, -100.0);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.band()).isEqualTo("HIGH");
    }

    @Test
    void missingMetricsDoNotCreateAPhantomRisk() {
        var result = model.assess(null, null, 0.0);

        assertThat(result.score()).isZero();
        assertThat(result.band()).isEqualTo("LOW");
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void mixedWatchRangesProduceMediumBand() {
        var result = model.assess(60.0, 70.0, 0.0);

        assertThat(result.score()).isEqualTo(53);
        assertThat(result.band()).isEqualTo("MEDIUM");
        assertThat(result.reasons()).hasSize(1);
        assertThat(result.reasons().get(0)).contains("75% rule");
    }
}
