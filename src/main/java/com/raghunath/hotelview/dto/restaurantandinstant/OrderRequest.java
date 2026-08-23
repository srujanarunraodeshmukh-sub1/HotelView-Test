package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    private List<OrderItem> items;
    private String comment;
}