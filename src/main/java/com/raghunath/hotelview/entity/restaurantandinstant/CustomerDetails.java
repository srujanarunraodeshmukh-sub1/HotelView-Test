package com.raghunath.hotelview.entity.restaurantandinstant;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customer_details")
public class CustomerDetails {
    @Id
    private String id;
    private String hotelId;
    private String customerName;
    private String customerMobile;
    private String customerAddress;
    private Integer totalOrders;
    private Double totalAmountPaid;
    private String lastOrderDate;
}