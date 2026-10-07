package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreditCustomerResponse {

    private String customerId;

    private String name;

    private String mobile;

    private String address;

    private int activeBillCount;

    private double totalPendingAmount;
}