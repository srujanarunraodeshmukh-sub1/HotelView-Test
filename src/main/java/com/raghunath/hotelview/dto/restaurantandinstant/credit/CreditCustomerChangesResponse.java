package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CreditCustomerChangesResponse {

    private String version;
    private List<CreditCustomerResponse> customers;
}
