package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountDTO {

    private String bankName;
    private String accountNumber;
    private String ifscCode;
}