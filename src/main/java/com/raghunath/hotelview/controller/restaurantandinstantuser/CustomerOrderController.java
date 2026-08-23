package com.raghunath.hotelview.controller.restaurantandinstantuser;

import com.raghunath.hotelview.dto.restaurantandinstant.CustomerOrderRequest;
import com.raghunath.hotelview.entity.restaurantandinstant.CustomerOrder;
import com.raghunath.hotelview.service.restaurantandinstantuser.CustomerOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("userKitchenOrderingController")
@RequestMapping("/api/v1/user/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CustomerOrderService customerOrderService;

    @GetMapping("/table")
    public ResponseEntity<List<Object>> getOrdersByTable(
            @RequestParam("hotelId") String hotelId,
            @RequestParam("tableId") String tableId) {

        List<Object> orders = customerOrderService.getCustomerOrdersByTable(hotelId, tableId);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/place")
    public ResponseEntity<Map<String, Object>> placeOrder(@Valid @RequestBody CustomerOrderRequest request) {
        CustomerOrder createdOrder = customerOrderService.placeCustomerOrder(
                request.getHotelId(),
                request.getTableId(),
                request.getItems(),
                request.getComment()
        );

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Order placed successfully. Awaiting waiter confirmation.",
                "orderId", createdOrder.getId()
        ));
    }
}