package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCustomer.SettlementOrder;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReceivePaymentRequest {

    @NotNull(message = "Amount received is required")
    @Positive(message = "Amount received must be greater than zero")
    private Double amountReceived;

    @NotNull(message = "Settlement order is required")
    private SettlementOrder settlementOrder;
}