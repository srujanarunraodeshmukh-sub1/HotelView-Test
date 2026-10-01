package com.raghunath.hotelview.controller.restaurantandinstantadmin;

import com.raghunath.hotelview.dto.restaurantandinstant.*;
import com.raghunath.hotelview.entity.restaurantandinstant.KitchenOrder;
import com.raghunath.hotelview.entity.restaurantandinstant.OrderDraft;
import com.raghunath.hotelview.repository.restaurantandinstant.KitchenOrderRepository;
import com.raghunath.hotelview.security.JwtUtil;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.restaurantandinstantadmin.KitchenOrderingService;
import com.raghunath.hotelview.service.restaurantandinstantadmin.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final MongoTemplate mongoTemplate;
    private final KitchenOrderingService kitchenOrderingService;
    private final JwtUtil jwtUtil;
    private final UserTypeAccessGuard userTypeAccessGuard;

    private String getHotelId() {
        return userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
    }

    private String getRestaurantOnlyHotelId() {
        return userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");
    }

    private String getAuthenticatedUserId() {
        try {
            // 1. Grab the active HTTP request from the thread context
            jakarta.servlet.http.HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                            .getRequest();

            // 2. Fetch the Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();

                // 3. Extract the subject (userId) using your verified JwtUtil structure
                return jwtUtil.extractUserId(token);
            }
        } catch (Exception e) {
            // Log the failure fallback info silently
        }

        // Fallback default: matches your security context subject name if header reading fails
        return org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName();
    }

    // 1. SAVE DRAFT FOR SPECIFIC TABLE - NOT IN USE
    @PostMapping("/draft/{tableName}")
    public ResponseEntity<String> saveOrderDraft(@PathVariable String tableName, @Valid @RequestBody List<OrderItem> items) {
        String hotelId = getRestaurantOnlyHotelId();
        orderService.saveDraft(hotelId, tableName, items);
        return ResponseEntity.ok("Draft saved successfully");
    }

    // 2. GET DRAFT FOR SPECIFIC TABLE - NOT IN USE
    @GetMapping("/draft/{tableName}")
    public OrderDraft getOrderDraft(@PathVariable String tableName) {
        String hotelId = getRestaurantOnlyHotelId();
        return orderService.getDraft(hotelId, tableName);
    }

    // 3. PLACE TABLE ORDER
    @PostMapping("/confirm/{tableName}")
    public ResponseEntity<String> confirmOrder(@PathVariable String tableName, @Valid @RequestBody OrderRequest request) {
        String hotelId = getRestaurantOnlyHotelId();
        String waiterId = getAuthenticatedUserId();

        orderService.confirmOrder(hotelId, tableName, request.getItems(), waiterId, request.getComment());
        return ResponseEntity.ok("Order sent to kitchen");
    }

    @PostMapping("/confirm/custom-order")
    public ResponseEntity<String> placeCustomItemOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CustomKitchenOrderRequest request) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");

        // Extract authorization payload properties cleanly
        String token = authHeader.substring(7).trim();
        String hotelId = jwtUtil.extractHotelId(token);
        String waiterId = jwtUtil.extractUserId(token);

        String result = orderService.confirmCustomItemOrder(hotelId, request, waiterId);

        return ResponseEntity.ok(result);
    }

    // 4. PLACE HOME DELIVERY ORDER
    @PostMapping("/confirm/delivery")
    public ResponseEntity<String> confirmHomeDelivery(@Valid @RequestBody DeliveryRequest request) {
        String hotelId = getHotelId();
        String waiterId = getAuthenticatedUserId();

        String type = StringUtils.hasText(request.getOrderType()) ? request.getOrderType() : "HOME_DELIVERY";

        String orderId = orderService.confirmHomeDelivery(hotelId, request.getItems(), waiterId, type);
        return ResponseEntity.ok("Order sent to kitchen. ID: " + orderId);
    }

    // 5. FETCH SPECIFIC TABLE ORDERS (Latest First)
    @GetMapping("/table/{tableName}")
    public ResponseEntity<List<KitchenOrder>> getTableOrders(@PathVariable String tableName) {
        String hotelId = getRestaurantOnlyHotelId();

        // 🌟 FIX: Directly pass the List<KitchenOrder> returned by the service
        List<KitchenOrder> orders = orderService.getOrdersByTable(hotelId, tableName);
        return ResponseEntity.ok(orders);
    }

    // 6. CHECKOUT ORDER FOR TABLE
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CheckoutRequest request) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");

        // Extract token
        String token = authHeader.substring(7).trim();

        // Extract hotelId and actual user id from JWT
        String hotelId = jwtUtil.extractHotelId(token);
        String checkoutBy = jwtUtil.extractUserId(token);

        CheckoutResponse response = orderService.checkoutOrders(hotelId, request, checkoutBy);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/direct-checkout")
    public ResponseEntity<CheckoutResponse> directCheckout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody DirectOrderRequest request) {

        // 🌟 Enforce RESTAURANT/INSTANT before trusting hotelId from the token
        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");

        // 1. Extract raw token string cleanly
        String token = authHeader.substring(7).trim();

        // 2. Extract multi-tenant identities from your verified JWT claims
        String hotelId = jwtUtil.extractHotelId(token);
        String checkoutBy = jwtUtil.extractUserId(token);

        // 3. Delegate to the uniform business handler layer
        CheckoutResponse response = orderService.checkoutDirectOrder(hotelId, request, checkoutBy);

        return ResponseEntity.ok(response);
    }


    // 7. INSTANT CHECKOUT
    @PostMapping("/instant/checkout")
    public ResponseEntity<CheckoutResponse> processInstantOrder(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody InstantCheckoutRequest request) {

        // 🌟 Enforce RESTAURANT/INSTANT before trusting hotelId from the token
        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");

        String token = authHeader.substring(7).trim();

        String hotelId = jwtUtil.extractHotelId(token);
        String actualLoggedInUser = jwtUtil.extractUserId(token);

        CheckoutResponse response = orderService.instantOrderAndCheckout(hotelId, request, actualLoggedInUser);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/instant/direct-checkout")
    public ResponseEntity<CheckoutResponse> processInstantCustomOrder(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody InstantCheckoutRequestNew request) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");

        String token = authHeader.substring(7).trim();

        String hotelId = jwtUtil.extractHotelId(token);
        String actualLoggedInUser = jwtUtil.extractUserId(token);

        CheckoutResponse response = orderService.instantCustomOrderAndCheckout(hotelId, request, actualLoggedInUser);

        return ResponseEntity.ok(response);
    }

    // 8. GET STATISTICS
    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        String hotelId = getHotelId();
        return ResponseEntity.ok(orderService.getDashboardStats(hotelId));
    }

    // 9. EDIT ORDER
    @PutMapping("/kitchen/{orderId}/confirm-edit")
    public ResponseEntity<String> confirmEdit(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String orderId,
            @RequestBody List<OrderItem> newItems) {
        String hotelId = getRestaurantOnlyHotelId();
        String token = authHeader.substring(7).trim();
        String editedBy = jwtUtil.extractUserId(token);
        orderService.confirmOrderEdit(hotelId, orderId, newItems, editedBy);
        return ResponseEntity.ok("Order updated and logs saved successfully");
    }

    /**
     * API 1: Fetch Today's Complete Sales & Staff Summary
     * Example GET: /api/v1/analytics/summary/today?hotelId=104
     */
    @GetMapping("/summary/today")
    public ResponseEntity<DailySummaryResponse> getTodaySummary() {
        String hotelId = getHotelId();
        DailySummaryResponse response = orderService.getTodaySummary(hotelId);
        return ResponseEntity.ok(response);
    }

    /**
     * API 2: Fetch Summary for Any Specific Date
     * Example GET: /api/v1/analytics/summary/date?hotelId=104&date=2026-07-28
     */
    @GetMapping("/summary/date")
    public ResponseEntity<DailySummaryResponse> getSummaryByDate(
            @RequestParam("date") String date) { // Expects "YYYY-MM-DD"
        String hotelId = getHotelId();
        DailySummaryResponse response = orderService.getSummaryByDate(hotelId, date);
        return ResponseEntity.ok(response);
    }

    // 10. Get Full Table History via Completed Order ID
    @GetMapping("/summary/completed/{completedOrderId}")
    public ResponseEntity<Map<String, Object>> getFullTableSummary(@PathVariable String completedOrderId) {
        String hotelId = getRestaurantOnlyHotelId();

        try {
            Map<String, Object> aggregatedSummary = orderService.getAggregatedTableSummary(hotelId, completedOrderId);
            return ResponseEntity.ok(aggregatedSummary);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 11. PUBLIC WEBHOOK: Receives orders from Zomato/Swiggy
    @PostMapping("/webhook")
    public ResponseEntity<String> receiveExternalOrder(@RequestBody OrderWebhookDTO externalOrder) {
        orderService.processExternalOrder(externalOrder);
        return ResponseEntity.ok("Order Received Successfully");
    }

    // 12. PRIVATE API: Approve external order and move to kitchen
    @PutMapping("/external/accept/{orderId}")
    public ResponseEntity<String> acceptExternalOrder(@PathVariable String orderId) {
        String userId = getHotelId();
        String kitchenOrderId = orderService.approveExternalOrder(orderId, userId);
        return ResponseEntity.ok("External order approved. Kitchen Order ID: " + kitchenOrderId);
    }

    // 13. ACCEPT CUSTOMER QR ORDER (Requires JWT Authorization)
    @PostMapping("/accept-customer-order")
    public ResponseEntity<Map<String, Object>> acceptCustomerOrder(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody AcceptCustomerOrderRequest request) {

        userTypeAccessGuard.enforceUserTypeForCurrentUser(
                "RESTAURANT",
                "INSTANT"
        );

        String token = authHeader.substring(7).trim();

        String hotelId = jwtUtil.extractHotelId(token);
        String staffId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);

        KitchenOrder kitchenOrder = orderService.acceptCustomerOrder(
                hotelId,
                request.getCustomerOrderId(),
                staffId,
                role
        );

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Order accepted successfully and sent to kitchen.",
                "kitchenOrderId", kitchenOrder.getId()
        ));
    }
}
