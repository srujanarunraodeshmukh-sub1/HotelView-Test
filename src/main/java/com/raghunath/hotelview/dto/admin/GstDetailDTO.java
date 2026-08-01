package com.raghunath.hotelview.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GstDetailDTO {
    private String gstType;
    private Double gstPercent;
}
