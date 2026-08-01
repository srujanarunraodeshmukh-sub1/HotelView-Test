package com.raghunath.hotelview.dto.admin;

import com.raghunath.hotelview.dto.ReceiptGstToggleDTO;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptSettingsDTO {
    private boolean printRestaurantName;
    private boolean printBusinessAddress;
    private boolean printCustomerNumber;
    private boolean includeRestaurantLogo;
    private boolean printDateTime;
    private boolean showOrderTypeLabel;
    private boolean displayOrderId;
    private boolean showCustomerDetails;
    private boolean printUpiAndQr;
    private boolean printGreetingNote;
    private boolean printItemQuantities;
    private boolean printGstNumber;
    private List<ReceiptGstToggleDTO> gstDetails;
}