package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreditCustomerDetailsResponse {

    private String customerId;
    private String name;
    private String mobile;
    private String address;

    private int activeBillCount;
    private double totalPendingAmount;

    private List<CreditBillResponse> activeBills =
            new ArrayList<>();

    private List<CreditHistoryResponse> transactions =
            new ArrayList<>();
}
