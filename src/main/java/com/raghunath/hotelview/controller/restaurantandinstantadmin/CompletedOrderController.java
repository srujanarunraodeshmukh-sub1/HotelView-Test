package com.raghunath.hotelview.controller.restaurantandinstantadmin;

import com.raghunath.hotelview.dto.restaurantandinstant.*;
import com.raghunath.hotelview.entity.restaurantandinstant.CompletedOrder;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.restaurantandinstantadmin.CompletedOrderService;
import com.raghunath.hotelview.service.restaurantandinstantadmin.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders/completed")
@RequiredArgsConstructor
public class CompletedOrderController {

    private final OrderService orderService;
    private final CompletedOrderService completedOrderService;
    private final UserTypeAccessGuard userTypeAccessGuard;

    @GetMapping("/list")  // Allow Restaurant, Instant
    public ResponseEntity<Page<DeliverySummaryDTO>> getCompletedList(
            @RequestParam(defaultValue = "0") int page) {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        return ResponseEntity.ok(completedOrderService.getCompletedOrdersPaged(hotelId, page));
    }

    @GetMapping("/list/staff/{checkoutBy}")  // Allow Restaurant, Instant
    public ResponseEntity<Page<DailyOrderGroupDTO>> getCompletedListByStaff(
            @PathVariable String checkoutBy,
            @RequestParam(defaultValue = "0") int page) {

        // Extracts context authentication name exactly like your original method
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");

        Page<DailyOrderGroupDTO> staffOrders = completedOrderService.getCompletedOrdersByStaffPaged(hotelId, checkoutBy, page);
        return ResponseEntity.ok(staffOrders);
    }

    @GetMapping("/{id}")  // Allow Restaurant, Instant
    public ResponseEntity<ReceiptResponse> getOrderDetails(@PathVariable String id) {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        return ResponseEntity.ok(completedOrderService.getReceiptDetails(id, hotelId));
    }

    @GetMapping("/search")  // Allow Restaurant, Instant
    public ResponseEntity<List<CompletedOrder>> searchOrders(@RequestParam String query) {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        return ResponseEntity.ok(completedOrderService.searchCompletedOrders(hotelId, query));
    }

    @GetMapping("/delivery/today")  // Allow Restaurant, Instant
    public ResponseEntity<List<DeliverySummaryDTO>> getTodayCompletedDeliveries() {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        return ResponseEntity.ok(completedOrderService.getTodayCompletedHomeDeliveries(hotelId));
    }

    // --- EDIT COMPLETED ORDER DETAILS & TOTALS ---
    @PutMapping("/{id}/edit")  // Allow Restaurant, Instant
    public ResponseEntity<String> editCompletedOrder(
            @PathVariable String id,
            @RequestBody com.raghunath.hotelview.dto.restaurantandinstant.CompletedOrderEditDTO editDto) {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        completedOrderService.editCompletedOrderDetails(hotelId, id, editDto);
        return ResponseEntity.ok("Completed order items quantity and totals updated successfully.");
    }

    // --- PERMANENTLY DELETE COMPLETED ORDER ---
    @DeleteMapping("/{id}")  // Allow Restaurant, Instant
    public ResponseEntity<String> deleteCompletedOrder(@PathVariable String id) {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        completedOrderService.deleteCompletedOrderById(hotelId, id);
        return ResponseEntity.ok("Completed order deleted successfully and customer tracking reversed.");
    }

    @DeleteMapping("/delete-multiple")  // Allow Restaurant
    public ResponseEntity<String> deleteOrders(@RequestBody List<String> orderIds) {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");
        completedOrderService.softDeleteOrders(hotelId, orderIds);
        return ResponseEntity.ok("Orders moved to trash successfully.");
    }

    @GetMapping("/analytics/today")  // Allow Restaurant, Instant
    @Cacheable(value = "orderCache", key = "#principal.name + '-today'")
    public ResponseEntity<SalesAnalyticsDTO> getTodayAnalytics(java.security.Principal principal) { // 👈 Injected Principal parameter
        String hotelId = principal.getName(); // 👈 Bypasses manual SecurityContextHolder lookups!
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");
        return ResponseEntity.ok(orderService.getTodayHourlySales(hotelId));
    }

    @GetMapping("/analytics/week")  // Allow Restaurant, Instant
    @Cacheable(value = "orderCache", key = "#principal.name + '-week'")
    public ResponseEntity<SalesAnalyticsDTO> getWeeklyAnalytics(java.security.Principal principal) {
        String hotelId = principal.getName();
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");
        return ResponseEntity.ok(orderService.getCurrentWeekSales(hotelId));
    }

    @GetMapping("/analytics/month")  // Allow Restaurant, Instant
    @Cacheable(value = "orderCache", key = "#principal.name + '-month'")
    public ResponseEntity<SalesAnalyticsDTO> getMonthlyAnalytics(java.security.Principal principal) {
        String hotelId = principal.getName();
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");
        return ResponseEntity.ok(orderService.getCurrentMonthSales(hotelId));
    }

    @GetMapping("/analytics/year")  // Allow Restaurant, Instant
    @Cacheable(value = "orderCache", key = "#principal.name + '-year'")
    public ResponseEntity<SalesAnalyticsDTO> getYearlyAnalytics(java.security.Principal principal) {
        String hotelId = principal.getName();
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");
        return ResponseEntity.ok(orderService.getCurrentYearSales(hotelId));
    }

    @GetMapping("/deleted-list")  // Allow Restaurant
    public ResponseEntity<List<org.bson.Document>> getDeletedOrders() {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");
        return ResponseEntity.ok(completedOrderService.getDeletedOrders(hotelId));
    }

    @GetMapping("/{orderId}/inventory-profiles")  // Allow Restaurant, Instant
    public ResponseEntity<List<InventoryItemsResponse>> getBillInventoryFootprints(
            @PathVariable String orderId) {

        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");

        List<InventoryItemsResponse> responsePayload = completedOrderService.getOrderInventoryDetails(hotelId, orderId);
        return ResponseEntity.ok(responsePayload);
    }
}