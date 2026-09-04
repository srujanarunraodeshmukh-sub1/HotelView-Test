package com.raghunath.hotelview.dto.buyandsell;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyncInfoDTO {
    private long version;
    private long count;
}