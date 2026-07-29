package com.bank.aml.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentExecutionContextTest {

    @Test
    @DisplayName("live(clock) derives FX date from the same instant as occurredAt")
    void liveAlignsFxDateWithOccurredAt() {
        Instant fixed = Instant.parse("2026-03-15T22:30:00Z");
        Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);

        PaymentExecutionContext ctx = PaymentExecutionContext.live(clock);

        assertThat(ctx.occurredAt()).isEqualTo(fixed);
        assertThat(ctx.fxRateDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    @DisplayName("virtualAt keeps demo Payment A timestamps off the wall clock")
    void virtualAtPinsSimulatedBusinessTime() {
        Instant simulated = Instant.parse("2026-07-27T10:05:00Z");

        PaymentExecutionContext ctx = PaymentExecutionContext.virtualAt(simulated);

        assertThat(ctx.occurredAt()).isEqualTo(simulated);
        assertThat(ctx.fxRateDate()).isEqualTo(LocalDate.of(2026, 7, 27));
    }

    @Test
    @DisplayName("FX date follows the UTC calendar day even near midnight")
    void fxDateUsesUtcCalendarDay() {
        Instant justAfterMidnightUtc = Instant.parse("2026-01-01T00:15:00Z");

        PaymentExecutionContext ctx = PaymentExecutionContext.virtualAt(justAfterMidnightUtc);

        assertThat(ctx.fxRateDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }
}
