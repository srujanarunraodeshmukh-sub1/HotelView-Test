package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ReceivePaymentResponse {

    private String paymentId;

    private double amountReceived;

    private double amountApplied;

    private double unappliedAmount;

    private double totalPendingAmount;

    private List<Allocation> allocations = new ArrayList<>();


    // ============================================================
    // PAYMENT ALLOCATION RESPONSE
    // ============================================================

    @Getter
    @Setter
    public static class Allocation {

        private String billId;

        private double amountApplied;

        private double remainingPendingAmount;

        private boolean billCompleted;
    }
}