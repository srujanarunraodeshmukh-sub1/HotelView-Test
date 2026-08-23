package com.raghunath.hotelview.entity.restaurantandinstant;

import com.raghunath.hotelview.dto.restaurantandinstant.ReceiptGstToggleDTO;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "receipt_settings")
public class ReceiptSettings {

    @Id
    private String id;
    private String hotelId;
    private String receiptType; // "TABLE_HOME" or "INSTANT"

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

    private boolean printGstNumber;
    private boolean applyItemLevelGst;
    private List<ReceiptGstToggleDTO> gstDetails;
    private boolean printItemQuantities;
}