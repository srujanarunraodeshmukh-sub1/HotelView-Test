package com.raghunath.hotelview.entity.restaurantandinstant;

import com.raghunath.hotelview.dto.restaurantandinstant.GstDetailDTO;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "admins")
public class Admin {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String mobile;

    private String alternateMobile;

    private String password;

    private List<String> email;

    private String address;

    private String hotelId;

    private String userType;

    private boolean isActive;

    private boolean isApproved;

    private int maxLogins = 1;

    private String planType;

    private LocalDateTime subscriptionStart;
    private LocalDateTime subscriptionExpiry;

    private String restaurantName;
    private String restaurantAddress;
    private String restaurantContact;
    private String restaurantLogo;
    private String restaurantUpi;

    private String gstNumber;
    private String gstTaxMode;
    private List<GstDetailDTO> gstDetails;

    private String fssaiNumber;

    private List<BankAccount> bankAccounts;

    private String privacyPolicyStatus;
    private java.time.LocalDateTime privacyPolicySignedAt;

    private String termsAndConditionsStatus;
    private java.time.LocalDateTime termsSignedAt;

    private String greeting;

    private Map<String, String> platformIds = new HashMap<>();

    private Map<String, Boolean> integrationStatus = new HashMap<>();
}