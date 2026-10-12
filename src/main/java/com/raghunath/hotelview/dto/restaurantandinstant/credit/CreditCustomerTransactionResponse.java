package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCustomer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditCustomerTransactionResponse {

    // Bill fields
    private String billId;
    private String customerId;
    private LocalDate billDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double totalAmount;
    private Double paidAmount;
    private Double pendingAmount;
    private Boolean completed;
    private List<CommodityResponse> commodities;
    private List<InstantCreditCustomer.PaymentReceived> paymentReceived;
    private List<InstantCreditCustomer.CreditBillEdit> editHistory;

    // Ledger transaction fields
    private String entryId;
    private Long sequenceNumber;
    private InstantCreditCustomer.EntryType entryType;
    private LocalDate transactionDate;
    private LocalDateTime transactionTime;

    // Populated for RECEIVE_PAYMENT entries
    private InstantCreditCustomer.PaymentReceived payment;
}
