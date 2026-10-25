package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDetailsDTO {
    private String restaurantName;
    private String restaurantAddress;
    private String restaurantContact;
    private String restaurantLogo;
    private String restaurantUpi;
    private String planType;
    private String gstNumber;
    private String gstTaxMode;
    private List<GstDetailDTO> gstDetails;
    private String fssaiNumber;
    private List<BankAccountDTO> bankAccounts;
    private List<String> email;
}