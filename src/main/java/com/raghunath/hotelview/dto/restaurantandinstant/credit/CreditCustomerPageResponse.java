package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CreditCustomerPageResponse {

    private String version;
    private long totalCount;
    private int page;
    private int size;
    private boolean hasNext;
    private List<CreditCustomerResponse> customers;
}
