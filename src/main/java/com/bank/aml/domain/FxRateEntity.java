package com.bank.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "fx_rate")
@Getter
@Setter
public class FxRateEntity {

    @EmbeddedId
    private FxRateId id;

    @Column(name = "rate_to_gbp", nullable = false, precision = 18, scale = 8)
    private BigDecimal rateToGbp;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Embeddable
    @Getter
    @Setter
    public static class FxRateId implements Serializable {

        @Column(nullable = false, length = 3)
        private String currency;

        @Column(name = "rate_date", nullable = false)
        private LocalDate rateDate;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FxRateId that)) {
                return false;
            }
            return Objects.equals(currency, that.currency) && Objects.equals(rateDate, that.rateDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(currency, rateDate);
        }
    }
}
