package com.leaseledger.lease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leaseledger.lease.RentSchedule.Installment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RentScheduleTest {

    private static final LocalDate JAN_1 = LocalDate.of(2027, 1, 1);

    private static BigDecimal sum(List<Installment> schedule) {
        return schedule.stream().map(Installment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void fourQuarterlyInstallmentsOverTwelveMonths() {
        List<Installment> schedule = RentSchedule.generate(JAN_1, 12, 4, new BigDecimal("102000.00"));

        assertThat(schedule).extracting(Installment::dueDate).containsExactly(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 4, 1),
                LocalDate.of(2027, 7, 1), LocalDate.of(2027, 10, 1));
        assertThat(schedule).extracting(Installment::amount)
                .containsOnly(new BigDecimal("25500.00"));
        assertThat(schedule).extracting(Installment::sequenceNo).containsExactly(1, 2, 3, 4);
    }

    @Test
    void amountThatDoesNotDivideEvenlyPutsTheLeftoverOnTheLastInstallment() {
        List<Installment> schedule = RentSchedule.generate(JAN_1, 12, 6, new BigDecimal("100000.00"));

        assertThat(schedule.subList(0, 5)).extracting(Installment::amount)
                .containsOnly(new BigDecimal("16666.66"));
        assertThat(schedule.get(5).amount()).isEqualByComparingTo("16666.70");
    }

    /** The property that matters most: invoices never add up to more or less than the agreed rent. */
    @ParameterizedTest
    @CsvSource({
            "100000.00, 12, 3", "100000.00, 12, 6", "100000.00, 12, 12",
            "99999.99, 12, 12", "0.07, 12, 4", "85000.50, 24, 8", "1.00, 6, 6",
    })
    void installmentsAlwaysSumToExactlyTheTotal(String total, int termMonths, int installments) {
        List<Installment> schedule = RentSchedule.generate(JAN_1, termMonths, installments, new BigDecimal(total));

        assertThat(schedule).hasSize(installments);
        assertThat(sum(schedule)).isEqualByComparingTo(total);
        assertThat(schedule).allSatisfy(i -> assertThat(i.amount().scale()).isLessThanOrEqualTo(2));
    }

    @Test
    void dueDatesFromTheThirtyFirstDoNotDriftToTheTwentyEighth() {
        List<Installment> schedule = RentSchedule.generate(
                LocalDate.of(2027, 1, 31), 12, 12, new BigDecimal("12000.00"));

        assertThat(schedule).extracting(Installment::dueDate).startsWith(
                LocalDate.of(2027, 1, 31),
                LocalDate.of(2027, 2, 28), // February has no 31st
                LocalDate.of(2027, 3, 31), // but March goes back to the 31st
                LocalDate.of(2027, 4, 30));
    }

    @Test
    void singleInstallmentIsTheWholeRentOnTheStartDate() {
        List<Installment> schedule = RentSchedule.generate(JAN_1, 12, 1, new BigDecimal("60000.00"));

        assertThat(schedule).containsExactly(new Installment(1, JAN_1, new BigDecimal("60000.00")));
    }

    @Test
    void installmentsMustDivideTheTerm() {
        assertThatThrownBy(() -> RentSchedule.generate(JAN_1, 12, 5, new BigDecimal("60000.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
