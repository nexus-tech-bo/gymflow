package com.example.gymflow.service.support;

import com.example.gymflow.entity.Membership;
import com.example.gymflow.enums.MembershipStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Pure membership date rules. A membership covers {@code [startDate, endDate]} inclusive; its status is
 * derived from those dates, {@code frozenSince} and {@code cancelledAt}, never stored.
 */
public final class MembershipRules {

    private MembershipRules() {}

    /**
     * Effective status on {@code today}: cancelled wins, then frozen, then the date range decides.
     *
     * @param membership the membership
     * @param today      the reference day
     * @return the effective status
     */
    public static MembershipStatus effectiveStatus(Membership membership, LocalDate today) {
        if (membership.getCancelledAt() != null) return MembershipStatus.CANCELLED;
        if (membership.getFrozenSince() != null) return MembershipStatus.FROZEN;
        if (today.isBefore(membership.getStartDate())) return MembershipStatus.SCHEDULED;
        if (today.isAfter(membership.getEndDate())) return MembershipStatus.EXPIRED;
        return MembershipStatus.ACTIVE;
    }

    /**
     * Days from {@code today} until {@code endDate}. A frozen membership keeps the days it had when frozen;
     * expired and cancelled ones have 0.
     *
     * @param membership the membership
     * @param today      the reference day
     * @return the remaining days, never negative
     */
    public static int daysLeft(Membership membership, LocalDate today) {
        long days = switch (effectiveStatus(membership, today)) {
            case ACTIVE, SCHEDULED -> ChronoUnit.DAYS.between(today, membership.getEndDate());
            case FROZEN -> ChronoUnit.DAYS.between(membership.getFrozenSince(), membership.getEndDate());
            case EXPIRED, CANCELLED -> 0;
        };
        return (int) Math.max(0, days);
    }

    /**
     * Picks the membership that represents the member today, ignoring cancelled ones: the active or frozen
     * one, else the next scheduled one, else the last expired one.
     *
     * @param memberships all memberships of one member
     * @param today       the reference day
     * @return the current membership, or empty when there is none
     */
    public static Optional<Membership> pickCurrent(Collection<Membership> memberships, LocalDate today) {
        var live = memberships.stream().filter(m -> m.getCancelledAt() == null).toList();
        Comparator<Membership> byStart = Comparator.comparing(Membership::getStartDate);
        return live.stream()
                .filter(m -> switch (effectiveStatus(m, today)) {
                    case ACTIVE, FROZEN -> true;
                    default -> false;
                })
                .min(byStart)
                .or(() -> live.stream().filter(m -> effectiveStatus(m, today) == MembershipStatus.SCHEDULED).min(byStart))
                .or(() -> live.stream().max(Comparator.comparing(Membership::getEndDate)));
    }

    /**
     * Start day of a new sale: renewals queue right after the latest non-cancelled membership; otherwise today.
     *
     * @param memberships all memberships of one member
     * @param today       the reference day
     * @return the start date for the new membership
     */
    public static LocalDate nextStartDate(Collection<Membership> memberships, LocalDate today) {
        return memberships.stream()
                .filter(m -> m.getCancelledAt() == null)
                .map(Membership::getEndDate)
                .max(Comparator.naturalOrder())
                .filter(end -> !end.isBefore(today))
                .map(end -> end.plusDays(1))
                .orElse(today);
    }

    /**
     * Last covered day of a membership of {@code durationDays} starting on {@code startDate}.
     *
     * @param startDate    the first covered day
     * @param durationDays the plan duration
     * @return the inclusive end date
     */
    public static LocalDate endDate(LocalDate startDate, int durationDays) {
        return startDate.plusDays(durationDays - 1L);
    }
}
