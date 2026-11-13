package com.leaseledger.lease;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a lease's total rent into dated installments.
 *
 * <p>Pure calculation: no Spring, no database. That makes the trickiest rules in the
 * project (rounding and month-end dates) testable in milliseconds.
 */
public final class RentSchedule {

    private RentSchedule() {
    }

    public record Installment(int sequenceNo, LocalDate dueDate, BigDecimal amount) {
    }

    /**
     * @param termMonths   length of the lease in months
     * @param installments number of payments; must divide termMonths evenly
     * @param totalRent    rent for the whole term, with at most 2 decimal places
     */
    public static List<Installment> generate(LocalDate startDate, int termMonths, int installments,
                                             BigDecimal totalRent) {
        if (installments < 1 || termMonths % installments != 0) {
            throw new IllegalArgumentException("installments must divide the lease term evenly");
        }
        int monthsBetweenPayments = termMonths / installments;

        // 100000 / 6 is 16666.666..., which cannot be invoiced. Round each share DOWN to
        // whole fils, then add what is left over to the last installment, so the invoices
        // always add up to exactly the total. Rounding each share to nearest would lose
        // or invent money (6 x 16666.67 = 100000.02).
        BigDecimal share = totalRent.divide(BigDecimal.valueOf(installments), 2, RoundingMode.DOWN);
        BigDecimal remainder = totalRent.subtract(share.multiply(BigDecimal.valueOf(installments)));

        List<Installment> schedule = new ArrayList<>(installments);
        for (int i = 0; i < installments; i++) {
            // Always offset from the original start date. Chaining plusMonths from the
            // previous due date would drift: 31 Jan -> 28 Feb -> 28 Mar, instead of 31 Mar.
            LocalDate dueDate = startDate.plusMonths((long) i * monthsBetweenPayments);
            BigDecimal amount = (i == installments - 1) ? share.add(remainder) : share;
            schedule.add(new Installment(i + 1, dueDate, amount));
        }
        return schedule;
    }
}
