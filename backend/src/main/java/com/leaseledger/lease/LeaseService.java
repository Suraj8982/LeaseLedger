package com.leaseledger.lease;

import com.leaseledger.common.ConflictException;
import com.leaseledger.common.NotFoundException;
import com.leaseledger.common.PageResponse;
import com.leaseledger.common.Paging;
import com.leaseledger.lease.LeaseDtos.CreateLeaseRequest;
import com.leaseledger.lease.LeaseDtos.InvoiceResponse;
import com.leaseledger.lease.LeaseDtos.LeaseDetailResponse;
import com.leaseledger.lease.LeaseDtos.LeaseResponse;
import com.leaseledger.property.Unit;
import com.leaseledger.property.UnitService;
import com.leaseledger.security.AuthenticatedUser;
import com.leaseledger.user.Role;
import com.leaseledger.user.User;
import com.leaseledger.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LeaseService {

    private static final int DEFAULT_TERM_MONTHS = 12;

    private final LeaseRepository leases;
    private final InvoiceRepository invoices;
    private final UserRepository users;
    private final UnitService unitService;
    private final Clock clock;

    public LeaseService(LeaseRepository leases, InvoiceRepository invoices, UserRepository users,
                        UnitService unitService, Clock clock) {
        this.leases = leases;
        this.invoices = invoices;
        this.users = users;
        this.unitService = unitService;
        this.clock = clock;
    }

    /**
     * Creates the lease, marks the unit occupied and generates every invoice, all in one
     * transaction: if any step fails, none of it is saved. There is never a lease without
     * its invoices or an occupied unit without a lease.
     */
    public LeaseDetailResponse create(Long managerId, CreateLeaseRequest request) {
        int termMonths = request.termMonths() == null ? DEFAULT_TERM_MONTHS : request.termMonths();
        if (termMonths % request.installments() != 0) {
            throw badRequest("installments must divide the lease term evenly (for 12 months: 1, 2, 3, 4, 6 or 12)");
        }
        User tenant = users.findByEmail(request.tenantEmail().trim().toLowerCase(Locale.ROOT))
                .filter(u -> u.getRole() == Role.TENANT)
                .orElseThrow(() -> badRequest("No tenant account exists with this email"));

        // Takes a row lock on the unit. A second request for the same unit waits here
        // until this transaction finishes, then sees the unit is OCCUPIED and gets a 409.
        Unit unit = unitService.lockVacantUnit(managerId, request.unitId());

        BigDecimal totalRent = request.totalRent() != null
                ? request.totalRent()
                : unit.getMonthlyRent().multiply(BigDecimal.valueOf(termMonths));
        BigDecimal deposit = request.securityDeposit() == null ? BigDecimal.ZERO : request.securityDeposit();
        LocalDate start = request.startDate();
        // A 12-month lease starting 1 Jan ends 31 Dec, not 1 Jan of the next year.
        LocalDate end = start.plusMonths(termMonths).minusDays(1);

        Lease lease = leases.save(new Lease(
                unit, tenant, managerId, start, end, totalRent, request.installments(), deposit));
        unit.occupy();

        List<Invoice> generated = new ArrayList<>();
        if (deposit.signum() > 0) {
            generated.add(new Invoice(lease, InvoiceType.DEPOSIT, 1, start, deposit));
        }
        for (var installment : RentSchedule.generate(start, termMonths, request.installments(), totalRent)) {
            generated.add(new Invoice(lease, InvoiceType.RENT, installment.sequenceNo(),
                    installment.dueDate(), installment.amount()));
        }
        invoices.saveAll(generated);

        return detail(lease, generated);
    }

    /** Managers see the leases they manage; tenants see their own. Same endpoint, different data. */
    @Transactional(readOnly = true)
    public PageResponse<LeaseResponse> list(AuthenticatedUser caller, int page, int size) {
        var pageable = Paging.of(page, size);
        Page<Lease> result = caller.role() == Role.MANAGER
                ? leases.findByManager(caller.id(), pageable)
                : leases.findByTenant(caller.id(), pageable);
        return PageResponse.from(result.map(LeaseResponse::from));
    }

    @Transactional(readOnly = true)
    public LeaseDetailResponse get(AuthenticatedUser caller, Long leaseId) {
        Lease lease = leases.findWithDetailsById(leaseId)
                .filter(l -> l.getManagerId().equals(caller.id()) || l.getTenant().getId().equals(caller.id()))
                .orElseThrow(() -> new NotFoundException("Lease not found"));
        return detail(lease, invoices.findByLeaseIdOrderByDueDateAscIdAsc(leaseId));
    }

    /**
     * Ends a lease early. Invoices not yet due are cancelled; invoices already due stay
     * PENDING because the tenant still owes them. The unit becomes available again.
     */
    public LeaseDetailResponse terminate(Long managerId, Long leaseId) {
        Lease lease = leases.findWithDetailsById(leaseId)
                .filter(l -> l.getManagerId().equals(managerId))
                .orElseThrow(() -> new NotFoundException("Lease not found"));
        if (lease.getStatus() != LeaseStatus.ACTIVE) {
            throw new ConflictException("This lease has already ended");
        }
        LocalDate today = LocalDate.now(clock);
        lease.terminate(today);
        lease.getUnit().vacate();

        List<Invoice> all = invoices.findByLeaseIdOrderByDueDateAscIdAsc(leaseId);
        all.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PENDING && i.getDueDate().isAfter(today))
                .forEach(Invoice::cancel);
        return detail(lease, all);
    }

    private LeaseDetailResponse detail(Lease lease, List<Invoice> leaseInvoices) {
        LocalDate today = LocalDate.now(clock);
        List<InvoiceResponse> items = leaseInvoices.stream().map(i -> InvoiceResponse.from(i, today)).toList();
        return new LeaseDetailResponse(LeaseResponse.from(lease), items);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
