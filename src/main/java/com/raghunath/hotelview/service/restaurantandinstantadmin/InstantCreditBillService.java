package com.raghunath.hotelview.service.restaurantandinstantadmin;

import com.raghunath.hotelview.dto.restaurantandinstant.credit.*;
import com.raghunath.hotelview.entity.restaurantandinstant.Admin;
import com.raghunath.hotelview.entity.restaurantandinstant.CompletedOrder;
import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCompletedBills;
import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCustomer;
import com.raghunath.hotelview.repository.restaurantandinstant.AdminRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.CompleteOrderRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.InstantCreditCompletedBillsRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.InstantCreditCustomerRepository;

import java.time.LocalDate;
import java.time.ZoneId;

import org.bson.Document;
import org.springframework.data.mongodb.core.query.Update;

import com.raghunath.hotelview.dto.restaurantandinstant.OrderItem;
import com.raghunath.hotelview.entity.restaurantandinstant.KitchenOrderSnapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import com.raghunath.hotelview.dto.restaurantandinstant.credit.CreditCustomerChangesResponse;
import com.raghunath.hotelview.dto.restaurantandinstant.credit.CreditCustomerPageResponse;
import com.raghunath.hotelview.dto.restaurantandinstant.credit.CreditCustomerResponse;
import com.raghunath.hotelview.dto.restaurantandinstant.credit.CreditCustomerVersionResponse;

import com.raghunath.hotelview.entity.restaurantandinstant.KitchenOrderSnapshot;
import com.raghunath.hotelview.dto.restaurantandinstant.OrderItem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstantCreditBillService {

    private final AdminRepository adminRepository;
    private final CompleteOrderRepository completeOrderRepository;
    private final MongoTemplate mongoTemplate;

    private final InstantCreditCustomerRepository customerRepository;

    private final InstantCreditCompletedBillsRepository
            completedBillsRepository;

    private static final DateTimeFormatter SYNC_TIMESTAMP_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;


// ============================================================
// GET CUSTOMER VERSION AND COUNT
// ============================================================

    public CreditCustomerVersionResponse getCustomerVersion(
            String hotelId
    ) {
        long totalCount =
                customerRepository.countByHotelId(hotelId);

        LocalDateTime latestUpdatedAt =
                customerRepository
                        .findTopByHotelIdOrderByUpdatedAtDescIdDesc(hotelId)
                        .map(InstantCreditCustomer::getUpdatedAt)
                        .orElse(LocalDateTime.of(
                                1970, 1, 1, 0, 0
                        ));

        return new CreditCustomerVersionResponse(
                latestUpdatedAt.format(SYNC_TIMESTAMP_FORMAT),
                totalCount
        );
    }


// ============================================================
// GET CUSTOMERS CHANGED SINCE TIMESTAMP
// ============================================================

    public CreditCustomerChangesResponse getCustomerChanges(
            String hotelId,
            String afterVersion
    ) {
        LocalDateTime from;

        try {
            from = LocalDateTime.parse(
                    afterVersion,
                    SYNC_TIMESTAMP_FORMAT
            );
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid afterVersion. Use ISO date-time format, e.g. 2026-10-12T20:15:30"
            );
        }

        CreditCustomerVersionResponse currentVersion =
                getCustomerVersion(hotelId);

        LocalDateTime to = LocalDateTime.parse(
                currentVersion.getVersion(),
                SYNC_TIMESTAMP_FORMAT
        );

        List<InstantCreditCustomer> changedCustomers =
                customerRepository.findChangedCustomersBetween(
                        hotelId,
                        from,
                        to
                );

        List<CreditCustomerResponse> customers =
                changedCustomers.stream()
                        .map(this::toCustomerListResponse)
                        .collect(Collectors.toList());

        return new CreditCustomerChangesResponse(
                currentVersion.getVersion(),
                customers
        );
    }


// ============================================================
// GET CUSTOMERS WITH PAGINATION
// ============================================================

    public CreditCustomerPageResponse getCustomerPage(
            String hotelId,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100"
            );
        }

        Page<InstantCreditCustomer> customerPage =
                customerRepository.findByHotelId(
                        hotelId,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        Sort.Order.desc("createdAt"),
                                        Sort.Order.desc("id")
                                )
                        )
                );

        List<CreditCustomerResponse> customers =
                customerPage.getContent()
                        .stream()
                        .map(this::toCustomerListResponse)
                        .collect(Collectors.toList());

        CreditCustomerVersionResponse currentVersion =
                getCustomerVersion(hotelId);

        return new CreditCustomerPageResponse(
                currentVersion.getVersion(),
                customerPage.getTotalElements(),
                page,
                size,
                customerPage.hasNext(),
                customers
        );
    }


    public CreditBillDashboardStatsDTO getCreditBillDashboardStats(
            String hotelId
    ) {

        // --------------------------------------------------------
        // Current date and time in IST
        // --------------------------------------------------------

        ZoneId istZone = ZoneId.of("Asia/Kolkata");

        ZonedDateTime nowIST = ZonedDateTime.now(istZone);

        String todayDate = nowIST.format(
                DateTimeFormatter.ISO_LOCAL_DATE
        );

        ZonedDateTime last24HoursStart = nowIST.minusHours(24);

        String last24HoursStartDate = last24HoursStart.format(
                DateTimeFormatter.ISO_LOCAL_DATE
        );

        // --------------------------------------------------------
        // Fetch restaurant/admin details
        // --------------------------------------------------------

        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Hotel Admin not found"
                        )
                );

        String restaurantName =
                admin.getRestaurantName() != null
                        ? admin.getRestaurantName()
                        : "";

        String planType =
                admin.getPlanType() != null
                        ? admin.getPlanType()
                        : "BASIC";

        // --------------------------------------------------------
        // Today's order counts
        // --------------------------------------------------------

        List<String> deliveryTypes = List.of("HOME", "PARCEL");

        Long homeDeliveriesCount =
                completeOrderRepository
                        .countByHotelIdAndOrderTypeInAndCheckoutDate(
                                hotelId,
                                deliveryTypes,
                                todayDate
                        );

        List<String> instantTypes = List.of("INSTANT");

        Long instantCount =
                completeOrderRepository
                        .countByHotelIdAndOrderTypeInAndCheckoutDate(
                                hotelId,
                                instantTypes,
                                todayDate
                        );

        Long completedOrdersTodayCount =
                completeOrderRepository
                        .countByHotelIdAndCheckoutDate(
                                hotelId,
                                todayDate
                        );

        // --------------------------------------------------------
        // Today's sales
        // --------------------------------------------------------

        Double todaySalesRupees = 0.0;

        try {

            Double result =
                    completeOrderRepository
                            .sumTotalPayableByHotelIdAndCheckoutDate(
                                    hotelId,
                                    todayDate
                            );

            todaySalesRupees = result != null ? result : 0.0;

        } catch (Exception e) {

            log.error(
                    "CREDIT_DASHBOARD: Today's sales calculation failed for hotel {}",
                    hotelId,
                    e
            );
        }

        // --------------------------------------------------------
        // Last 24 hours sales
        // --------------------------------------------------------

        Double last24HoursSalesRupees = 0.0;

        try {

            List<CompletedOrder> completedOrders =
                    mongoTemplate.find(
                            new Query(
                                    Criteria.where("hotelId").is(hotelId)
                                            .orOperator(
                                                    Criteria.where("checkoutDate")
                                                            .is(todayDate),
                                                    Criteria.where("checkoutDate")
                                                            .is(last24HoursStartDate)
                                            )
                            ),
                            CompletedOrder.class
                    );

            last24HoursSalesRupees = completedOrders.stream()
                    .filter(order -> {
                        try {

                            LocalDateTime orderDateTime =
                                    LocalDateTime.parse(
                                            order.getCheckoutDate()
                                                    + "T"
                                                    + order.getCheckoutTime()
                                    );

                            ZonedDateTime orderTimeIST =
                                    orderDateTime.atZone(istZone);

                            return !orderTimeIST.isBefore(last24HoursStart)
                                    && !orderTimeIST.isAfter(nowIST);

                        } catch (Exception ex) {
                            return false;
                        }
                    })
                    .mapToDouble(order ->
                            order.getTotalPayable() != null
                                    ? order.getTotalPayable()
                                    : 0.0
                    )
                    .sum();

        } catch (Exception e) {

            log.error(
                    "CREDIT_DASHBOARD: Last 24 hours sales calculation failed for hotel {}",
                    hotelId,
                    e
            );
        }

        // --------------------------------------------------------
        // Return only the requested dashboard fields
        // --------------------------------------------------------

        return CreditBillDashboardStatsDTO.builder()
                .completedOrdersTodayCount(completedOrdersTodayCount)
                .homeDeliveriesCount(homeDeliveriesCount)
                .instantCount(instantCount)
                .last24HoursSalesRupees(
                        roundTo2Decimals(last24HoursSalesRupees)
                )
                .planType(planType)
                .restaurantAddress(admin.getRestaurantAddress())
                .restaurantContact(admin.getRestaurantContact())
                .restaurantLogo(admin.getRestaurantLogo())
                .restaurantName(restaurantName)
                .restaurantUpi(admin.getRestaurantUpi())
                .todaySalesRupees(
                        roundTo2Decimals(todaySalesRupees)
                )
                .build();
    }

    private Double roundTo2Decimals(Double value) {
        if (value == null) {
            return 0.0;
        }

        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }


// ============================================================
// MAP CUSTOMER TO LIST RESPONSE
// ============================================================

    private CreditCustomerResponse toCustomerListResponse(
            InstantCreditCustomer customer
    ) {
        List<InstantCreditCustomer.CreditEntry> activeBills =
                getActiveBillEntries(customer);

        CreditCustomerResponse response =
                new CreditCustomerResponse();

        response.setCustomerId(customer.getId());
        response.setName(customer.getName());
        response.setMobile(customer.getMobile());
        response.setAddress(customer.getAddress());

        response.setActiveBillCount(activeBills.size());

        response.setTotalPendingAmount(
                money(
                        activeBills.stream()
                                .mapToDouble(entry ->
                                        entry.getBill().getPendingAmount()
                                )
                                .sum()
                )
        );

        return response;
    }


    // ============================================================
    // CREATE CREDIT BILL
    // ============================================================


    public CreditBillResponse createCreditBill(
            String hotelId,
            CreateCreditBillRequest request,
            String actualLoggedInUser
    ) {

        validateAmount(
                request.getTotalAmount(),
                "Total amount"
        );

        validateAmount(
                request.getPaidAmount(),
                "Paid amount"
        );

        // Read GST values provided by frontend.

        // Read GST values provided by frontend.
        double grossTotal =
                request.getGrossTotal() == null
                        ? 0.0
                        : money(request.getGrossTotal());

        Map<String, Double> gstDetails =
                request.getGstDetails() != null
                        ? new HashMap<>(request.getGstDetails())
                        : new HashMap<>();

        double totalAmount =
                money(request.getTotalAmount());


        double paidAmount =
                money(request.getPaidAmount());

        // Validate gross total.
        if (grossTotal < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Gross total cannot be negative"
            );
        }


        // Validate GST rates.
        if (gstDetails != null) {
            for (Map.Entry<String, Double> gstEntry : gstDetails.entrySet()) {

                String gstName = gstEntry.getKey();
                Double percentage = gstEntry.getValue();

                if (gstName == null || gstName.isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "GST name cannot be empty"
                    );
                }

                if (percentage == null
                        || percentage < 0
                        || percentage > 100) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            gstName + " must be between 0 and 100"
                    );
                }
            }
        }


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

        // Save frontend-provided GST amounts and rates.
        bill.setGrossTotal(grossTotal);

        bill.setGstDetails(
                request.getGstDetails() != null
                        ? new HashMap<>(request.getGstDetails())
                        : new HashMap<>()
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

        bill.setCreatedById(actualLoggedInUser);
        bill.setUpdatedById(actualLoggedInUser);

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

        // Add the sale to completed_orders exactly once.
        syncCreditBillToCompletedOrders(
                hotelId,
                customer,
                bill,
                actualLoggedInUser
        );

        // Update daily credit bill analytics
        String summaryDate = getCreditSummaryDate(bill);

        updateCreditBillSummary(
                hotelId,
                summaryDate,
                bill.getTotalAmount(),    // Credit bill sales increase
                bill.getPendingAmount(),  // Outstanding balance increase
                bill.getPaidAmount()      // Include initial payment received today
        );

        syncCreditBillItemSummary(
                hotelId,
                getCreditSummaryDate(bill),
                bill.getBillId(),
                bill.getCommodities()
        );

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

    public ReceivePaymentResponse receivePayment(String hotelId, String customerId, ReceivePaymentRequest request, String actualLoggedInUser) {

        if (request == null || request.getAmountReceived() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount is required");
        }

        validateAmount(request.getAmountReceived(), "Payment amount");

        double amountReceived = money(request.getAmountReceived());

        if (amountReceived <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount must be greater than zero");
        }

        InstantCreditCustomer customer = validateCustomer(hotelId, customerId);

        List<InstantCreditCustomer.CreditEntry> activeBills = getActiveBillEntries(customer);

        if (activeBills.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer has no pending bills");
        }

        double totalPending = calculateTotalPending(customer);

        if (amountReceived > totalPending) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount cannot be greater than total pending amount");
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

        Set<String> affectedBillIds = new HashSet<>();

        // --------------------------------------------------------
        // Apply payment bill by bill
        // --------------------------------------------------------

        String today = LocalDate.now(INDIA_ZONE).toString();

        double pendingTodayDelta = 0.0;

        for (InstantCreditCustomer.CreditEntry entry :
                activeBills) {

            if (remainingPayment <= 0) {
                break;
            }

            InstantCreditCustomer.CreditBill bill =
                    entry.getBill();

            double previousBillPending =
                    money(bill.getPendingAmount());

            String originalBillDate =
                    getCreditSummaryDate(bill);

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

            if (applied > 0) {
                affectedBillIds.add(bill.getBillId());
            }

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


            if (applied > 0 && originalBillDate.equals(
                    LocalDate.now(INDIA_ZONE).toString())) {

                pendingTodayDelta += money(
                        bill.getPendingAmount() - previousBillPending
                );
            }


            bill.setUpdatedAt(
                    paymentTime
            );

            bill.setUpdatedById(actualLoggedInUser);

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

            billPayment.setReceivedById(actualLoggedInUser);

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

        payment.setReceivedById(actualLoggedInUser);

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

        for (InstantCreditCustomer.CreditEntry entry : activeBills) {
            if (entry.getBill() != null
                    && affectedBillIds.contains(entry.getBill().getBillId())) {

                syncCreditBillPaymentToCompletedOrder(
                        hotelId,
                        entry.getBill()
                );
            }
        }


        updateCreditBillSummary(
                hotelId,
                today,
                0.0,                  // Do not count another sale
                money(pendingTodayDelta),
                amountReceived        // Payment received today
        );


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
            EditCreditBillRequest request,
            String actualLoggedInUser
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


        /*
         * Validate frontend-provided GST values.
         * GST percentages are stored as supplied by the frontend.
         * The backend does not calculate GST amounts.
         */

        Double grossTotal = request.getGrossTotal();
        Map<String, Double> gstDetails = request.getGstDetails();

        if (grossTotal != null && grossTotal < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Gross total cannot be negative"
            );
        }

        if (gstDetails != null) {
            for (Map.Entry<String, Double> gstEntry : gstDetails.entrySet()) {

                String gstName = gstEntry.getKey();
                Double percentage = gstEntry.getValue();

                if (gstName == null || gstName.isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "GST name cannot be empty"
                    );
                }

                if (percentage == null
                        || percentage < 0
                        || percentage > 100) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            gstName + " must be between 0 and 100"
                    );
                }
            }
        }

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

        double previousTotalAmount = bill.getTotalAmount();

        double previousPaidAmount = bill.getPaidAmount();

        double previousPendingAmount = bill.getPendingAmount();

        String billDateBeforeEdit =
                getCreditSummaryDate(bill);

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

        edit.setEditedById(actualLoggedInUser);

        // --------------------------------------------------------
        // Update bill
        // --------------------------------------------------------


        // Update GST fields only when supplied by the frontend.
        if (request.getGrossTotal() != null) {
            bill.setGrossTotal(
                    money(request.getGrossTotal())
            );
        }

        if (request.getGstDetails() != null) {
            bill.setGstDetails(
                    new HashMap<>(request.getGstDetails())
            );
        }

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

        bill.setUpdatedById(actualLoggedInUser);

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

        // Update the existing completed sale rather than inserting another.
        syncCreditBillToCompletedOrders(
                hotelId,
                customer,
                bill,
                actualLoggedInUser
        );

        double totalDelta = money(
                bill.getTotalAmount() - previousTotalAmount
        );

        double pendingDelta = money(
                bill.getPendingAmount() - previousPendingAmount
        );

        double receivedDelta = money(
                bill.getPaidAmount() - previousPaidAmount
        );

        String today = LocalDate.now(INDIA_ZONE).toString();

        // Update sale and outstanding balance for the original bill date.
        updateCreditBillSummary(
                hotelId,
                billDateBeforeEdit,
                totalDelta,
                pendingDelta,
                0.0
        );

        // Update today's received-payment metric by the paid-amount difference.
        updateCreditBillSummary(
                hotelId,
                today,
                0.0,
                0.0,
                receivedDelta
        );

        syncCreditBillItemSummary(
                hotelId,
                getCreditSummaryDate(bill),
                bill.getBillId(),
                bill.getCommodities()
        );

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

            // ADDED: Read and validate item-level GST.
            double itemGst =
                    request.getItemGst() == null
                            ? 0.0
                            : request.getItemGst();

            if (itemGst < 0 || itemGst > 100) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Item GST must be between 0 and 100"
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

            // ADDED: Store item-level GST without recalculating totals.
            commodity.setItemGst(
                    itemGst
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

        // GST details
        response.setGrossTotal(
                money(
                        bill.getGrossTotal()
                )
        );


        response.setGstDetails(
                bill.getGstDetails() != null
                        ? new HashMap<>(bill.getGstDetails())
                        : new HashMap<>()
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

                // Item-level GST
                dto.setItemGst(
                        commodity.getItemGst()
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




    public ReceivePaymentResponse settleSpecificBill(
            String hotelId,
            String billId,
            SettleSpecificBillRequest request,
            String actualLoggedInUser
    ) {

        // --------------------------------------------------------
        // Validate request
        // --------------------------------------------------------

        if (request == null || request.getAmountReceived() == null) {
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
                money(request.getAmountReceived());

        if (amountReceived <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount must be greater than zero"
            );
        }

        // --------------------------------------------------------
        // Find customer and specified active bill
        // --------------------------------------------------------

        InstantCreditCustomer customer =
                findCustomerContainingBill(hotelId, billId);

        if (customer.getCreditEntries() == null) {
            customer.setCreditEntries(new ArrayList<>());
        }

        InstantCreditCustomer.CreditEntry billEntry =
                customer.getCreditEntries()
                        .stream()
                        .filter(entry ->
                                entry.getEntryType()
                                        == InstantCreditCustomer.EntryType.CREDIT_BILL
                                        && entry.getBill() != null
                                        && billId.equals(
                                        entry.getBill().getBillId()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Active credit bill not found"
                                )
                        );

        InstantCreditCustomer.CreditBill bill =
                billEntry.getBill();

        double previousPendingAmount =
                money(bill.getPendingAmount());

        String originalBillDate =
                getCreditSummaryDate(bill);

        String today =
                LocalDate.now(INDIA_ZONE).toString();

        double pendingAmount =
                money(bill.getPendingAmount());

        if (bill.isCompleted() || pendingAmount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This bill is already completed"
            );
        }

        if (amountReceived > pendingAmount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount cannot be greater than this bill's pending amount"
            );
        }

        // --------------------------------------------------------
        // Apply payment only to the specified bill
        // --------------------------------------------------------

        String paymentId =
                UUID.randomUUID().toString();

        LocalDateTime paymentTime =
                LocalDateTime.now();

        double updatedPaidAmount =
                money(
                        bill.getPaidAmount() + amountReceived
                );

        double updatedPendingAmount =
                money(
                        bill.getTotalAmount() - updatedPaidAmount
                );

        boolean billCompleted =
                updatedPendingAmount <= 0;

        bill.setPaidAmount(updatedPaidAmount);

        bill.setPendingAmount(
                billCompleted ? 0.0 : updatedPendingAmount
        );

        bill.setCompleted(billCompleted);
        bill.setUpdatedAt(paymentTime);
        bill.setUpdatedById(actualLoggedInUser);

        Set<String> completedBillIds =
                new HashSet<>();

        Set<String> affectedBillIds = new HashSet<>();

        if (billCompleted) {
            completedBillIds.add(bill.getBillId());
        }

        // --------------------------------------------------------
        // Create allocation for the specific bill
        // --------------------------------------------------------

        InstantCreditCustomer.PaymentAllocation billAllocation =
                new InstantCreditCustomer.PaymentAllocation();

        billAllocation.setBillId(bill.getBillId());
        billAllocation.setAmountApplied(amountReceived);

        // --------------------------------------------------------
        // Add bill-level payment record
        // Follow the same pattern as receivePayment()
        // --------------------------------------------------------

        InstantCreditCustomer.PaymentReceived billPayment =
                new InstantCreditCustomer.PaymentReceived();

        billPayment.setPaymentId(paymentId);
        billPayment.setAmountReceived(amountReceived);
        billPayment.setPaymentDate(LocalDate.now());
        billPayment.setReceivedAt(paymentTime);
        billPayment.setSettlementOrder(
                InstantCreditCustomer.SettlementOrder.OLDEST_FIRST
        );

        billPayment.setAllocations(
                new ArrayList<>(
                        List.of(billAllocation)
                )
        );

        billPayment.setReceivedById(actualLoggedInUser);

        if (bill.getPaymentReceived() == null) {
            bill.setPaymentReceived(new ArrayList<>());
        }

        bill.getPaymentReceived().add(billPayment);

        // --------------------------------------------------------
        // Create ledger allocation
        // --------------------------------------------------------

        InstantCreditCustomer.PaymentAllocation ledgerAllocation =
                new InstantCreditCustomer.PaymentAllocation();

        ledgerAllocation.setBillId(bill.getBillId());
        ledgerAllocation.setAmountApplied(amountReceived);

        List<InstantCreditCustomer.PaymentAllocation>
                ledgerAllocations = new ArrayList<>();

        ledgerAllocations.add(ledgerAllocation);

        // --------------------------------------------------------
        // Create separate chronological RECEIVE_PAYMENT record
        // Follow the same pattern as receivePayment()
        // --------------------------------------------------------

        InstantCreditCustomer.PaymentReceived ledgerPayment =
                new InstantCreditCustomer.PaymentReceived();

        ledgerPayment.setPaymentId(paymentId);
        ledgerPayment.setAmountReceived(amountReceived);
        ledgerPayment.setPaymentDate(LocalDate.now());
        ledgerPayment.setReceivedAt(paymentTime);
        ledgerPayment.setSettlementOrder(
                InstantCreditCustomer.SettlementOrder.OLDEST_FIRST
        );

        ledgerPayment.setAllocations(ledgerAllocations);

        ledgerPayment.setReceivedById(actualLoggedInUser);

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

        paymentEntry.setTransactionDate(LocalDate.now());
        paymentEntry.setTransactionTime(paymentTime);
        paymentEntry.setBill(null);
        paymentEntry.setPayment(ledgerPayment);

        customer.getCreditEntries().add(paymentEntry);

        // --------------------------------------------------------
        // Move completed bill and eligible payment to history
        // --------------------------------------------------------

        if (!completedBillIds.isEmpty()) {
            moveCompletedBillsAndPayments(
                    customer,
                    completedBillIds
            );
        }

        // --------------------------------------------------------
        // Update customer and save
        // --------------------------------------------------------

        customer.setActive(
                hasActivePendingBills(customer)
        );

        customer.setUpdatedAt(paymentTime);

        saveCompletedHistory(customer);

        customerRepository.save(customer);

        syncCreditBillPaymentToCompletedOrder(
                hotelId,
                bill
        );


        double pendingDelta = money(
                bill.getPendingAmount() - previousPendingAmount
        );

        updateCreditBillSummary(
                hotelId,
                today,
                0.0, // Do not count another sale
                originalBillDate.equals(today) ? pendingDelta : 0.0,
                amountReceived
        );


        // --------------------------------------------------------
        // Build response
        // --------------------------------------------------------

        ReceivePaymentResponse response =
                new ReceivePaymentResponse();

        response.setPaymentId(paymentId);
        response.setAmountReceived(amountReceived);
        response.setAmountApplied(amountReceived);
        response.setUnappliedAmount(0.0);

        response.setTotalPendingAmount(
                calculateTotalPending(customer)
        );

        ReceivePaymentResponse.Allocation responseAllocation =
                new ReceivePaymentResponse.Allocation();

        responseAllocation.setBillId(
                bill.getBillId()
        );

        responseAllocation.setAmountApplied(
                amountReceived
        );

        responseAllocation.setRemainingPendingAmount(
                billCompleted ? 0.0 : updatedPendingAmount
        );

        responseAllocation.setBillCompleted(
                billCompleted
        );

        response.setAllocations(
                new ArrayList<>(
                        List.of(responseAllocation)
                )
        );

        return response;
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

    private static final ZoneId INDIA_ZONE =
            ZoneId.of("Asia/Kolkata");

    private String getCreditSummaryDate(
            InstantCreditCustomer.CreditBill bill
    ) {
        if (bill != null && bill.getBillDate() != null) {
            return bill.getBillDate().toString();
        }

        return LocalDate.now(INDIA_ZONE).toString();
    }

    private String getStaffSummaryId(
            String hotelId,
            String date
    ) {
        return hotelId + "_" + date + "_STAFF";
    }

    private void updateCreditBillSummary(
            String hotelId,
            String date,
            double billTodayDelta,
            double pendingTodayDelta,
            double receivedTodayDelta
    ) {
        String documentId = getStaffSummaryId(hotelId, date);

        Query query = new Query(
                Criteria.where("_id").is(documentId)
        );

        Update update = new Update()
                .inc(
                        "creditBillSummary.creditBillToday",
                        billTodayDelta
                )
                .inc(
                        "creditBillSummary.creditPendingToday",
                        pendingTodayDelta
                )
                .inc(
                        "creditBillSummary.creditReceivedToday",
                        receivedTodayDelta
                )
                .setOnInsert("restaurantId", hotelId)
                .setOnInsert("date", date);

        mongoTemplate.upsert(
                query,
                update,
                "daily_staff_summary"
        );
    }

    private CompletedOrder syncCreditBillToCompletedOrders(
            String hotelId,
            InstantCreditCustomer customer,
            InstantCreditCustomer.CreditBill bill,
            String actualLoggedInUser
    ) {
        if (bill == null || bill.getBillId() == null) {
            throw new IllegalArgumentException(
                    "Credit bill ID is required"
            );
        }

        CompletedOrder order = completeOrderRepository
                .findByHotelIdAndCreditBillId(
                        hotelId,
                        bill.getBillId()
                )
                .orElseGet(CompletedOrder::new);

        boolean isNewSale = order.getId() == null;

        List<OrderItem> items = new ArrayList<>();

        if (bill.getCommodities() != null) {
            for (InstantCreditCustomer.CreditCommodity commodity
                    : bill.getCommodities()) {

                if (commodity == null) {
                    continue;
                }

                items.add(
                        OrderItem.builder()
                                .itemId(commodity.getId())
                                .itemName(commodity.getName())
                                .quantity(String.valueOf(commodity.getQuantity()))
                                .unit(commodity.getUnit())
                                .price(commodity.getRate())
                                .subTotal(commodity.getSubtotal())
                                .gst(String.valueOf(commodity.getItemGst()))
                                .build()
                );
            }
        }

        LocalDate billDate = bill.getBillDate() != null
                ? bill.getBillDate()
                : LocalDate.now(ZoneId.of("Asia/Kolkata"));

        LocalDateTime createdAt = bill.getCreatedAt() != null
                ? bill.getCreatedAt()
                : LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

        String checkoutTime = createdAt.format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
        );

        KitchenOrderSnapshot snapshot = KitchenOrderSnapshot.builder()
                .id(bill.getBillId())
                .items(items)
                .totalAmount(bill.getTotalAmount())
                .status("COMPLETED")
                .placedBy(actualLoggedInUser)
                .completedBy(actualLoggedInUser)
                .createdDate(billDate.toString())
                .createdTime(checkoutTime)
                .build();

        order.setHotelId(hotelId);
        order.setCreditBillId(bill.getBillId());
        order.setOrderType("INSTANT");

        order.setCustomerName(customer.getName());
        order.setCustomerMobile(customer.getMobile());
        order.setCustomerAddress(customer.getAddress());

        order.setAllOrders(Collections.singletonList(snapshot));

        order.setGrandTotal(bill.getGrossTotal());

        order.setTotalPayable(bill.getTotalAmount());

        order.setPaidAmount(bill.getPaidAmount());
        order.setPendingAmount(bill.getPendingAmount());
        order.setBillDate(billDate);

        order.setPaymentStatus(
                bill.getPendingAmount() <= 0
                        ? "PAID"
                        : "CREDIT"
        );

        // Preserve the original checkout date and operator on edits.
        if (isNewSale) {
            order.setCheckoutBy(actualLoggedInUser);
            order.setCheckoutDate(billDate.toString());
            order.setCheckoutTime(checkoutTime);
        }

        // GST values here are percentages, not collected tax amounts.
        // Do not treat them as monetary tax values.
        List<Map<String, Object>> gstDetails = new ArrayList<>();

        if (bill.getGstDetails() != null
                && !bill.getGstDetails().isEmpty()) {

            Map<String, Object> percentages = new HashMap<>();

            bill.getGstDetails().forEach((key, value) ->
                    percentages.put(key + "Percent", value)
            );

            gstDetails.add(percentages);
        }

        order.setGsttype(gstDetails);

        return completeOrderRepository.save(order);
    }

    private void syncCreditBillPaymentToCompletedOrder(
            String hotelId,
            InstantCreditCustomer.CreditBill bill
    ) {
        if (bill == null || bill.getBillId() == null) {
            return;
        }

        Optional<CompletedOrder> existingOrder =
                completeOrderRepository.findByHotelIdAndCreditBillId(
                        hotelId,
                        bill.getBillId()
                );

        if (existingOrder.isEmpty()) {
            log.warn(
                    "No completed_orders record found for credit bill {} in hotel {}",
                    bill.getBillId(),
                    hotelId
            );
            return;
        }

        CompletedOrder order = existingOrder.get();

        order.setPaidAmount(bill.getPaidAmount());
        order.setPendingAmount(bill.getPendingAmount());

        order.setPaymentStatus(
                bill.getPendingAmount() <= 0
                        ? "PAID"
                        : "CREDIT"
        );

        completeOrderRepository.save(order);

        log.info(
                "Synced credit bill {}: paid={}, pending={}",
                bill.getBillId(),
                bill.getPaidAmount(),
                bill.getPendingAmount()
        );
    }


    private double getCommodityQuantity(
            InstantCreditCustomer.CreditCommodity commodity
    ) {
        return commodity.getQuantity();
    }

    private Map<String, Document> aggregateCreditCommodities(
            List<InstantCreditCustomer.CreditCommodity> commodities
    ) {
        Map<String, Document> result = new LinkedHashMap<>();

        if (commodities == null) {
            return result;
        }

        for (InstantCreditCustomer.CreditCommodity commodity : commodities) {
            if (commodity == null
                    || commodity.getId() == null
                    || commodity.getId().trim().isEmpty()) {
                continue;
            }

            String itemId = commodity.getId().trim();
            double qty = getCommodityQuantity(commodity);

            Document existing = result.get(itemId);

            if (existing == null) {
                result.put(
                        itemId,
                        new Document("itemId", itemId)
                                .append(
                                        "name",
                                        commodity.getName() != null
                                                ? commodity.getName()
                                                : "Unknown Item"
                                )
                                .append(
                                        "unit",
                                        commodity.getUnit() != null
                                                ? commodity.getUnit()
                                                : "PIECE"
                                )
                                .append("qty", qty)
                );
            } else {
                existing.put(
                        "qty",
                        ((Number) existing.get("qty")).doubleValue() + qty
                );
            }
        }

        return result;
    }

    private void syncCreditBillItemSummary(
            String hotelId,
            String billDate,
            String billId,
            List<InstantCreditCustomer.CreditCommodity> newCommodities
    ) {
        if (hotelId == null || billDate == null || billId == null) {
            throw new IllegalArgumentException(
                    "Hotel ID, bill date and bill ID are required"
            );
        }

        String itemDocId = hotelId + "_" + billDate + "_ITEMS";

        Query query = new Query(
                Criteria.where("_id").is(itemDocId)
        );

        Document itemDoc = mongoTemplate.findById(
                itemDocId,
                Document.class,
                "daily_item_summary"
        );

        Map<String, Document> newItems =
                aggregateCreditCommodities(newCommodities);

        List<Document> snapshots = itemDoc == null
                ? new ArrayList<>()
                : itemDoc.getList(
                "creditBillSnapshots",
                Document.class,
                new ArrayList<>()
        );

        Document oldSnapshot = snapshots.stream()
                .filter(s -> billId.equals(s.getString("billId")))
                .findFirst()
                .orElse(null);

        Map<String, Document> oldItems = new LinkedHashMap<>();

        if (oldSnapshot != null) {
            List<Document> oldSnapshotItems =
                    oldSnapshot.getList("items", Document.class);

            if (oldSnapshotItems != null) {
                for (Document item : oldSnapshotItems) {
                    if (item.getString("itemId") != null) {
                        oldItems.put(item.getString("itemId"), item);
                    }
                }
            }
        }

        Set<String> affectedItemIds = new LinkedHashSet<>();
        affectedItemIds.addAll(oldItems.keySet());
        affectedItemIds.addAll(newItems.keySet());

        double totalQtyDelta = 0.0;

        for (String itemId : affectedItemIds) {
            Document oldItem = oldItems.get(itemId);
            Document newItem = newItems.get(itemId);

            double oldQty = oldItem == null
                    ? 0.0
                    : ((Number) oldItem.get("qty")).doubleValue();

            double newQty = newItem == null
                    ? 0.0
                    : ((Number) newItem.get("qty")).doubleValue();

            double delta = newQty - oldQty;

            totalQtyDelta += delta;

            if (Math.abs(delta) < 0.000001) {
                continue;
            }

            Query existingItemQuery = new Query(
                    Criteria.where("_id").is(itemDocId)
                            .and("items.itemId").is(itemId)
            );

            Update itemUpdate = new Update()
                    .inc("totalQtySold", delta)
                    .inc("items.$.qty", delta);

            var result = mongoTemplate.updateFirst(
                    existingItemQuery,
                    itemUpdate,
                    "daily_item_summary"
            );

            if (result.getMatchedCount() > 0) {
                Query updatedItemQuery = new Query(
                        Criteria.where("_id").is(itemDocId)
                                .and("items.itemId").is(itemId)
                );

                Document updatedItemDoc = mongoTemplate.findOne(
                        updatedItemQuery,
                        Document.class,
                        "daily_item_summary"
                );

                if (updatedItemDoc != null) {
                    List<Document> updatedItems =
                            updatedItemDoc.getList("items", Document.class);

                    if (updatedItems != null) {
                        for (Document updatedItem : updatedItems) {
                            if (itemId.equals(updatedItem.getString("itemId"))) {
                                Object qtyValue = updatedItem.get("qty");

                                if (qtyValue instanceof Number
                                        && ((Number) qtyValue).doubleValue() <= 0.000001) {

                                    Query removeItemQuery = new Query(
                                            Criteria.where("_id").is(itemDocId)
                                    );

                                    Update removeItemUpdate = new Update()
                                            .pull(
                                                    "items",
                                                    new Document("itemId", itemId)
                                            );

                                    mongoTemplate.updateFirst(
                                            removeItemQuery,
                                            removeItemUpdate,
                                            "daily_item_summary"
                                    );
                                }

                                break;
                            }
                        }
                    }
                }
            }

            if (result.getMatchedCount() == 0 && newQty > 0.0) {
                Document itemToAdd = new Document(
                        "itemId", itemId
                ).append(
                        "name", newItem.getString("name")
                ).append(
                        "unit", newItem.getString("unit")
                ).append(
                        "qty", newQty
                );

                Query baseQuery = new Query(
                        Criteria.where("_id").is(itemDocId)
                );

                Update addItem = new Update()
                        .setOnInsert("restaurantId", hotelId)
                        .setOnInsert("date", billDate)
                        .inc("totalQtySold", newQty)
                        .push("items", itemToAdd);

                mongoTemplate.upsert(
                        baseQuery,
                        addItem,
                        "daily_item_summary"
                );
            }
        }

        // Replace this bill's snapshot with the latest commodities.
        snapshots.removeIf(
                s -> billId.equals(s.getString("billId"))
        );

        List<Document> snapshotItems = new ArrayList<>();

        for (Document item : newItems.values()) {
            snapshotItems.add(new Document(item));
        }

        snapshots.add(
                new Document("billId", billId)
                        .append("items", snapshotItems)
        );

        Query baseQuery = new Query(
                Criteria.where("_id").is(itemDocId)
        );

        Update snapshotUpdate = new Update()
                .set("creditBillSnapshots", snapshots)
                .setOnInsert("restaurantId", hotelId)
                .setOnInsert("date", billDate);

        if (itemDoc == null) {
            snapshotUpdate.setOnInsert("totalQtySold", totalQtyDelta);
            snapshotUpdate.setOnInsert(
                    "items",
                    new ArrayList<Document>()
            );
        }

        mongoTemplate.upsert(
                baseQuery,
                snapshotUpdate,
                "daily_item_summary"
        );
    }

}