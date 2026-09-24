package com.raghunath.hotelview.service.customer;

import com.raghunath.hotelview.dto.customer.AssignDeliveryBoyRequest;
import com.raghunath.hotelview.dto.customer.PlaceHomeDeliveryOrderRequest;
import com.raghunath.hotelview.entity.customer.HomeDeliveryOrder;
import com.raghunath.hotelview.repository.customer.HomeDeliveryOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class HomeDeliveryOrderService {

    @Autowired
    private HomeDeliveryOrderRepository homeDeliveryOrderRepository;

    // 1. Customer places a home delivery order
    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#request.hotelId"),
            @CacheEvict(value = "orderCache", key = "#request.hotelId + '-today'")
    })
    public HomeDeliveryOrder placeOrder(String customerId, PlaceHomeDeliveryOrderRequest request) {
        LocalDateTime now = LocalDateTime.now();

        HomeDeliveryOrder order = HomeDeliveryOrder.builder()
                .hotelId(request.getHotelId())
                .orderType("HomeDelivery")
                .customerId(customerId)
                .customerName(request.getCustomerName())
                .customerMobile(request.getCustomerMobile())
                .customerAddress(request.getCustomerAddress())
                .items(request.getItems())
                .totalAmount(request.getTotalAmount())
                .comments(request.getComments())
                .status("PENDING")
                .assignedTo("yetToAssign")
                .delivered(false)
                .placedBy("CUSTOMER")
                .createdDate(now.toLocalDate().format(DateTimeFormatter.ISO_DATE))
                .createdTime(now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .updatedAt(now)
                .build();

        return homeDeliveryOrderRepository.save(order);
    }

    // 2. Fetch by status (used by the pending/accepted/completed endpoints)
    public List<HomeDeliveryOrder> getOrdersByStatus(String hotelId, String status) {
        return homeDeliveryOrderRepository.findByHotelIdAndStatus(hotelId, status);
    }

    // 3. Mark as accepted
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'")
    })
    public HomeDeliveryOrder acceptOrder(String hotelId, String orderId, String staffId) {
        HomeDeliveryOrder order = getOwnedOrder(hotelId, orderId);

        if (!"PENDING".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be accepted.");
        }

        order.setStatus("ACCEPTED");
        order.setAcceptedBy(staffId);
        order.setUpdatedAt(LocalDateTime.now());

        return homeDeliveryOrderRepository.save(order);
    }

    // 4. Mark as completed directly (no delivery boy involved)
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'")
    })
    public HomeDeliveryOrder completeOrder(String hotelId, String orderId, String staffId) {
        HomeDeliveryOrder order = getOwnedOrder(hotelId, orderId);

        if ("COMPLETED".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already completed.");
        }

        order.setStatus("COMPLETED");
        order.setCompletedBy(staffId);
        order.setUpdatedAt(LocalDateTime.now());

        return homeDeliveryOrderRepository.save(order);
    }

    // 5. Assign delivery boy -> hand-over -> order status becomes COMPLETED
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'")
    })
    public HomeDeliveryOrder assignDeliveryBoy(String hotelId, String orderId, String staffId, AssignDeliveryBoyRequest request) {
        HomeDeliveryOrder order = getOwnedOrder(hotelId, orderId);

        if ("COMPLETED".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already completed.");
        }

        HomeDeliveryOrder.DeliveryBoy deliveryBoy = HomeDeliveryOrder.DeliveryBoy.builder()
                .name(request.getName())
                .mobile(request.getMobile())
                .vehicleNumber(request.getVehicleNumber())
                .staffId(request.getStaffId())
                .build();

        order.setDeliveryBoy(deliveryBoy);
        order.setAssignedTo(request.getName()); // no longer "yetToAssign"
        order.setStatus("COMPLETED");           // hand-over = completed
        order.setCompletedBy(staffId);
        order.setUpdatedAt(LocalDateTime.now());

        return homeDeliveryOrderRepository.save(order);
    }

    // 6. Mark as delivered (final confirmation, independent of status)
    @Transactional
    public HomeDeliveryOrder markDelivered(String hotelId, String orderId, String deliveryBoyId) {
        HomeDeliveryOrder order = getOwnedOrder(hotelId, orderId);

        order.setDelivered(true);
        order.setDeliveredByDeliveryBoyId(deliveryBoyId);
        order.setUpdatedAt(LocalDateTime.now());

        return homeDeliveryOrderRepository.save(order);
    }

    private HomeDeliveryOrder getOwnedOrder(String hotelId, String orderId) {
        HomeDeliveryOrder order = homeDeliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Home delivery order not found with id: " + orderId));

        if (!order.getHotelId().equals(hotelId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order does not belong to this hotel.");
        }

        return order;
    }
}