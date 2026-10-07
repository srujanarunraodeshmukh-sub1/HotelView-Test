package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PendingTransactionResponse {

    private String billId;

    private String customerId;

    private String customerName;

    private String customerMobile;

    private LocalDate billDate;

    private double totalAmount;

    private double paidAmount;

    private double pendingAmount;
}