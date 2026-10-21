package com.raghunath.hotelview.entity.restaurantandinstant;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {

    private String bankName;
    private String accountNumber;
    private String ifscCode;
}