package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCustomer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class CreditHistoryResponse {

    private String entryId;

    private Long sequenceNumber;

    private InstantCreditCustomer.EntryType entryType;

    private LocalDate transactionDate;

    private LocalDateTime transactionTime;

    /*
     * Present when entryType = CREDIT_BILL
     */
    private CreditBillResponse bill;

    /*
     * Present when entryType = RECEIVE_PAYMENT
     */
    private InstantCreditCustomer.PaymentReceived payment;
}