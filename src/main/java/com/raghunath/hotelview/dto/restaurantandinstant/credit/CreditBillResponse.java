package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCustomer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreditBillResponse {

    private String billId;

    private String customerId;

    private LocalDate billDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private double totalAmount;

    private double paidAmount;

    private double pendingAmount;

    private boolean completed;

    private List<CommodityResponse> commodities =
            new ArrayList<>();

    private List<InstantCreditCustomer.PaymentReceived>
            paymentReceived =
            new ArrayList<>();

    private List<InstantCreditCustomer.CreditBillEdit>
            editHistory =
            new ArrayList<>();
}