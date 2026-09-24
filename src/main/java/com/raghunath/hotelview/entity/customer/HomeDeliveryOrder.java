package com.raghunath.hotelview.entity.customer;

import com.raghunath.hotelview.dto.restaurantandinstant.OrderItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndex(name = "hotel_status", def = "{'hotelId': 1, 'status': 1}")
@Document(collection = "home_delivery_orders")
public class HomeDeliveryOrder {

    @Id
    private String id;

    @Field("hotelId")
    private String hotelId;

    @Field("orderType")
    @Builder.Default
    private String orderType = "HomeDelivery";

    private String customerId;
    private String customerName;
    private String customerMobile;
    private String customerAddress;

    private List<OrderItem> items;
    private Double totalAmount;
    private String comments;

    private String status; // PENDING, ACCEPTED, COMPLETED

    @Builder.Default
    private String assignedTo = "yetToAssign";

    private DeliveryBoy deliveryBoy;

    @Builder.Default
    private boolean delivered = false;

    private String placedBy;    // "CUSTOMER"
    private String acceptedBy;  // staffId who accepted
    private String completedBy; // staffId who completed / assigned

    private String createdDate;
    private String createdTime;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    private String deliveredByDeliveryBoyId; // nullable — set when a logged-in delivery boy marks it delivered

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeliveryBoy {
        private String name;
        private String mobile;
        private String vehicleNumber;
        private String staffId;
    }
}