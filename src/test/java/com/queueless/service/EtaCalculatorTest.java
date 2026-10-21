package com.queueless.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class EtaCalculatorTest {

    @Test
    void averageMovesTowardObservedDuration() {
        // 0.3 * 600 + 0.7 * 300 = 390
        assertThat(EtaCalculator.updateAverage(300, 600)).isCloseTo(390.0, within(0.001));
    }

    @Test
    void averageIsClampedAgainstAbsurdObservations() {
        // A customer left the counter open for 10 hours: observation is capped at 3600s.
        assertThat(EtaCalculator.updateAverage(300, 36_000)).isCloseTo(0.3 * 3600 + 0.7 * 300, within(0.001));
        // An accidental double click gives ~0s: the 30s floor applies.
        assertThat(EtaCalculator.updateAverage(300, 0)).isCloseTo(0.3 * 30 + 0.7 * 300, within(0.001));
    }

    @Test
    void etaCountsPeopleAheadPlusRemainderOfCurrentCustomer() {
        // 2 ahead at 300s each, current customer has used 100s -> 200s left.
        assertThat(EtaCalculator.etaSeconds(2, 300, 100L)).isEqualTo(800);
    }

    @Test
    void etaDoesNotGoNegativeWhenCurrentCustomerRunsLong() {
        assertThat(EtaCalculator.etaSeconds(1, 300, 900L)).isEqualTo(300);
    }

    @Test
    void etaWithNobodyBeingServed() {
        assertThat(EtaCalculator.etaSeconds(3, 120, null)).isEqualTo(360);
    }
}
