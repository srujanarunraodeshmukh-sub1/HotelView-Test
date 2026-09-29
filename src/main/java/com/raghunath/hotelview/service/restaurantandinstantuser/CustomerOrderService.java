package com.raghunath.hotelview.service.restaurantandinstantuser;

import com.raghunath.hotelview.dto.restaurantandinstant.OrderItem;
import com.raghunath.hotelview.entity.restaurantandinstant.Admin;
import com.raghunath.hotelview.entity.restaurantandinstant.CustomerOrder;
import com.raghunath.hotelview.entity.restaurantandinstant.KitchenOrder;
import com.raghunath.hotelview.entity.restaurantandinstant.RestaurantTable;
import com.raghunath.hotelview.repository.restaurantandinstant.AdminRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.KitchenOrderRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.TableRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final CustomerOrderRepository customerTableOrderRepository;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final TableRepository restaurantTableRepository; // 🌟 Injected to resolve tableId -> tableName
    private final AdminRepository adminRepository;

    private ZonedDateTime getISTNow() {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
    }

    public List<Object> getCustomerOrdersByTable(String hotelId, String tableIdOrName) {
        // 1. Sanitize & Validate Inputs
        if (!StringUtils.hasText(hotelId) || !StringUtils.hasText(tableIdOrName)) {
            throw new IllegalArgumentException("Hotel ID and Table Identifier are required");
        }

        String cleanHotelId = hotelId.trim();
        String targetInput = tableIdOrName.trim();

        // 2. Validate Tenant Subscription & Premium Plan Check
        Admin admin = adminRepository.findByHotelId(cleanHotelId)
                .orElseThrow(() -> new RuntimeException("Invalid QR Code: Restaurant not found"));

        if (admin.getSubscriptionExpiry() != null &&
                getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Service temporarily disabled for this restaurant");
        }

        if (admin.getPlanType() == null || !"PREMIUM".equalsIgnoreCase(admin.getPlanType().trim())) {
            throw new RuntimeException("Upgrade to premium plan");
        }

        // 3. 🌟 RESOLVE TABLE NAME IF INPUT IS AN _id
        String resolvedTableName = targetInput;

        // Try looking up by MongoDB _id first
        RestaurantTable table = restaurantTableRepository.findById(targetInput)
                .orElse(null);

        if (table != null && StringUtils.hasText(table.getTableName())) {
            resolvedTableName = table.getTableName(); // e.g., converts "6a608871b5ceea76cc7e2dd0" -> "T10"
        }

        // 4. Fetch Pending Customer Orders matching resolved tableName ("T10")
        List<CustomerOrder> pendingCustomerOrders = customerTableOrderRepository
                .findByHotelIdAndTableNameAndStatusNotOrderByCreatedDateDescCreatedTimeDesc(
                        cleanHotelId, resolvedTableName, "PAID");

        // 5. Fetch Accepted Kitchen Orders matching resolved tableName ("T10")
        List<KitchenOrder> activeKitchenOrders = kitchenOrderRepository
                .findByHotelIdAndTableNameAndStatusNotOrderByCreatedDateDescCreatedTimeDesc(
                        cleanHotelId, resolvedTableName, "COMPLETED");

        // 6. Combine both lists
        List<Object> combinedOrders = new ArrayList<>();
        if (pendingCustomerOrders != null) {
            combinedOrders.addAll(pendingCustomerOrders);
        }
        if (activeKitchenOrders != null) {
            combinedOrders.addAll(activeKitchenOrders);
        }

        return Collections.singletonList(Map.of(
                "restaurantName", admin.getRestaurantName() != null ? admin.getRestaurantName() : "",
                "orders", combinedOrders
        ));
    }

    /**
     * PLACE CUSTOMER QR ORDER
     * Saves order into CustomerOrder (customer_table_orders) with status = "PENDING_ACCEPTANCE"
     */
    @CacheEvict(value = "dashboardStatsCache", key = "#hotelId")
    @Transactional
    public CustomerOrder placeCustomerOrder(String hotelId, String tableIdOrName, List<OrderItem> items, String comment) {
        // 1. Sanitize & Validate Inputs
        if (!StringUtils.hasText(hotelId) || !StringUtils.hasText(tableIdOrName)) {
            throw new IllegalArgumentException("Hotel ID and Table Identifier are required");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be empty");
        }

        String cleanHotelId = hotelId.trim();
        String targetInput = tableIdOrName.trim();

        // 2. Validate Restaurant & Subscription Expiry
        Admin admin = adminRepository.findByHotelId(cleanHotelId)
                .orElseThrow(() -> new RuntimeException("Invalid QR Code: Restaurant not found"));

        if (admin.getSubscriptionExpiry() != null &&
                getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        if (admin.getPlanType() == null || !"PREMIUM".equalsIgnoreCase(admin.getPlanType().trim())) {
            throw new RuntimeException("Upgrade to premium plan to access QR ordering.");
        }

        // 3. Resolve table_id or table_name to actual tableName
        String resolvedTableName = targetInput;
        RestaurantTable table = restaurantTableRepository.findById(targetInput).orElse(null);
        if (table != null && StringUtils.hasText(table.getTableName())) {
            resolvedTableName = table.getTableName();
        } else {
            // Verify table exists by name if ID lookup failed
            table = restaurantTableRepository.findByHotelIdAndTableName(cleanHotelId, targetInput)
                    .orElseThrow(() -> new RuntimeException("Table " + targetInput + " does not exist."));
        }

        ZonedDateTime nowIST = getISTNow();

        // 4. Sanitize Order Items
        items.forEach(item -> {
            if (item.getItemId() != null) {
                item.setItemId(item.getItemId().contains("//") ? item.getItemId().split("//")[0].trim() : item.getItemId().trim());
            }

            if (item.getItemName() != null) {
                item.setItemName(item.getItemName().contains("//") ? item.getItemName().split("//")[0].trim() : item.getItemName().trim());
            }

            if (item.getQuantity() != null) {
                item.setQuantity(item.getQuantity().contains("//") ? item.getQuantity().split("//")[0].trim() : item.getQuantity().trim());
            }

            if (item.getUnit() == null || item.getUnit().trim().isEmpty()) {
                item.setUnit("PIECE");
            } else {
                item.setUnit(item.getUnit().contains("//") ? item.getUnit().split("//")[0].trim() : item.getUnit().trim());
            }
        });

        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        // 5. Build and Save CustomerOrder Entity
        CustomerOrder customerOrder = CustomerOrder.builder()
                .hotelId(cleanHotelId)
                .tableName(resolvedTableName)
                .orderType("TABLE")
                .items(items)
                .totalAmount(total)
                .status("AWAITING") // Waiter will confirm this order
                .comments(comment)
                .placedBy("CUSTOMER")
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        CustomerOrder savedOrder = customerTableOrderRepository.save(customerOrder);

        table.setStatus("AWAITING");
        restaurantTableRepository.save(table);

        return savedOrder;
    }

}