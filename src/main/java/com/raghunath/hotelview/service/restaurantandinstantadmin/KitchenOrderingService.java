package com.raghunath.hotelview.service.restaurantandinstantadmin;

import com.raghunath.hotelview.entity.restaurantandinstant.Admin;
import com.raghunath.hotelview.entity.restaurantandinstant.CustomerOrder;
import com.raghunath.hotelview.entity.restaurantandinstant.Employee;
import com.raghunath.hotelview.entity.restaurantandinstant.KitchenOrder;
import com.raghunath.hotelview.repository.restaurantandinstant.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenOrderingService {

    private final AdminRepository adminRepository;
    private final MongoTemplate mongoTemplate;
    private final EmployeeRepository employeeRepository;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final VersionService versionService;
    private final TableRepository tableRepository;
    private final CustomerOrderRepository customerOrderRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * 1. UPDATE STATUS WITH CHEF / ADMIN: Handles Kitchen Lifecycle.
     * Open to both Chefs and Admins natively.
     */
    @Transactional
    public void updateStatusWithChef(String orderId, String newStatus, String userId) {
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        String formattedStatus = newStatus.toUpperCase();
        order.setStatus(formattedStatus);
        order.setUpdatedAt(ZonedDateTime.now(IST).toLocalDateTime());

        // Tracks who claimed/accepted the order preparation ticket (Chef or Admin)
        if ("PREPARING".equalsIgnoreCase(formattedStatus) && userId != null) {
            order.setAcceptedBy(userId);
            log.info("Order {} accepted by User: {}", orderId, userId);
        }

        // ADD THIS BLOCK
        if ("COMPLETED".equalsIgnoreCase(formattedStatus) && userId != null) {
            order.setCompletedBy(userId);
            log.info("Order {} completed by User: {}", orderId, userId);
        }

        kitchenOrderRepository.save(order);

        // Notify downstream apps via version updates
        versionService.bumpKitchen(order.getHotelId());

        // Process Live Floor Table Visual State Transitions
        if ("TABLE".equalsIgnoreCase(order.getOrderType()) && order.getTableName() != null) {
            String tableUIStatus = switch (formattedStatus) {
                case "ACCEPTED", "PREPARING" -> "ACCEPTED";
                case "COMPLETED" -> "ACTIVE";
                default -> formattedStatus;
            };

            updateTableVisualStatus(order.getHotelId(), order.getTableName(), tableUIStatus);
        }
    }

    /**
     * ✅ Untouched: Keeps your original string-basedTableName logic intact
     */
    private void updateTableVisualStatus(String hotelId, String tableName, String status) {
        if (tableName == null) return;

        tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(t -> {
            t.setStatus(status);
            t.setUpdatedAt(ZonedDateTime.now(IST).toLocalDateTime());
            tableRepository.save(t);

            // Sync live dashboard views
            versionService.bumpTables(hotelId);
        });
    }

    /**
     * DELETE ORDER: Removes a kitchen order (scoped to the caller's hotel).
     */
    @Transactional
    public void deleteOrder(
            String hotelId,
            String orderId,
            String deletedById,
            String role) {

        // =========================================================
        // 1. FIND ORDER
        // =========================================================

        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() ->
                        new RuntimeException("Order not found"));

        // =========================================================
        // 2. MULTI-TENANT SAFETY
        // =========================================================

        if (!hotelId.equals(order.getHotelId())) {
            throw new RuntimeException("Order not found");
        }

        String tableName = order.getTableName();

        // Amount that will be removed from the table bill
        double deletedOrderAmount =
                order.getTotalAmount() != null
                        ? order.getTotalAmount()
                        : 0.0;

        // =========================================================
        // 3. FIND WHO DELETED THE ORDER
        // =========================================================

        String deletedByName;

        if ("ADMIN".equalsIgnoreCase(role)) {

            Admin admin = adminRepository.findById(deletedById)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Admin not found with ID: " + deletedById));

            // Admin must belong to same hotel
            if (!hotelId.equals(admin.getHotelId())) {
                throw new RuntimeException(
                        "Unauthorized: Admin does not belong to this hotel");
            }

            deletedByName = admin.getName();

        } else {

            Employee employee = employeeRepository.findById(deletedById)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Employee not found with ID: " + deletedById));

            // Employee must belong to same hotel
            if (!hotelId.equals(employee.getHotelId())) {
                throw new RuntimeException(
                        "Unauthorized: Employee does not belong to this hotel");
            }

            deletedByName = employee.getName();
        }

        // =========================================================
        // 4. ARCHIVE ORDER INTO deleted_orders
        // =========================================================

        Document deletedOrder = new Document();

        mongoTemplate.getConverter().write(
                order,
                deletedOrder
        );

        // Keep original order ID for audit/history
        deletedOrder.put("originalOrderId", order.getId());

        // Let deleted_orders get its own MongoDB _id
        deletedOrder.remove("_id");

        // Deletion information
        deletedOrder.put("deletedBy", deletedByName);
        deletedOrder.put("deletedById", deletedById);
        deletedOrder.put("deletedAt", LocalDateTime.now());

        // Save archive
        mongoTemplate.insert(
                deletedOrder,
                "deleted_orders"
        );

        // =========================================================
        // 5. DELETE ORIGINAL KITCHEN ORDER
        // =========================================================

        kitchenOrderRepository.delete(order);

        // =========================================================
        // 6. UPDATE TABLE BILL + STATUS
        // =========================================================

        if (StringUtils.hasText(tableName)) {

            tableRepository.findByHotelIdAndTableName(
                    hotelId,
                    tableName
            ).ifPresent(table -> {

                // Current bill
                double currentBill =
                        table.getCurrentBill() != null
                                ? table.getCurrentBill()
                                : 0.0;

                // Deduct deleted order amount
                double newBill = Math.max(
                        0.0,
                        currentBill - deletedOrderAmount
                );

                table.setCurrentBill(newBill);

                // =================================================
                // CASE 1: BILL IS ZERO
                // =================================================

                if (newBill <= 0.0) {

                    // Check if customer has another QR order
                    List<CustomerOrder> pendingCustomerOrders =
                            customerOrderRepository
                                    .findByHotelIdAndTableNameAndStatus(
                                            hotelId,
                                            tableName,
                                            "AWAITING"
                                    );

                    if (!pendingCustomerOrders.isEmpty()) {

                        // Customer has placed another order
                        table.setStatus("AWAITING");

                        log.info(
                                "TABLE_STATUS: Hotel {} Table {} -> AWAITING " +
                                        "(pending customer order exists)",
                                hotelId,
                                tableName
                        );

                    } else {

                        // No bill + no pending customer order
                        table.setStatus("INACTIVE");

                        log.info(
                                "TABLE_STATUS: Hotel {} Table {} -> INACTIVE",
                                hotelId,
                                tableName
                        );
                    }

                }

                // =================================================
                // CASE 2: BILL IS STILL GREATER THAN ZERO
                // =================================================

                else {

                    // Find the latest remaining kitchen order
                    KitchenOrder latestOrder =
                            kitchenOrderRepository
                                    .findFirstByHotelIdAndTableNameOrderByCreatedDateDescCreatedTimeDesc(
                                            hotelId,
                                            tableName
                                    );

                    if (latestOrder != null) {

                        String latestOrderStatus =
                                latestOrder.getStatus();

                        String tableStatus =
                                convertOrderStatusToTableStatus(
                                        latestOrderStatus
                                );

                        table.setStatus(tableStatus);

                        log.info(
                                "TABLE_STATUS: Hotel {} Table {} -> {} " +
                                        "(latest order {} status {})",
                                hotelId,
                                tableName,
                                tableStatus,
                                latestOrder.getId(),
                                latestOrderStatus
                        );

                    } else {

                        /*
                         * This should normally not happen because
                         * currentBill > 0 means some order should exist.
                         *
                         * But keep a safe fallback.
                         */
                        table.setStatus("INACTIVE");

                        log.warn(
                                "TABLE_STATUS: Bill is {} but no remaining " +
                                        "kitchen order found for Hotel {} Table {}",
                                newBill,
                                hotelId,
                                tableName
                        );
                    }
                }

                // Update timestamp
                table.setUpdatedAt(LocalDateTime.now());

                // Save table
                tableRepository.save(table);

                log.info(
                        "BILL_SYNC: Hotel {} Table {} bill changed " +
                                "from {} to {}. Final status: {}",
                        hotelId,
                        tableName,
                        currentBill,
                        newBill,
                        table.getStatus()
                );
            });
        }

        // =========================================================
        // 7. LOG DELETION
        // =========================================================

        log.info(
                "Order {} deleted by {} for hotel {}",
                orderId,
                deletedByName,
                hotelId
        );

        // =========================================================
        // 8. NOTIFY KITCHEN SCREENS
        // =========================================================

        versionService.bumpKitchen(hotelId);

        // Also notify table/dashboard changes
        versionService.bumpTables(hotelId);
    }

    /**
     * GENERAL STATUS UPDATE: Fallback for direct status modifications
     */
    @Transactional
    public void updateOrderStatus(String orderId, String newStatus) {
        updateStatusWithChef(orderId, newStatus, null);
    }


    private String convertOrderStatusToTableStatus(String orderStatus) {

        if (orderStatus == null) {
            return "INACTIVE";
        }

        return switch (orderStatus.toUpperCase()) {

            case "PENDING" ->
                    "PENDING";

            case "PREPARING" ->
                    "ACCEPTED";

            case "COMPLETED" ->
                    "ACTIVE";

            default ->
                    "ACTIVE";
        };
    }
}