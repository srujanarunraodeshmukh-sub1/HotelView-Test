package com.raghunath.hotelview.service.restaurantandinstantadmin;

import com.raghunath.hotelview.entity.restaurantandinstant.Admin;
import com.raghunath.hotelview.entity.restaurantandinstant.Employee;
import com.raghunath.hotelview.entity.restaurantandinstant.KitchenOrder;
import com.raghunath.hotelview.repository.restaurantandinstant.AdminRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.EmployeeRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.KitchenOrderRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import java.time.LocalDateTime;

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

        // 1. Find the order
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() ->
                        new RuntimeException("Order not found"));

        // 2. Multi-tenant safety
        if (!hotelId.equals(order.getHotelId())) {
            throw new RuntimeException("Order not found");
        }

        // 3. Find the name of the person deleting the order
        String deletedByName;

        if ("ADMIN".equalsIgnoreCase(role)) {

            // ADMIN
            Admin admin = adminRepository.findById(deletedById)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Admin not found with ID: " + deletedById));

            // Make sure admin belongs to the same hotel
            if (!hotelId.equals(admin.getHotelId())) {
                throw new RuntimeException(
                        "Unauthorized: Admin does not belong to this hotel");
            }

            deletedByName = admin.getName();

        } else {

            // EMPLOYEE
            Employee employee = employeeRepository.findById(deletedById)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Employee not found with ID: " + deletedById));

            // Make sure employee belongs to the same hotel
            if (!hotelId.equals(employee.getHotelId())) {
                throw new RuntimeException(
                        "Unauthorized: Employee does not belong to this hotel");
            }

            deletedByName = employee.getName();
        }

        // 4. Convert KitchenOrder into MongoDB Document
        Document deletedOrder = new Document();

        mongoTemplate.getConverter().write(
                order,
                deletedOrder
        );

        // 5. Add deletion information
        deletedOrder.put("deletedBy", deletedByName);
        deletedOrder.put("deletedAt", LocalDateTime.now());

        // 6. Save complete order into deleted_orders collection
        mongoTemplate.save(
                deletedOrder,
                "deleted_orders"
        );

        // 7. Delete original order from kitchen_orders
        kitchenOrderRepository.delete(order);

        // 8. Log deletion
        log.info(
                "Order {} deleted by {} for hotel {}",
                orderId,
                deletedByName,
                hotelId
        );

        // 9. Notify kitchen screens
        versionService.bumpKitchen(hotelId);
    }

    /**
     * GENERAL STATUS UPDATE: Fallback for direct status modifications
     */
    @Transactional
    public void updateOrderStatus(String orderId, String newStatus) {
        updateStatusWithChef(orderId, newStatus, null);
    }
}