package com.leaseledger.lease;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class LeaseDtos {

    private LeaseDtos() {
    }

    public record CreateLeaseRequest(
            @NotNull Long unitId,
            @NotBlank @Email String tenantEmail,
            @NotNull LocalDate startDate,
            /** Optional. Defaults to 12. */
            @Min(1) @Max(60) Integer termMonths,
            /** Number of rent payments (in the UAE, traditionally the number of cheques). */
            @NotNull @Min(1) @Max(60) Integer installments,
            /** Optional. Defaults to the unit's monthly rent multiplied by the term. */
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal totalRent,
            /** Optional. Defaults to 0 (no deposit invoice). */
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal securityDeposit) {
    }

    public record LeaseResponse(
            Long id,
            LeaseStatus status,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate terminatedOn,
            BigDecimal totalRent,
            int installments,
            BigDecimal securityDeposit,
            Long unitId,
            String unitNumber,
            String propertyName,
            Long tenantId,
            String tenantName,
            String tenantEmail) {

        static LeaseResponse from(Lease l) {
            return new LeaseResponse(
                    l.getId(), l.getStatus(), l.getStartDate(), l.getEndDate(), l.getTerminatedOn(),
                    l.getTotalRent(), l.getInstallments(), l.getSecurityDeposit(),
                    l.getUnit().getId(), l.getUnit().getUnitNumber(), l.getUnit().getProperty().getName(),
                    l.getTenant().getId(), l.getTenant().getFullName(), l.getTenant().getEmail());
        }
    }

    public record LeaseDetailResponse(LeaseResponse lease, List<InvoiceResponse> invoices) {
    }

    public record InvoiceResponse(
            Long id,
            Long leaseId,
            InvoiceType type,
            int sequenceNo,
            LocalDate dueDate,
            BigDecimal amount,
            InvoiceStatus status,
            /** Computed for today's date; not stored. */
            boolean overdue,
            Instant paidAt,
            String paymentReference) {

        static InvoiceResponse from(Invoice i, LocalDate today) {
            return new InvoiceResponse(
                    // getLease().getId() reads the foreign key already on the invoice row;
                    // it does not load the lease.
                    i.getId(), i.getLease().getId(), i.getType(), i.getSequenceNo(), i.getDueDate(),
                    i.getAmount(), i.getStatus(), i.isOverdue(today), i.getPaidAt(), i.getPaymentReference());
        }
    }

    public record MarkPaidRequest(
            /** Optional note on how it was paid, for example a cheque number. */
            @Size(max = 100) String reference) {
    }
}
