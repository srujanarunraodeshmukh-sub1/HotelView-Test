package com.raghunath.hotelview.service.restaurantandinstantadmin;

import com.raghunath.hotelview.dto.restaurantandinstant.credit.*;
import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCompletedBills;
import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCustomer;
import com.raghunath.hotelview.repository.restaurantandinstant.InstantCreditCompletedBillsRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.InstantCreditCustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstantCreditBillService {

    private final InstantCreditCustomerRepository customerRepository;

    private final InstantCreditCompletedBillsRepository
            completedBillsRepository;


    // ============================================================
    // CREATE CREDIT BILL
    // ============================================================

    public CreditBillResponse createCreditBill(
            String hotelId,
            CreateCreditBillRequest request
    ) {

        validateAmount(
                request.getTotalAmount(),
                "Total amount"
        );

        validateAmount(
                request.getPaidAmount(),
                "Paid amount"
        );

        double totalAmount =
                money(request.getTotalAmount());

        double paidAmount =
                money(request.getPaidAmount());

        if (paidAmount > totalAmount) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Paid amount cannot be greater than total amount"
            );
        }

        if (request.getMobile() == null ||
                request.getMobile().trim().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mobile number is required"
            );
        }

        InstantCreditCustomer customer;

        // --------------------------------------------------------
        // Existing customer by customer ID
        // --------------------------------------------------------

        if (request.getCustomerId() != null &&
                !request.getCustomerId().trim().isEmpty()) {

            customer =
                    customerRepository
                            .findByIdAndHotelId(
                                    request.getCustomerId(),
                                    hotelId
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Customer not found"
                                    )
                            );

            if (request.getName() != null &&
                    !request.getName().trim().isEmpty()) {

                customer.setName(
                        request.getName().trim()
                );
            }

            if (request.getMobile() != null &&
                    !request.getMobile().trim().isEmpty()) {

                customer.setMobile(
                        request.getMobile().trim()
                );
            }

            if (request.getAddress() != null) {
                customer.setAddress(
                        request.getAddress().trim()
                );
            }

        } else {

            // ----------------------------------------------------
            // Existing customer by mobile
            // ----------------------------------------------------

            Optional<InstantCreditCustomer> existing =
                    customerRepository
                            .findByHotelIdAndMobile(
                                    hotelId,
                                    request.getMobile().trim()
                            );

            if (existing.isPresent()) {

                customer = existing.get();

                if (request.getName() != null &&
                        !request.getName().trim().isEmpty()) {

                    customer.setName(
                            request.getName().trim()
                    );
                }

                if (request.getAddress() != null) {

                    customer.setAddress(
                            request.getAddress().trim()
                    );
                }

            } else {

                // ------------------------------------------------
                // New customer
                // ------------------------------------------------

                customer =
                        new InstantCreditCustomer();

                customer.setHotelId(hotelId);

                customer.setName(
                        request.getName()
                );

                customer.setMobile(
                        request.getMobile().trim()
                );

                customer.setAddress(
                        request.getAddress()
                );

                customer.setCreatedAt(
                        LocalDateTime.now()
                );

                customer.setUpdatedAt(
                        LocalDateTime.now()
                );

                customer.setActive(false);

                customer.setLastSequenceNumber(0L);

                customer.setCreditEntries(
                        new ArrayList<>()
                );
            }
        }

        if (customer.getCreditEntries() == null) {

            customer.setCreditEntries(
                    new ArrayList<>()
            );
        }

        if (customer.getLastSequenceNumber() == null) {

            customer.setLastSequenceNumber(0L);
        }

        LocalDateTime now =
                LocalDateTime.now();

        // --------------------------------------------------------
        // Build commodities
        // --------------------------------------------------------

        List<InstantCreditCustomer.CreditCommodity>
                commodities =
                buildCommodities(
                        request.getCommodities()
                );

        double pendingAmount =
                money(
                        totalAmount - paidAmount
                );

        boolean completed =
                pendingAmount <= 0;

        // --------------------------------------------------------
        // Create bill
        // --------------------------------------------------------

        InstantCreditCustomer.CreditBill bill =
                new InstantCreditCustomer.CreditBill();

        bill.setBillId(
                UUID.randomUUID().toString()
        );

        bill.setBillDate(
                LocalDate.now()
        );

        bill.setCreatedAt(now);

        bill.setUpdatedAt(now);

        bill.setTotalAmount(
                totalAmount
        );

        bill.setPaidAmount(
                paidAmount
        );

        bill.setPendingAmount(
                pendingAmount
        );

        bill.setCompleted(
                completed
        );

        bill.setCommodities(
                commodities
        );

        bill.setPaymentReceived(
                new ArrayList<>()
        );

        bill.setEditHistory(
                new ArrayList<>()
        );

        // --------------------------------------------------------
        // Create CREDIT_BILL ledger entry
        // --------------------------------------------------------

        InstantCreditCustomer.CreditEntry entry =
                new InstantCreditCustomer.CreditEntry();

        entry.setEntryId(
                UUID.randomUUID().toString()
        );

        entry.setSequenceNumber(
                nextSequence(customer)
        );

        entry.setEntryType(
                InstantCreditCustomer.EntryType.CREDIT_BILL
        );

        entry.setTransactionDate(
                LocalDate.now()
        );

        entry.setTransactionTime(now);

        entry.setBill(bill);

        entry.setPayment(null);

        // --------------------------------------------------------
        // Already completely paid
        // --------------------------------------------------------

        if (completed) {

            InstantCreditCompletedBills history =
                    getOrCreateCompletedHistory(
                            customer
                    );

            history.getCompletedEntries()
                    .add(entry);

            sortEntries(
                    history.getCompletedEntries()
            );

            history.setUpdatedAt(now);

            completedBillsRepository.save(
                    history
            );

            customer.setActive(
                    hasActivePendingBills(customer)
            );

        } else {

            customer.getCreditEntries()
                    .add(entry);

            customer.setActive(true);
        }

        customer.setUpdatedAt(now);

        customerRepository.save(customer);

        return toBillResponse(
                customer.getId(),
                bill
        );
    }


    // ============================================================
    // GET ACTIVE CUSTOMERS
    // ============================================================


    public List<CreditCustomerResponse> getActiveCustomers(
            String hotelId
    ) {
        List<InstantCreditCustomer> customers =
                customerRepository.findByHotelIdOrderByNameAsc(hotelId);

        List<CreditCustomerResponse> response = new ArrayList<>();

        for (InstantCreditCustomer customer : customers) {
            List<InstantCreditCustomer.CreditEntry> activeBills =
                    getActiveBillEntries(customer);

            CreditCustomerResponse dto = new CreditCustomerResponse();

            dto.setCustomerId(customer.getId());
            dto.setName(customer.getName());
            dto.setMobile(customer.getMobile());
            dto.setAddress(customer.getAddress());
            dto.setActiveBillCount(activeBills.size());

            dto.setTotalPendingAmount(
                    money(activeBills.stream()
                            .mapToDouble(e -> e.getBill().getPendingAmount())
                            .sum())
            );

            response.add(dto);
        }

        return response;
    }


    // ============================================================
    // GET CUSTOMER ACTIVE BILLS
    // ============================================================



    public CreditCustomerDetailsResponse getCustomerDetails(
            String hotelId,
            String customerId
    ) {
        InstantCreditCustomer customer =
                validateCustomer(hotelId, customerId);

        List<InstantCreditCustomer.CreditEntry> activeBills =
                getActiveBillEntries(customer);

        CreditCustomerDetailsResponse response =
                new CreditCustomerDetailsResponse();

        response.setCustomerId(customer.getId());
        response.setName(customer.getName());
        response.setMobile(customer.getMobile());
        response.setAddress(customer.getAddress());

        response.setActiveBillCount(activeBills.size());
        response.setTotalPendingAmount(calculateTotalPending(customer));

        // Result array: active bills first, followed by payment transactions.
        List<Object> results = new ArrayList<>();

        // 1. Add active bills.
        activeBills.stream()
                .map(entry -> toBillResponse(
                        customer.getId(),
                        entry.getBill()
                ))
                .forEach(results::add);

        // 2. Collect payment transactions without duplicates.
        Set<String> addedPaymentEntryIds = new HashSet<>();

        // Search the customer's current ledger.
        if (customer.getCreditEntries() != null) {
            customer.getCreditEntries().stream()
                    .filter(entry ->
                            entry.getEntryType()
                                    == InstantCreditCustomer.EntryType.RECEIVE_PAYMENT
                    )
                    .sorted(Comparator.comparing(
                            InstantCreditCustomer.CreditEntry::getSequenceNumber,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .filter(entry ->
                            addedPaymentEntryIds.add(entry.getEntryId())
                    )
                    .map(entry -> toHistoryResponse(
                            entry,
                            customer.getId()
                    ))
                    .forEach(results::add);
        }

        // Search the completed history ledger.
        Optional<InstantCreditCompletedBills> history =
                completedBillsRepository.findByHotelIdAndCustomerId(
                        hotelId,
                        customerId
                );

        if (history.isPresent()
                && history.get().getCompletedEntries() != null) {

            history.get().getCompletedEntries().stream()
                    .filter(entry ->
                            "RECEIVE_PAYMENT".equals(entry.getEntryType())
                    )
                    .sorted(Comparator.comparing(
                            InstantCreditCustomer.CreditEntry::getSequenceNumber,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .filter(entry ->
                            addedPaymentEntryIds.add(entry.getEntryId())
                    )
                    .map(entry -> toHistoryResponse(
                            entry,
                            customer.getId()
                    ))
                    .forEach(results::add);
        }

        // Return bills and payment transactions in the same array.
        response.setActiveBills(results);

        return response;
    }

    // ============================================================
    // GET PENDING TRANSACTIONS
    // ============================================================

    public List<PendingTransactionResponse>
    getPendingTransactions(
            String hotelId
    ) {

        List<InstantCreditCustomer> customers =
                customerRepository
                        .findByHotelIdAndActiveTrueOrderByNameAsc(
                                hotelId
                        );

        List<PendingTransactionResponse> response =
                new ArrayList<>();

        for (InstantCreditCustomer customer :
                customers) {

            for (InstantCreditCustomer.CreditEntry entry :
                    getActiveBillEntries(customer)) {

                InstantCreditCustomer.CreditBill bill =
                        entry.getBill();

                PendingTransactionResponse dto =
                        new PendingTransactionResponse();

                dto.setBillId(
                        bill.getBillId()
                );

                dto.setCustomerId(
                        customer.getId()
                );

                dto.setCustomerName(
                        customer.getName()
                );

                dto.setCustomerMobile(
                        customer.getMobile()
                );

                dto.setBillDate(
                        bill.getBillDate()
                );

                dto.setTotalAmount(
                        money(
                                bill.getTotalAmount()
                        )
                );

                dto.setPaidAmount(
                        money(
                                bill.getPaidAmount()
                        )
                );

                dto.setPendingAmount(
                        money(
                                bill.getPendingAmount()
                        )
                );

                response.add(dto);
            }
        }

        return response;
    }


    // ============================================================
    // RECEIVE PAYMENT
    // ============================================================

    public ReceivePaymentResponse receivePayment(
            String hotelId,
            String customerId,
            ReceivePaymentRequest request
    ) {

        if (request == null ||
                request.getAmountReceived() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount is required"
            );
        }

        validateAmount(
                request.getAmountReceived(),
                "Payment amount"
        );

        double amountReceived =
                money(
                        request.getAmountReceived()
                );

        if (amountReceived <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount must be greater than zero"
            );
        }

        InstantCreditCustomer customer =
                validateCustomer(
                        hotelId,
                        customerId
                );

        List<InstantCreditCustomer.CreditEntry>
                activeBills =
                getActiveBillEntries(customer);

        if (activeBills.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer has no pending bills"
            );
        }

        double totalPending =
                calculateTotalPending(customer);

        if (amountReceived > totalPending) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount cannot be greater than total pending amount"
            );
        }

        InstantCreditCustomer.SettlementOrder order =
                request.getSettlementOrder();

        if (order == null) {

            order =
                    InstantCreditCustomer.SettlementOrder
                            .OLDEST_FIRST;
        }

        if (order ==
                InstantCreditCustomer.SettlementOrder.NEWEST_FIRST) {

            activeBills.sort(
                    Comparator.comparing(
                            e -> e.getBill().getCreatedAt(),
                            Comparator.nullsLast(
                                    Comparator.reverseOrder()
                            )
                    )
            );

        } else {

            activeBills.sort(
                    Comparator.comparing(
                            e -> e.getBill().getCreatedAt(),
                            Comparator.nullsLast(
                                    Comparator.naturalOrder()
                            )
                    )
            );
        }

        String paymentId =
                UUID.randomUUID().toString();

        LocalDateTime paymentTime =
                LocalDateTime.now();

        double remainingPayment =
                amountReceived;

        List<ReceivePaymentResponse.Allocation>
                responseAllocations =
                new ArrayList<>();

        List<InstantCreditCustomer.PaymentAllocation>
                ledgerAllocations =
                new ArrayList<>();

        Set<String> completedBillIds =
                new HashSet<>();

        // --------------------------------------------------------
        // Apply payment bill by bill
        // --------------------------------------------------------

        for (InstantCreditCustomer.CreditEntry entry :
                activeBills) {

            if (remainingPayment <= 0) {
                break;
            }

            InstantCreditCustomer.CreditBill bill =
                    entry.getBill();

            double pending =
                    money(
                            bill.getPendingAmount()
                    );

            double applied =
                    money(
                            Math.min(
                                    remainingPayment,
                                    pending
                            )
                    );

            bill.setPaidAmount(
                    money(
                            bill.getPaidAmount()
                                    + applied
                    )
            );

            bill.setPendingAmount(
                    money(
                            bill.getTotalAmount()
                                    - bill.getPaidAmount()
                    )
            );

            boolean billCompleted =
                    bill.getPendingAmount() <= 0;

            bill.setCompleted(
                    billCompleted
            );

            if (billCompleted) {

                bill.setPendingAmount(0);

                completedBillIds.add(
                        bill.getBillId()
                );
            }

            bill.setUpdatedAt(
                    paymentTime
            );

            // ----------------------------------------------------
            // Add payment to bill
            // ----------------------------------------------------

            InstantCreditCustomer.PaymentAllocation
                    billAllocation =
                    new InstantCreditCustomer.PaymentAllocation();

            billAllocation.setBillId(
                    bill.getBillId()
            );

            billAllocation.setAmountApplied(
                    applied
            );

            InstantCreditCustomer.PaymentReceived
                    billPayment =
                    new InstantCreditCustomer.PaymentReceived();

            billPayment.setPaymentId(
                    paymentId
            );

            billPayment.setAmountReceived(
                    applied
            );

            billPayment.setPaymentDate(
                    LocalDate.now()
            );

            billPayment.setReceivedAt(
                    paymentTime
            );

            billPayment.setSettlementOrder(
                    order
            );

            billPayment.setAllocations(
                    new ArrayList<>(
                            List.of(
                                    billAllocation
                            )
                    )
            );

            if (bill.getPaymentReceived() == null) {

                bill.setPaymentReceived(
                        new ArrayList<>()
                );
            }

            bill.getPaymentReceived()
                    .add(billPayment);

            // ----------------------------------------------------
            // Ledger allocation
            // ----------------------------------------------------

            InstantCreditCustomer.PaymentAllocation
                    ledgerAllocation =
                    new InstantCreditCustomer.PaymentAllocation();

            ledgerAllocation.setBillId(
                    bill.getBillId()
            );

            ledgerAllocation.setAmountApplied(
                    applied
            );

            ledgerAllocations.add(
                    ledgerAllocation
            );

            // ----------------------------------------------------
            // Response
            // ----------------------------------------------------

            ReceivePaymentResponse.Allocation
                    responseAllocation =
                    new ReceivePaymentResponse.Allocation();

            responseAllocation.setBillId(
                    bill.getBillId()
            );

            responseAllocation.setAmountApplied(
                    applied
            );

            responseAllocation.setRemainingPendingAmount(
                    money(
                            bill.getPendingAmount()
                    )
            );

            responseAllocation.setBillCompleted(
                    billCompleted
            );

            responseAllocations.add(
                    responseAllocation
            );

            remainingPayment =
                    money(
                            remainingPayment - applied
                    );
        }

        // --------------------------------------------------------
        // Create chronological RECEIVE_PAYMENT entry
        // --------------------------------------------------------

        InstantCreditCustomer.PaymentReceived payment =
                new InstantCreditCustomer.PaymentReceived();

        payment.setPaymentId(
                paymentId
        );

        payment.setAmountReceived(
                amountReceived
        );

        payment.setPaymentDate(
                LocalDate.now()
        );

        payment.setReceivedAt(
                paymentTime
        );

        payment.setSettlementOrder(
                order
        );

        payment.setAllocations(
                ledgerAllocations
        );

        InstantCreditCustomer.CreditEntry paymentEntry =
                new InstantCreditCustomer.CreditEntry();

        paymentEntry.setEntryId(
                UUID.randomUUID().toString()
        );

        paymentEntry.setSequenceNumber(
                nextSequence(customer)
        );

        paymentEntry.setEntryType(
                InstantCreditCustomer.EntryType.RECEIVE_PAYMENT
        );

        paymentEntry.setTransactionDate(
                LocalDate.now()
        );

        paymentEntry.setTransactionTime(
                paymentTime
        );

        paymentEntry.setBill(null);

        paymentEntry.setPayment(payment);

        customer.getCreditEntries()
                .add(paymentEntry);

        // --------------------------------------------------------
        // Move completed bills
        // --------------------------------------------------------

        if (!completedBillIds.isEmpty()) {

            moveCompletedBillsAndPayments(
                    customer,
                    completedBillIds
            );
        }

        customer.setActive(
                hasActivePendingBills(customer)
        );

        customer.setUpdatedAt(
                paymentTime
        );

        saveCompletedHistory(customer);

        customerRepository.save(customer);

        // --------------------------------------------------------
        // Response
        // --------------------------------------------------------

        ReceivePaymentResponse response =
                new ReceivePaymentResponse();

        response.setPaymentId(
                paymentId
        );

        response.setAmountReceived(
                amountReceived
        );

        response.setAmountApplied(
                money(
                        amountReceived - remainingPayment
                )
        );

        response.setUnappliedAmount(
                remainingPayment
        );

        response.setTotalPendingAmount(
                calculateTotalPending(customer)
        );

        response.setAllocations(
                responseAllocations
        );

        return response;
    }


    // ============================================================
    // EDIT CREDIT BILL
    // ============================================================

    public CreditBillResponse editCreditBill(
            String hotelId,
            String billId,
            EditCreditBillRequest request
    ) {

        validateAmount(
                request.getTotalAmount(),
                "Total amount"
        );

        validateAmount(
                request.getPaidAmount(),
                "Paid amount"
        );

        double totalAmount =
                money(
                        request.getTotalAmount()
                );

        double paidAmount =
                money(
                        request.getPaidAmount()
                );

        if (paidAmount > totalAmount) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Paid amount cannot be greater than total amount"
            );
        }

        InstantCreditCustomer customer =
                findCustomerContainingBill(
                        hotelId,
                        billId
                );

        InstantCreditCustomer.CreditEntry billEntry =
                customer.getCreditEntries()
                        .stream()
                        .filter(
                                e ->
                                        e.getEntryType() ==
                                                InstantCreditCustomer.EntryType
                                                        .CREDIT_BILL
                                                &&
                                                e.getBill() != null
                                                &&
                                                billId.equals(
                                                        e.getBill()
                                                                .getBillId()
                                                )
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Active credit bill not found"
                                        )
                        );

        InstantCreditCustomer.CreditBill bill =
                billEntry.getBill();

        LocalDateTime now =
                LocalDateTime.now();

        // --------------------------------------------------------
        // Save edit history
        // --------------------------------------------------------

        InstantCreditCustomer.CreditBillEdit edit =
                new InstantCreditCustomer.CreditBillEdit();

        edit.setEditId(
                UUID.randomUUID().toString()
        );

        edit.setBillId(
                bill.getBillId()
        );

        edit.setPreviousTotalAmount(
                bill.getTotalAmount()
        );

        edit.setPreviousPaidAmount(
                bill.getPaidAmount()
        );

        edit.setPreviousPendingAmount(
                bill.getPendingAmount()
        );

        edit.setUpdatedTotalAmount(
                totalAmount
        );

        edit.setUpdatedPaidAmount(
                paidAmount
        );

        edit.setUpdatedPendingAmount(
                money(
                        totalAmount - paidAmount
                )
        );

        edit.setEditReason(
                request.getEditReason()
        );

        edit.setEditedAt(now);

        if (bill.getEditHistory() == null) {

            bill.setEditHistory(
                    new ArrayList<>()
            );
        }

        bill.getEditHistory()
                .add(edit);

        // --------------------------------------------------------
        // Update bill
        // --------------------------------------------------------

        double pendingAmount =
                money(
                        totalAmount - paidAmount
                );

        bill.setTotalAmount(
                totalAmount
        );

        bill.setPaidAmount(
                paidAmount
        );

        bill.setPendingAmount(
                pendingAmount
        );

        bill.setCompleted(
                pendingAmount <= 0
        );

        if (request.getCommodities() != null) {

            bill.setCommodities(
                    buildCommodities(
                            request.getCommodities()
                    )
            );
        }

        bill.setUpdatedAt(now);

        // --------------------------------------------------------
        // If bill became completed
        // --------------------------------------------------------

        if (bill.isCompleted()) {

            Set<String> completedBillIds =
                    new HashSet<>();

            completedBillIds.add(
                    bill.getBillId()
            );

            moveCompletedBillsAndPayments(
                    customer,
                    completedBillIds
            );
        }

        customer.setActive(
                hasActivePendingBills(customer)
        );

        customer.setUpdatedAt(now);

        saveCompletedHistory(customer);

        customerRepository.save(customer);

        return toBillResponse(
                customer.getId(),
                bill
        );
    }


    // ============================================================
    // CUSTOMER HISTORY
    // ============================================================

    public List<CreditHistoryResponse> getCustomerHistory(
            String hotelId,
            String customerId
    ) {

        validateCustomer(
                hotelId,
                customerId
        );

        Optional<InstantCreditCompletedBills> history =
                completedBillsRepository
                        .findByHotelIdAndCustomerId(
                                hotelId,
                                customerId
                        );

        if (history.isEmpty() ||
                history.get().getCompletedEntries() == null) {

            return new ArrayList<>();
        }

        return history.get()
                .getCompletedEntries()
                .stream()
                .sorted(
                        Comparator.comparing(
                                InstantCreditCustomer.CreditEntry
                                        ::getSequenceNumber
                        )
                )
                .map(
                        entry ->
                                toHistoryResponse(
                                        entry,
                                        customerId
                                )
                )
                .collect(Collectors.toList());
    }


    // ============================================================
    // ALL HISTORY
    // ============================================================

    public List<CreditHistoryResponse> getAllHistory(
            String hotelId
    ) {

        List<InstantCreditCompletedBills> histories =
                completedBillsRepository
                        .findByHotelIdOrderByUpdatedAtDesc(
                                hotelId
                        );

        List<CreditHistoryResponse> response =
                new ArrayList<>();

        for (InstantCreditCompletedBills history :
                histories) {

            if (history.getCompletedEntries() == null) {
                continue;
            }

            for (InstantCreditCustomer.CreditEntry entry :
                    history.getCompletedEntries()) {

                response.add(
                        toHistoryResponse(
                                entry,
                                history.getCustomerId()
                        )
                );
            }
        }

        response.sort(
                Comparator.comparing(
                        CreditHistoryResponse
                                ::getTransactionTime,
                        Comparator.nullsLast(
                                Comparator.reverseOrder()
                        )
                )
        );

        return response;
    }


    // ============================================================
    // FIND CUSTOMER
    // ============================================================

    private InstantCreditCustomer validateCustomer(
            String hotelId,
            String customerId
    ) {

        return customerRepository
                .findByIdAndHotelId(
                        customerId,
                        hotelId
                )
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Customer not found"
                                )
                );
    }


    // ============================================================
    // FIND CUSTOMER CONTAINING BILL
    // ============================================================

    private InstantCreditCustomer findCustomerContainingBill(
            String hotelId,
            String billId
    ) {

        List<InstantCreditCustomer> customers =
                customerRepository
                        .findByHotelIdAndActiveTrueOrderByNameAsc(
                                hotelId
                        );

        for (InstantCreditCustomer customer :
                customers) {

            if (customer.getCreditEntries() == null) {
                continue;
            }

            boolean found =
                    customer.getCreditEntries()
                            .stream()
                            .anyMatch(
                                    e ->
                                            e.getEntryType() ==
                                                    InstantCreditCustomer
                                                            .EntryType
                                                            .CREDIT_BILL
                                                    &&
                                                    e.getBill() != null
                                                    &&
                                                    billId.equals(
                                                            e.getBill()
                                                                    .getBillId()
                                                    )
                            );

            if (found) {
                return customer;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Active credit bill not found"
        );
    }


    // ============================================================
    // ACTIVE BILL ENTRIES
    // ============================================================

    private List<InstantCreditCustomer.CreditEntry>
    getActiveBillEntries(
            InstantCreditCustomer customer
    ) {

        if (customer.getCreditEntries() == null) {

            return new ArrayList<>();
        }

        return customer.getCreditEntries()
                .stream()
                .filter(
                        entry ->
                                entry.getEntryType() ==
                                        InstantCreditCustomer.EntryType
                                                .CREDIT_BILL
                                        &&
                                        entry.getBill() != null
                                        &&
                                        !entry.getBill()
                                                .isCompleted()
                                        &&
                                        entry.getBill()
                                                .getPendingAmount() > 0
                )
                .sorted(
                        Comparator.comparing(
                                e ->
                                        e.getBill()
                                                .getCreatedAt(),
                                Comparator.nullsLast(
                                        Comparator.naturalOrder()
                                )
                        )
                )
                .collect(Collectors.toList());
    }


    // ============================================================
    // MOVE COMPLETED BILLS AND PAYMENTS
    // ============================================================

    // ============================================================
// MOVE COMPLETED BILLS AND PAYMENTS
// ============================================================

    private void moveCompletedBillsAndPayments(
            InstantCreditCustomer customer,
            Set<String> completedBillIds
    ) {

        if (completedBillIds == null ||
                completedBillIds.isEmpty()) {

            return;
        }

        if (customer.getCreditEntries() == null) {
            return;
        }

        InstantCreditCompletedBills history =
                getOrCreateCompletedHistory(customer);

        if (history.getCompletedEntries() == null) {

            history.setCompletedEntries(
                    new ArrayList<>()
            );
        }

        /*
         * IMPORTANT RULE:
         *
         * CREDIT_BILL:
         *     Move individually when that bill is completed.
         *
         * RECEIVE_PAYMENT:
         *     NEVER split the payment entry.
         *
         *     If even ONE bill referenced by the payment is still
         *     active, keep the COMPLETE payment entry in the
         *     active customer document.
         *
         *     Only when ALL bills referenced by that payment are
         *     completed, move the COMPLETE payment entry to history.
         */

        List<InstantCreditCustomer.CreditEntry>
                remainingActiveEntries =
                new ArrayList<>();


        // ------------------------------------------------------------
        // First determine which bills are STILL ACTIVE
        // ------------------------------------------------------------

        Set<String> activeBillIds =
                customer.getCreditEntries()
                        .stream()
                        .filter(entry ->
                                entry.getEntryType() ==
                                        InstantCreditCustomer.EntryType
                                                .CREDIT_BILL
                                        &&
                                        entry.getBill() != null
                                        &&
                                        !entry.getBill().isCompleted()
                                        &&
                                        entry.getBill()
                                                .getPendingAmount() > 0
                        )
                        .map(entry ->
                                entry.getBill().getBillId()
                        )
                        .collect(Collectors.toSet());


        // ------------------------------------------------------------
        // Process ledger entries
        // ------------------------------------------------------------

        for (InstantCreditCustomer.CreditEntry entry :
                customer.getCreditEntries()) {


            // ========================================================
            // CREDIT BILL
            // ========================================================

            if (entry.getEntryType() ==
                    InstantCreditCustomer.EntryType.CREDIT_BILL) {

                if (entry.getBill() != null &&
                        completedBillIds.contains(
                                entry.getBill().getBillId()
                        )) {

                    /*
                     * Only the completed BILL entry moves.
                     */
                    history.getCompletedEntries()
                            .add(entry);

                } else {

                    remainingActiveEntries.add(entry);
                }

                continue;
            }


            // ========================================================
            // RECEIVE PAYMENT
            // ========================================================

            if (entry.getEntryType() ==
                    InstantCreditCustomer.EntryType.RECEIVE_PAYMENT) {

                InstantCreditCustomer.PaymentReceived payment =
                        entry.getPayment();

                if (payment == null ||
                        payment.getAllocations() == null ||
                        payment.getAllocations().isEmpty()) {

                    remainingActiveEntries.add(entry);

                    continue;
                }


                /*
                 * Check whether ANY bill associated with this payment
                 * is still active.
                 */

                boolean hasActiveBillAllocation =
                        payment.getAllocations()
                                .stream()
                                .anyMatch(allocation ->
                                        activeBillIds.contains(
                                                allocation.getBillId()
                                        )
                                );


                if (hasActiveBillAllocation) {

                    /*
                     * IMPORTANT:
                     *
                     * Keep the COMPLETE payment entry.
                     *
                     * DO NOT split the payment.
                     *
                     * Example:
                     *
                     * ₹4,500 payment
                     *     ₹4,000 → completed Bill 1
                     *     ₹500   → active Bill 2
                     *
                     * The complete ₹4,500 entry stays here.
                     */

                    remainingActiveEntries.add(entry);

                } else {

                    /*
                     * NONE of the bills associated with this payment
                     * are active anymore.
                     *
                     * Therefore ALL bills associated with this
                     * payment are completed.
                     *
                     * Move the COMPLETE payment entry to history.
                     */

                    history.getCompletedEntries()
                            .add(entry);
                }

                continue;
            }


            // ========================================================
            // UNKNOWN ENTRY TYPE
            // ========================================================

            remainingActiveEntries.add(entry);
        }


        // ------------------------------------------------------------
        // Update active customer
        // ------------------------------------------------------------

        customer.setCreditEntries(
                remainingActiveEntries
        );

        sortEntries(
                customer.getCreditEntries()
        );


        // ------------------------------------------------------------
        // Sort completed history
        // ------------------------------------------------------------

        sortEntries(
                history.getCompletedEntries()
        );


        history.setUpdatedAt(
                LocalDateTime.now()
        );

        completedBillsRepository.save(
                history
        );
    }


    // ============================================================
    // COPY PAYMENT ENTRY
    // ============================================================

    private InstantCreditCustomer.CreditEntry
    copyPaymentEntry(
            InstantCreditCustomer.CreditEntry original,
            List<InstantCreditCustomer.PaymentAllocation>
                    allocations
    ) {

        InstantCreditCustomer.CreditEntry copy =
                new InstantCreditCustomer.CreditEntry();

        copy.setEntryId(
                original.getEntryId()
        );

        copy.setSequenceNumber(
                original.getSequenceNumber()
        );

        copy.setEntryType(
                InstantCreditCustomer.EntryType.RECEIVE_PAYMENT
        );

        copy.setTransactionDate(
                original.getTransactionDate()
        );

        copy.setTransactionTime(
                original.getTransactionTime()
        );

        copy.setBill(null);

        InstantCreditCustomer.PaymentReceived originalPayment =
                original.getPayment();

        InstantCreditCustomer.PaymentReceived newPayment =
                new InstantCreditCustomer.PaymentReceived();

        newPayment.setPaymentId(
                originalPayment.getPaymentId()
        );

        double amount =
                allocations.stream()
                        .mapToDouble(
                                InstantCreditCustomer
                                        .PaymentAllocation
                                        ::getAmountApplied
                        )
                        .sum();

        newPayment.setAmountReceived(
                money(amount)
        );

        newPayment.setPaymentDate(
                originalPayment.getPaymentDate()
        );

        newPayment.setReceivedAt(
                originalPayment.getReceivedAt()
        );

        newPayment.setSettlementOrder(
                originalPayment.getSettlementOrder()
        );

        newPayment.setAllocations(
                allocations
        );

        copy.setPayment(
                newPayment
        );

        return copy;
    }


    // ============================================================
    // COPY ALLOCATION
    // ============================================================

    private InstantCreditCustomer.PaymentAllocation
    copyAllocation(
            InstantCreditCustomer.PaymentAllocation source
    ) {

        InstantCreditCustomer.PaymentAllocation copy =
                new InstantCreditCustomer.PaymentAllocation();

        copy.setBillId(
                source.getBillId()
        );

        copy.setAmountApplied(
                money(
                        source.getAmountApplied()
                )
        );

        return copy;
    }


    // ============================================================
    // GET OR CREATE HISTORY
    // ============================================================

    private InstantCreditCompletedBills
    getOrCreateCompletedHistory(
            InstantCreditCustomer customer
    ) {

        Optional<InstantCreditCompletedBills> existing =
                completedBillsRepository
                        .findByHotelIdAndCustomerId(
                                customer.getHotelId(),
                                customer.getId()
                        );

        if (existing.isPresent()) {

            InstantCreditCompletedBills history =
                    existing.get();

            history.setCustomerName(
                    customer.getName()
            );

            history.setCustomerMobile(
                    customer.getMobile()
            );

            history.setCustomerAddress(
                    customer.getAddress()
            );

            if (history.getCompletedEntries() == null) {

                history.setCompletedEntries(
                        new ArrayList<>()
                );
            }

            return history;
        }

        InstantCreditCompletedBills history =
                new InstantCreditCompletedBills();

        history.setHotelId(
                customer.getHotelId()
        );

        history.setCustomerId(
                customer.getId()
        );

        history.setCustomerName(
                customer.getName()
        );

        history.setCustomerMobile(
                customer.getMobile()
        );

        history.setCustomerAddress(
                customer.getAddress()
        );

        history.setCompletedEntries(
                new ArrayList<>()
        );

        history.setCreatedAt(
                LocalDateTime.now()
        );

        history.setUpdatedAt(
                LocalDateTime.now()
        );

        return history;
    }


    // ============================================================
    // SAVE HISTORY
    // ============================================================

    private void saveCompletedHistory(
            InstantCreditCustomer customer
    ) {

        Optional<InstantCreditCompletedBills> existing =
                completedBillsRepository
                        .findByHotelIdAndCustomerId(
                                customer.getHotelId(),
                                customer.getId()
                        );

        if (existing.isEmpty()) {
            return;
        }

        InstantCreditCompletedBills history =
                existing.get();

        if (history.getCompletedEntries() == null) {

            history.setCompletedEntries(
                    new ArrayList<>()
            );
        }

        sortEntries(
                history.getCompletedEntries()
        );

        history.setCustomerName(
                customer.getName()
        );

        history.setCustomerMobile(
                customer.getMobile()
        );

        history.setCustomerAddress(
                customer.getAddress()
        );

        history.setUpdatedAt(
                LocalDateTime.now()
        );

        completedBillsRepository.save(
                history
        );
    }


    // ============================================================
    // ACTIVE STATUS
    // ============================================================

    private boolean hasActivePendingBills(
            InstantCreditCustomer customer
    ) {

        return !getActiveBillEntries(customer)
                .isEmpty();
    }


    // ============================================================
    // TOTAL PENDING
    // ============================================================

    private double calculateTotalPending(
            InstantCreditCustomer customer
    ) {

        return money(
                getActiveBillEntries(customer)
                        .stream()
                        .mapToDouble(
                                e ->
                                        e.getBill()
                                                .getPendingAmount()
                        )
                        .sum()
        );
    }


    // ============================================================
    // NEXT SEQUENCE
    // ============================================================

    private long nextSequence(
            InstantCreditCustomer customer
    ) {

        Long current =
                customer.getLastSequenceNumber();

        if (current == null) {
            current = 0L;
        }

        long next =
                current + 1;

        customer.setLastSequenceNumber(
                next
        );

        return next;
    }


    // ============================================================
    // SORT
    // ============================================================

    private void sortEntries(
            List<InstantCreditCustomer.CreditEntry>
                    entries
    ) {

        if (entries == null) {
            return;
        }

        entries.sort(
                Comparator.comparing(
                        InstantCreditCustomer.CreditEntry
                                ::getSequenceNumber,
                        Comparator.nullsLast(
                                Comparator.naturalOrder()
                        )
                )
        );
    }


    // ============================================================
    // BUILD COMMODITIES
    // ============================================================

    private List<InstantCreditCustomer.CreditCommodity>
    buildCommodities(
            List<CommodityRequest> requests
    ) {

        List<InstantCreditCustomer.CreditCommodity>
                commodities =
                new ArrayList<>();

        if (requests == null) {
            return commodities;
        }

        for (CommodityRequest request :
                requests) {

            if (request == null) {
                continue;
            }

            if (request.getRate() == null) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Commodity rate is required"
                );
            }

            if (request.getQuantity() == null) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Commodity quantity is required"
                );
            }

            double subtotal =
                    money(
                            request.getRate()
                                    * request.getQuantity()
                    );

            InstantCreditCustomer.CreditCommodity commodity =
                    new InstantCreditCustomer.CreditCommodity();

            commodity.setId(
                    request.getId()
            );

            commodity.setName(
                    request.getName()
            );

            commodity.setRate(
                    money(request.getRate())
            );

            commodity.setQuantity(
                    request.getQuantity()
            );

            commodity.setUnit(
                    request.getUnit()
            );

            commodity.setSubtotal(
                    subtotal
            );

            commodities.add(
                    commodity
            );
        }

        return commodities;
    }


    // ============================================================
    // BILL RESPONSE
    // ============================================================

    private CreditBillResponse toBillResponse(
            String customerId,
            InstantCreditCustomer.CreditBill bill
    ) {

        CreditBillResponse response =
                new CreditBillResponse();

        response.setBillId(
                bill.getBillId()
        );

        response.setCustomerId(
                customerId
        );

        response.setBillDate(
                bill.getBillDate()
        );

        response.setCreatedAt(
                bill.getCreatedAt()
        );

        response.setUpdatedAt(
                bill.getUpdatedAt()
        );

        response.setTotalAmount(
                money(
                        bill.getTotalAmount()
                )
        );

        response.setPaidAmount(
                money(
                        bill.getPaidAmount()
                )
        );

        response.setPendingAmount(
                money(
                        bill.getPendingAmount()
                )
        );

        response.setCompleted(
                bill.isCompleted()
        );

        List<CommodityResponse>
                commodities =
                new ArrayList<>();

        if (bill.getCommodities() != null) {

            for (InstantCreditCustomer.CreditCommodity commodity :
                    bill.getCommodities()) {

                CommodityResponse dto =
                        new CommodityResponse();

                dto.setId(
                        commodity.getId()
                );

                dto.setName(
                        commodity.getName()
                );

                dto.setRate(
                        money(
                                commodity.getRate()
                        )
                );

                dto.setQuantity(
                        commodity.getQuantity()
                );

                dto.setUnit(
                        commodity.getUnit()
                );

                dto.setSubtotal(
                        money(
                                commodity.getSubtotal()
                        )
                );

                commodities.add(dto);
            }
        }

        response.setCommodities(
                commodities
        );

        response.setPaymentReceived(
                bill.getPaymentReceived() != null
                        ? bill.getPaymentReceived()
                        : new ArrayList<>()
        );

        response.setEditHistory(
                bill.getEditHistory() != null
                        ? bill.getEditHistory()
                        : new ArrayList<>()
        );

        return response;
    }


    // ============================================================
    // HISTORY RESPONSE
    // ============================================================

    private CreditHistoryResponse toHistoryResponse(
            InstantCreditCustomer.CreditEntry entry,
            String customerId
    ) {

        CreditHistoryResponse response =
                new CreditHistoryResponse();

        response.setEntryId(
                entry.getEntryId()
        );

        response.setSequenceNumber(
                entry.getSequenceNumber()
        );

        response.setEntryType(
                entry.getEntryType()
        );

        response.setTransactionDate(
                entry.getTransactionDate()
        );

        response.setTransactionTime(
                entry.getTransactionTime()
        );

        if (entry.getBill() != null) {

            response.setBill(
                    toBillResponse(
                            customerId,
                            entry.getBill()
                    )
            );
        }

        response.setPayment(
                entry.getPayment()
        );

        return response;
    }


    // ============================================================
    // MONEY
    // ============================================================

    private double money(
            double value
    ) {

        return BigDecimal
                .valueOf(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }


    // ============================================================
    // VALIDATE AMOUNT
    // ============================================================

    private void validateAmount(
            Double value,
            String fieldName
    ) {

        if (value == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " is required"
            );
        }

        if (value < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " cannot be negative"
            );
        }
    }
}