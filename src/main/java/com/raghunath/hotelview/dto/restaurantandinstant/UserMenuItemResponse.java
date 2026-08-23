package com.raghunath.hotelview.dto.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.MenuItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMenuItemResponse implements Serializable {

    private String id;
    private String hotelId;
    private String category;
    private String name;
    private String shortCode;
    private String description;
    private BigDecimal price;
    private String quantity;
    private String unit;
    private Boolean isVeg;
    private Boolean isAvailable;
    private String imageUrl;
    private Integer preparationTime;

    // Helper mapper from MenuItem entity to DTO
    public static UserMenuItemResponse fromEntity(MenuItem entity) {
        return UserMenuItemResponse.builder()
                .id(entity.getId())
                .hotelId(entity.getHotelId())
                .category(entity.getCategory())
                .name(entity.getName())
                .shortCode(entity.getShortCode())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .quantity(entity.getQuantity())
                .unit(entity.getUnit())
                .isVeg(entity.getIsVeg())
                .isAvailable(entity.getIsAvailable())
                .imageUrl(entity.getImageUrl())
                .preparationTime(entity.getPreparationTime())
                .build();
    }
}