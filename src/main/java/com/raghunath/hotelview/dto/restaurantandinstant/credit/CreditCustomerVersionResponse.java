package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreditCustomerVersionResponse {

    private String version;
    private long totalCount;
}
