package com.raghunath.hotelview.service.restaurantandinstantadmin;

import com.raghunath.hotelview.dto.restaurantandinstant.ReceiptGstToggleDTO;
import com.raghunath.hotelview.dto.restaurantandinstant.GstDetailDTO;
import com.raghunath.hotelview.dto.restaurantandinstant.ReceiptSettingsDTO;
import com.raghunath.hotelview.entity.restaurantandinstant.Admin;
import com.raghunath.hotelview.entity.restaurantandinstant.ReceiptSettings;
import com.raghunath.hotelview.repository.restaurantandinstant.AdminRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.ReceiptSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptSettingsService {

    private final ReceiptSettingsRepository repository;
    private final AdminRepository adminRepository;

    /**
     * Fetches current configuration or returns hardcoded business fallbacks if missing
     */
    public ReceiptSettingsDTO getSettings(String hotelId, String receiptType) {
        return repository.findByHotelIdAndReceiptType(hotelId, receiptType)
                .map(entity -> mapToDTO(hotelId, entity))
                .orElseGet(() -> getFallbackDefaults(hotelId, receiptType));
    }

    /**
     * Saves or updates configuration properties via dynamic Upsert logic execution
     */
    @Transactional
    public String saveSettings(String hotelId, String receiptType, ReceiptSettingsDTO dto) {
        ReceiptSettings settings = repository.findByHotelIdAndReceiptType(hotelId, receiptType)
                .orElse(ReceiptSettings.builder()
                        .hotelId(hotelId)
                        .receiptType(receiptType)
                        .build());

        // Map switches cleanly
        settings.setPrintRestaurantName(dto.isPrintRestaurantName());
        settings.setPrintBusinessAddress(dto.isPrintBusinessAddress());
        settings.setPrintCustomerNumber(dto.isPrintCustomerNumber());
        settings.setIncludeRestaurantLogo(dto.isIncludeRestaurantLogo());
        settings.setPrintDateTime(dto.isPrintDateTime());
        settings.setShowOrderTypeLabel(dto.isShowOrderTypeLabel());
        settings.setDisplayOrderId(dto.isDisplayOrderId());
        settings.setShowCustomerDetails(dto.isShowCustomerDetails());
        settings.setPrintUpiAndQr(dto.isPrintUpiAndQr());
        settings.setPrintGreetingNote(dto.isPrintGreetingNote());
        settings.setPrintItemQuantities(dto.isPrintItemQuantities());

        // 🌟 Save GST Number Print Toggle
        settings.setPrintGstNumber(dto.isPrintGstNumber());
        settings.setApplyItemLevelGst(dto.isApplyItemLevelGst());

        settings.setGstDetails(dto.getGstDetails());

        repository.save(settings);
        log.info("Successfully updated '{}' receipt settings for hotel: {}", receiptType, hotelId);
        return "Receipt settings for " + receiptType + " saved successfully.";
    }

    private ReceiptSettingsDTO mapToDTO(String hotelId, ReceiptSettings entity) {
        // Dynamically sync saved receipt toggles with latest Admin Business Details GST list
        List<ReceiptGstToggleDTO> syncedGstDetails = syncGstTogglesWithAdmin(hotelId, entity.getGstDetails());

        return ReceiptSettingsDTO.builder()
                .printRestaurantName(entity.isPrintRestaurantName())
                .printBusinessAddress(entity.isPrintBusinessAddress())
                .printCustomerNumber(entity.isPrintCustomerNumber())
                .includeRestaurantLogo(entity.isIncludeRestaurantLogo())
                .printDateTime(entity.isPrintDateTime())
                .showOrderTypeLabel(entity.isShowOrderTypeLabel())
                .displayOrderId(entity.isDisplayOrderId())
                .showCustomerDetails(entity.isShowCustomerDetails())
                .printUpiAndQr(entity.isPrintUpiAndQr())
                .printGreetingNote(entity.isPrintGreetingNote())
                .printItemQuantities(entity.isPrintItemQuantities())
                .printGstNumber(entity.isPrintGstNumber())
                .printGstNumber(entity.isPrintGstNumber())
                .applyItemLevelGst(entity.isApplyItemLevelGst())
                .gstDetails(syncedGstDetails)
                .build();
    }

    private ReceiptSettingsDTO getFallbackDefaults(String hotelId, String receiptType) {
        List<ReceiptGstToggleDTO> initialGstToggles = syncGstTogglesWithAdmin(hotelId, null);

        // Check if admin has configured a GST number in Business Details to set default true/false
        Admin admin = adminRepository.findByHotelId(hotelId).orElse(null);
        boolean defaultPrintGst = admin != null && admin.getGstNumber() != null && !admin.getGstNumber().trim().isEmpty();

        boolean defaultApplyBusinessGst = initialGstToggles != null && !initialGstToggles.isEmpty();
        boolean defaultApplyItemLevelGst = false;

        if ("TABLE_HOME".equalsIgnoreCase(receiptType)) {
            return ReceiptSettingsDTO.builder()
                    .printRestaurantName(true)
                    .printBusinessAddress(true)
                    .printCustomerNumber(true)
                    .includeRestaurantLogo(true)
                    .printDateTime(true)
                    .showOrderTypeLabel(true)
                    .displayOrderId(true)
                    .showCustomerDetails(true)
                    .printUpiAndQr(true)
                    .printGreetingNote(true)
                    .printItemQuantities(true)
                    .printGstNumber(defaultPrintGst)
                    .applyItemLevelGst(defaultApplyItemLevelGst)
                    .gstDetails(initialGstToggles)
                    .build();
        } else { // Fallback targets "INSTANT" configuration defaults directly
            return ReceiptSettingsDTO.builder()
                    .printRestaurantName(true)
                    .printBusinessAddress(false)
                    .printCustomerNumber(false)
                    .includeRestaurantLogo(false)
                    .printDateTime(true)
                    .showOrderTypeLabel(false)
                    .displayOrderId(true)
                    .showCustomerDetails(true)
                    .printUpiAndQr(true)
                    .printGreetingNote(true)
                    .printItemQuantities(true)
                    .printGstNumber(defaultPrintGst) // 🌟 Default true if GST number exists
                    .gstDetails(initialGstToggles)
                    .build();
        }
    }

    private List<ReceiptGstToggleDTO> syncGstTogglesWithAdmin(String hotelId, List<ReceiptGstToggleDTO> savedToggles) {
        Admin admin = adminRepository.findByHotelId(hotelId).orElse(null);

        if (admin == null || admin.getGstDetails() == null || admin.getGstDetails().isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, ReceiptGstToggleDTO> savedToggleMap = new HashMap<>();
        if (savedToggles != null) {
            for (ReceiptGstToggleDTO toggle : savedToggles) {
                if (toggle != null && toggle.getGstType() != null) {
                    savedToggleMap.put(toggle.getGstType().toUpperCase().trim(), toggle);
                }
            }
        }

        List<ReceiptGstToggleDTO> syncedList = new ArrayList<>();

        for (GstDetailDTO adminGst : admin.getGstDetails()) {
            if (adminGst == null || adminGst.getGstType() == null) continue;

            String gstTypeKey = adminGst.getGstType().toUpperCase().trim();

            if (savedToggleMap.containsKey(gstTypeKey)) {
                ReceiptGstToggleDTO saved = savedToggleMap.get(gstTypeKey);
                syncedList.add(ReceiptGstToggleDTO.builder()
                        .gstType(adminGst.getGstType())
                        .gstPercent(adminGst.getGstPercent())
                        .enabled(saved.isEnabled())
                        .build());
            } else {
                syncedList.add(ReceiptGstToggleDTO.builder()
                        .gstType(adminGst.getGstType())
                        .gstPercent(adminGst.getGstPercent())
                        .enabled(true)
                        .build());
            }
        }

        return syncedList;
    }
}