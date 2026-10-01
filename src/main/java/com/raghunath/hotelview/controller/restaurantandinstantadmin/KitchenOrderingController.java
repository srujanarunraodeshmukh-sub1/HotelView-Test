package com.raghunath.hotelview.controller.restaurantandinstantadmin;

import com.raghunath.hotelview.entity.restaurantandinstant.KitchenOrder;
import com.raghunath.hotelview.repository.restaurantandinstant.KitchenOrderRepository;
import com.raghunath.hotelview.security.JwtUtil;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.restaurantandinstantadmin.KitchenOrderingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class KitchenOrderingController {

    private final KitchenOrderRepository kitchenOrderRepository;
    private final KitchenOrderingService kitchenOrderingService;
    private final JwtUtil jwtUtil;
    private final UserTypeAccessGuard userTypeAccessGuard;

    // 5. CHEF: FETCH PENDING ORDERS
    @GetMapping("/kitchen/pending")
    public ResponseEntity<List<KitchenOrder>> getPendingOrders() {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");
        return ResponseEntity.ok(kitchenOrderRepository.findByHotelIdAndStatus(hotelId, "PENDING"));
    }

    // 6. CHEF: ACCEPT ORDER (PENDING -> PREPARING)
    // Extract real user _id from JWT same way as instantOrderAndCheckout
    @PatchMapping("/kitchen/accept/{orderId}")
    public ResponseEntity<String> acceptOrder(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");

        String token = authHeader.substring(7).trim();
        String chefId = jwtUtil.extractUserId(token); // real MongoDB _id

        kitchenOrderingService.updateStatusWithChef(orderId, "PREPARING", chefId);
        return ResponseEntity.ok("Accepted by " + chefId);
    }

    // 7. CHEF: FETCH PREPARING ORDERS
    @GetMapping("/kitchen/preparing")
    public ResponseEntity<List<KitchenOrder>> getPreparingOrders() {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");
        return ResponseEntity.ok(kitchenOrderRepository.findByHotelIdAndStatus(hotelId, "PREPARING"));
    }

    // 8. CHEF: COMPLETE ORDER (PREPARING -> COMPLETED)
    @PatchMapping("/kitchen/complete/{orderId}")
    public ResponseEntity<String> completeOrder(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");

        String token = authHeader.substring(7).trim();
        String userId = jwtUtil.extractUserId(token); // real MongoDB _id

        kitchenOrderingService.updateStatusWithChef(orderId, "COMPLETED", userId);
        return ResponseEntity.ok("Order marked as COMPLETED");
    }

    // 9. CHEF: FETCH COMPLETED ORDERS
    @GetMapping("/kitchen/completed")
    public ResponseEntity<List<KitchenOrder>> getCompletedOrders() {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");
        return ResponseEntity.ok(kitchenOrderRepository.findByHotelIdAndStatus(hotelId, "COMPLETED"));
    }

    // 10. DELETE KITCHEN ORDER (WAITER / ADMIN only, same hotel only)
    @DeleteMapping("/kitchen/{orderId}")
    public ResponseEntity<String> deleteOrder(
            @PathVariable String orderId,
            @RequestHeader("Authorization") String authHeader) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");

        String token = authHeader.substring(7).trim();
        String role = jwtUtil.extractRole(token);
        String hotelId = jwtUtil.extractHotelId(token); // hotel comes from the token, never from the request

        if (role == null || !(role.equalsIgnoreCase("WAITER") || role.equalsIgnoreCase("ADMIN"))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only WAITER or ADMIN can delete kitchen orders");
        }

        kitchenOrderingService.deleteOrder(hotelId, orderId);
        return ResponseEntity.ok("Order deleted");
    }
}
