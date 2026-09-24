package com.raghunath.hotelview.controller.customer;

import com.raghunath.hotelview.dto.customer.PlaceHomeDeliveryOrderRequest;
import com.raghunath.hotelview.entity.customer.HomeDeliveryOrder;
import com.raghunath.hotelview.security.JwtUtil;
import com.raghunath.hotelview.service.customer.HomeDeliveryOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user/home-delivery")
public class CustomerHomeDeliveryController {

    @Autowired
    private HomeDeliveryOrderService homeDeliveryOrderService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> placeOrder(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody PlaceHomeDeliveryOrderRequest request) {

        String token = authHeader.substring(7).trim();
        String customerId = jwtUtil.extractUserId(token);

        HomeDeliveryOrder order = homeDeliveryOrderService.placeOrder(customerId, request);

        return new ResponseEntity<>(Map.of(
                "success", true
        ), HttpStatus.CREATED);
    }
}