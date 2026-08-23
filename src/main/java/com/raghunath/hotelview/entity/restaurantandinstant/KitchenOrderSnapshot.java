package com.raghunath.hotelview.entity.restaurantandinstant;

import com.raghunath.hotelview.dto.restaurantandinstant.OrderItem;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KitchenOrderSnapshot {
    private String id;
    private List<OrderItem> items;
    private Double totalAmount;
    private String status;
    private String comments;
    private String placedBy;
    private String acceptedBy;
    private String completedBy;
    private LocalDateTime updatedAt;
    private String createdDate;
    private String createdTime;
}