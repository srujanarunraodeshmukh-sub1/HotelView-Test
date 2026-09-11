package com.raghunath.hotelview.dto.buyandsell;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateInsuranceRequest {
    private String clientId;
    private ClientInfoDTO client;
    private InsuranceInfoDTO insurance;
}
