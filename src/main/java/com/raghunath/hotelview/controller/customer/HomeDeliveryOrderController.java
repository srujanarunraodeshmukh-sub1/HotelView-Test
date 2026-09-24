package com.raghunath.hotelview.controller.restaurantandinstant;

import com.raghunath.hotelview.dto.customer.AssignDeliveryBoyRequest;
import com.raghunath.hotelview.entity.customer.HomeDeliveryOrder;
import com.raghunath.hotelview.security.JwtUtil;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.customer.HomeDeliveryOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/restaurantandinstant/home-delivery")
public class HomeDeliveryOrderController {

    @Autowired
    private HomeDeliveryOrderService homeDeliveryOrderService;

    @Autowired
    private UserTypeAccessGuard userTypeAccessGuard;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/orders/pending")
    public org.springframework.http.ResponseEntity<Map<String, Object>> getPendingOrders(@RequestHeader("Authorization") String authHeader) {
        String hotelId = extractHotelId(authHeader);
        List<HomeDeliveryOrder> orders = homeDeliveryOrderService.getOrdersByStatus(hotelId, "PENDING");
        return org.springframework.http.ResponseEntity.ok(Map.of("success", true, "data", orders));
    }

    @GetMapping("/orders/accepted")
    public org.springframework.http.ResponseEntity<Map<String, Object>> getAcceptedOrders(@RequestHeader("Authorization") String authHeader) {
        String hotelId = extractHotelId(authHeader);
        List<HomeDeliveryOrder> orders = homeDeliveryOrderService.getOrdersByStatus(hotelId, "ACCEPTED");
        return org.springframework.http.ResponseEntity.ok(Map.of("success", true, "data", orders));
    }

    @GetMapping("/orders/completed")
    public org.springframework.http.ResponseEntity<Map<String, Object>> getCompletedOrders(@RequestHeader("Authorization") String authHeader) {
        String hotelId = extractHotelId(authHeader);
        List<HomeDeliveryOrder> orders = homeDeliveryOrderService.getOrdersByStatus(hotelId, "COMPLETED");
        return org.springframework.http.ResponseEntity.ok(Map.of("success", true, "data", orders));
    }

    @PutMapping("/orders/{id}/accept")
    public org.springframework.http.ResponseEntity<Map<String, Object>> acceptOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("id") String orderId) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        String token = authHeader.substring(7).trim();
        String hotelId = jwtUtil.extractHotelId(token);
        String staffId = jwtUtil.extractUserId(token);

        HomeDeliveryOrder order = homeDeliveryOrderService.acceptOrder(hotelId, orderId, staffId);

        return org.springframework.http.ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Order accepted successfully.",
                "data", order
        ));
    }

    @PutMapping("/orders/{id}/complete")
    public org.springframework.http.ResponseEntity<Map<String, Object>> completeOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("id") String orderId) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        String token = authHeader.substring(7).trim();
        String hotelId = jwtUtil.extractHotelId(token);
        String staffId = jwtUtil.extractUserId(token);

        HomeDeliveryOrder order = homeDeliveryOrderService.completeOrder(hotelId, orderId, staffId);

        return org.springframework.http.ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Order marked as completed.",
                "data", order
        ));
    }

    @PutMapping("/orders/{id}/assign")
    public org.springframework.http.ResponseEntity<Map<String, Object>> assignDeliveryBoy(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("id") String orderId,
            @jakarta.validation.Valid @RequestBody AssignDeliveryBoyRequest request) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        String token = authHeader.substring(7).trim();
        String hotelId = jwtUtil.extractHotelId(token);
        String staffId = jwtUtil.extractUserId(token);

        HomeDeliveryOrder order = homeDeliveryOrderService.assignDeliveryBoy(hotelId, orderId, staffId, request);

        return org.springframework.http.ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Delivery boy assigned and order handed over.",
                "data", order
        ));
    }

    private String extractHotelId(String authHeader) {
        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        String token = authHeader.substring(7).trim();
        return jwtUtil.extractHotelId(token);
    }
}