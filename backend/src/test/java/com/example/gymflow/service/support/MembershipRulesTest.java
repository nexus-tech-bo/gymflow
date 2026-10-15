package com.example.gymflow.service.support;

import com.example.gymflow.entity.Membership;
import com.example.gymflow.enums.MembershipStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MembershipRules")
class MembershipRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    private static Membership membership(String start, String end) {
        return Membership.builder()
                .startDate(LocalDate.parse(start))
                .endDate(LocalDate.parse(end))
                .planName("Mensual")
                .build();
    }

    @Nested
    @DisplayName("effectiveStatus")
    class EffectiveStatus {
        @ParameterizedTest(name = "{0}..{1} -> {2}")
        @CsvSource({
                "2026-03-11, 2026-04-09, SCHEDULED",
                "2026-03-10, 2026-04-08, ACTIVE",
                "2026-02-09, 2026-03-10, ACTIVE",
                "2026-02-08, 2026-03-09, EXPIRED"
        })
        void effectiveStatus_should_followDateRange_when_notFrozenNorCancelled(String start, String end, MembershipStatus expected) {
            assertThat(MembershipRules.effectiveStatus(membership(start, end), TODAY)).isEqualTo(expected);
        }

        @Test
        void effectiveStatus_should_beFrozen_when_frozenSinceIsSet_evenIfDatesPassed() {
            var m = membership("2026-01-01", "2026-01-30");
            m.setFrozenSince(LocalDate.of(2026, 1, 20));
            assertThat(MembershipRules.effectiveStatus(m, TODAY)).isEqualTo(MembershipStatus.FROZEN);
        }

        @Test
        void effectiveStatus_should_beCancelled_when_cancelledAtIsSet_evenIfFrozen() {
            var m = membership("2026-03-01", "2026-03-30");
            m.setFrozenSince(TODAY);
            m.setCancelledAt(Instant.parse("2026-03-10T15:00:00Z"));
            assertThat(MembershipRules.effectiveStatus(m, TODAY)).isEqualTo(MembershipStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("daysLeft")
    class DaysLeft {
        @Test
        void daysLeft_should_countDaysUntilEndDate_when_active() {
            assertThat(MembershipRules.daysLeft(membership("2026-03-01", "2026-03-30"), TODAY)).isEqualTo(20);
        }

        @Test
        void daysLeft_should_beZero_when_lastDay() {
            assertThat(MembershipRules.daysLeft(membership("2026-02-09", "2026-03-10"), TODAY)).isZero();
        }

        @Test
        void daysLeft_should_beZero_when_expired() {
            assertThat(MembershipRules.daysLeft(membership("2026-02-01", "2026-03-02"), TODAY)).isZero();
        }

        @Test
        void daysLeft_should_keepDaysAtFreezeTime_when_frozen() {
            var m = membership("2026-03-01", "2026-03-30");
            m.setFrozenSince(LocalDate.of(2026, 3, 5));
            assertThat(MembershipRules.daysLeft(m, TODAY)).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("pickCurrent")
    class PickCurrent {
        @Test
        void pickCurrent_should_preferActive_over_scheduledAndExpired() {
            var expired = membership("2026-01-01", "2026-01-30");
            var active = membership("2026-03-01", "2026-03-30");
            var scheduled = membership("2026-03-31", "2026-04-29");
            assertThat(MembershipRules.pickCurrent(List.of(expired, scheduled, active), TODAY)).containsSame(active);
        }

        @Test
        void pickCurrent_should_returnEarliestScheduled_when_noneActive() {
            var later = membership("2026-05-01", "2026-05-30");
            var next = membership("2026-04-01", "2026-04-30");
            assertThat(MembershipRules.pickCurrent(List.of(later, next), TODAY)).containsSame(next);
        }

        @Test
        void pickCurrent_should_returnLatestExpired_when_onlyExpired() {
            var old = membership("2025-12-01", "2025-12-30");
            var recent = membership("2026-01-01", "2026-01-30");
            assertThat(MembershipRules.pickCurrent(List.of(old, recent), TODAY)).containsSame(recent);
        }

        @Test
        void pickCurrent_should_ignoreCancelled() {
            var cancelled = membership("2026-03-01", "2026-03-30");
            cancelled.setCancelledAt(Instant.parse("2026-03-02T15:00:00Z"));
            assertThat(MembershipRules.pickCurrent(List.of(cancelled), TODAY)).isEmpty();
        }
    }

    @Nested
    @DisplayName("nextStartDate / endDate")
    class Dates {
        @Test
        void nextStartDate_should_beToday_when_noLiveMembership() {
            assertThat(MembershipRules.nextStartDate(List.of(membership("2026-01-01", "2026-01-30")), TODAY)).isEqualTo(TODAY);
        }

        @Test
        void nextStartDate_should_queueAfterLatestEnd_when_renewing() {
            var current = membership("2026-03-01", "2026-03-30");
            var queued = membership("2026-03-31", "2026-04-29");
            assertThat(MembershipRules.nextStartDate(List.of(current, queued), TODAY)).isEqualTo(LocalDate.of(2026, 4, 30));
        }

        @Test
        void nextStartDate_should_ignoreCancelled() {
            var cancelled = membership("2026-03-01", "2026-03-30");
            cancelled.setCancelledAt(Instant.parse("2026-03-02T15:00:00Z"));
            assertThat(MembershipRules.nextStartDate(List.of(cancelled), TODAY)).isEqualTo(TODAY);
        }

        @Test
        void endDate_should_includeStartDay() {
            assertThat(MembershipRules.endDate(LocalDate.of(2026, 3, 1), 30)).isEqualTo(LocalDate.of(2026, 3, 30));
        }
    }
}
