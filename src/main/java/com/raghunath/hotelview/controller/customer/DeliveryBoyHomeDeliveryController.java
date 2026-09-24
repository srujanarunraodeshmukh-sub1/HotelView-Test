package com.raghunath.hotelview.controller.customer;

import com.raghunath.hotelview.entity.customer.HomeDeliveryOrder;
import com.raghunath.hotelview.security.JwtUtil;
import com.raghunath.hotelview.service.customer.HomeDeliveryOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/delivery/home-delivery")
public class DeliveryBoyHomeDeliveryController {

    @Autowired
    private HomeDeliveryOrderService homeDeliveryOrderService;

    @Autowired
    private JwtUtil jwtUtil;

    @PutMapping("/orders/{id}/delivered")
    public ResponseEntity<Map<String, Object>> markDelivered(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("hotelId") String hotelId, // delivery boy isn't tied to a hotel, so it's passed explicitly
            @PathVariable("id") String orderId) {

        String token = authHeader.substring(7).trim();
        String deliveryBoyId = jwtUtil.extractUserId(token);

        HomeDeliveryOrder order = homeDeliveryOrderService.markDelivered(hotelId, orderId, deliveryBoyId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Order marked as delivered.",
                "data", order
        ));
    }
}