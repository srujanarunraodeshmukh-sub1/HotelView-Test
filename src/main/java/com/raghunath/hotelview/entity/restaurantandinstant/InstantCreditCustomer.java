package com.raghunath.hotelview.entity.restaurantandinstant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Document(collection = "instant_credit_customers")
@CompoundIndex(
        name = "hotel_mobile_unique",
        def = "{'hotelId': 1, 'mobile': 1}",
        unique = true
)
public class InstantCreditCustomer {

    @Id
    private String id;

    private String hotelId;

    private String name;

    private String mobile;

    private String address;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private boolean active;

    /*
     * This number NEVER resets.
     *
     * Every new transaction gets:
     * lastSequenceNumber + 1
     *
     * Even when an entry moves to completed history,
     * its original sequence number remains unchanged.
     */
    private Long lastSequenceNumber = 0L;

    /*
     * Only currently active/unsettled transactions remain here.
     */
    private List<CreditEntry> creditEntries = new ArrayList<>();


    // ============================================================
    // ENTRY TYPE
    // ============================================================

    public enum EntryType {
        CREDIT_BILL,
        RECEIVE_PAYMENT
    }


    // ============================================================
    // SETTLEMENT ORDER
    // ============================================================

    public enum SettlementOrder {
        OLDEST_FIRST,
        NEWEST_FIRST
    }


    // ============================================================
    // CREDIT ENTRY
    // ============================================================

    @Getter
    @Setter
    public static class CreditEntry {

        private String entryId;

        private Long sequenceNumber;

        private EntryType entryType;

        private LocalDate transactionDate;

        private LocalDateTime transactionTime;

        /*
         * Filled when entryType = CREDIT_BILL
         */
        private CreditBill bill;

        /*
         * Filled when entryType = RECEIVE_PAYMENT
         */
        private PaymentReceived payment;
    }


    // ============================================================
    // CREDIT BILL
    // ============================================================

    @Getter
    @Setter
    public static class CreditBill {

        private String billId;

        private LocalDate billDate;

        private LocalDateTime createdAt;

        private LocalDateTime updatedAt;

        private double grossTotal;

        private Map<String, Double> gstAmounts;

        private Map<String, Double> gstDetails;

        private Map<String, Double> extraCharges = new HashMap<>();

        private double discount;

        private double totalAmount;

        private double paidAmount;

        private double pendingAmount;

        private boolean completed;

        private List<CreditCommodity> commodities =
                new ArrayList<>();

        private List<PaymentReceived> paymentReceived =
                new ArrayList<>();

        private List<CreditBillEdit> editHistory =
                new ArrayList<>();

        private String createdById;
        private String updatedById;
    }


    // ============================================================
    // CREDIT COMMODITY
    // ============================================================

    @Getter
    @Setter
    public static class CreditCommodity {

        private String id;

        private String name;

        private double rate;

        private double quantity;

        private double itemGst;

        private String unit;

        /*
         * Backend calculates:
         *
         * subtotal = rate × quantity
         */
        private double subtotal;
    }


    // ============================================================
    // PAYMENT RECEIVED
    // ============================================================

    @Getter
    @Setter
    public static class PaymentReceived {

        private String paymentId;

        /*
         * For a bill-level payment record this is the amount
         * applied to that bill.
         *
         * For a chronological RECEIVE_PAYMENT entry this
         * represents the amount represented by that entry.
         */
        private double amountReceived;

        private LocalDate paymentDate;

        private LocalDateTime receivedAt;

        private SettlementOrder settlementOrder;

        private List<PaymentAllocation> allocations =
                new ArrayList<>();

        private String receivedById;
    }


    // ============================================================
    // PAYMENT ALLOCATION
    // ============================================================

    @Getter
    @Setter
    public static class PaymentAllocation {

        private String billId;

        private double amountApplied;
    }


    // ============================================================
    // CREDIT BILL EDIT
    // ============================================================

    @Getter
    @Setter
    public static class CreditBillEdit {

        private String editId;

        private String billId;

        private double previousTotalAmount;

        private double previousPaidAmount;

        private double previousPendingAmount;

        private double updatedTotalAmount;

        private double updatedPaidAmount;

        private double updatedPendingAmount;

        private String editReason;

        private LocalDateTime editedAt;

        private String editedById;
    }
}