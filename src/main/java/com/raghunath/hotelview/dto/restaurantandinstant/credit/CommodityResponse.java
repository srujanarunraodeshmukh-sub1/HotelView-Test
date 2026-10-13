package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommodityResponse {

    private String id;

    private String name;

    private double rate;

    private double itemGst;

    private double quantity;

    private String unit;

    private double subtotal;
}