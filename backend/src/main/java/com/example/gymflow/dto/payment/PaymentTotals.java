package com.example.gymflow.dto.payment;

import com.example.gymflow.enums.PaymentMethod;
import com.example.gymflow.repository.projection.MethodTotal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.EnumMap;

/**
 * Non-voided payment totals per method, their sum and the number of payments.
 */
public record PaymentTotals(
        BigDecimal cash,
        BigDecimal qr,
        BigDecimal total,
        long count
) {
    /**
     * Folds per-method totals; methods without payments are {@code 0.00}.
     *
     * @param totals the per-method sums
     * @return the totals with scale 2
     */
    public static PaymentTotals of(Collection<MethodTotal> totals) {
        var byMethod = new EnumMap<PaymentMethod, BigDecimal>(PaymentMethod.class);
        long count = 0;
        for (var t : totals) {
            byMethod.merge(t.method(), t.amount(), BigDecimal::add);
            count += t.count();
        }
        var total = byMethod.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PaymentTotals(
                money(byMethod.get(PaymentMethod.CASH)),
                money(byMethod.get(PaymentMethod.QR)),
                money(total),
                count);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
