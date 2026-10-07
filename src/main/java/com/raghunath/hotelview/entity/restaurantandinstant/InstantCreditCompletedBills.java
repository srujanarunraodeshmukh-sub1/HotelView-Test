package com.raghunath.hotelview.entity.restaurantandinstant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "instant_credit_completed_bills")
@CompoundIndex(
        name = "hotel_customer_unique_idx",
        def = "{'hotelId': 1, 'customerId': 1}",
        unique = true
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstantCreditCompletedBills {

    @Id
    private String id;

    private String hotelId;

    private String customerId;

    private String customerName;

    private String customerMobile;

    private String customerAddress;

    /*
     * Contains completed CREDIT_BILL and RECEIVE_PAYMENT
     * entries in their original chronological order.
     */
    @Builder.Default
    private List<InstantCreditCustomer.CreditEntry> completedEntries =
            new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}