package com.raghunath.hotelview.service.restaurantandinstantuser;

import com.raghunath.hotelview.dto.restaurantandinstant.UserMenuItemResponse;
import com.raghunath.hotelview.entity.restaurantandinstant.Admin;
import com.raghunath.hotelview.entity.restaurantandinstant.MenuItem;
import com.raghunath.hotelview.repository.restaurantandinstant.AdminRepository;
import com.raghunath.hotelview.repository.restaurantandinstant.UserMenuItemRepository; // 🌟 Using your new repository
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service("userMenuItemService")
@RequiredArgsConstructor
public class UserMenuItemService {

    private final UserMenuItemRepository userMenuItemRepository; // 🌟 Injected
    private final AdminRepository adminRepository;

    private ZonedDateTime getISTNow() {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
    }

    @Cacheable(value = "menuCache", key = "'user_' + #hotelId")
    public List<UserMenuItemResponse> getMenuByHotelId(String hotelId) {
        if (!StringUtils.hasText(hotelId)) {
            throw new IllegalArgumentException("Hotel ID is required");
        }

        String cleanHotelId = hotelId.trim();
        log.info("⚡ [CACHE MISS] Fetching user menu from DB for hotelId: {}", cleanHotelId);

        // 1. Validate Admin & Subscription
        Admin admin = adminRepository.findByHotelId(cleanHotelId)
                .orElseThrow(() -> new RuntimeException("Invalid QR Code: Restaurant not found"));

        if (admin.getSubscriptionExpiry() != null &&
                getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Service temporarily disabled for this restaurant");
        }

        if (admin.getPlanType() == null || !"PREMIUM".equalsIgnoreCase(admin.getPlanType().trim())) {
            throw new RuntimeException("Upgrade to premium plan");
        }

        // 2. Fetch available menu items
        List<MenuItem> menuItems = userMenuItemRepository.findByHotelIdAndIsAvailableTrue(cleanHotelId);

        if (menuItems == null || menuItems.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Map to lightweight DTO (trimming internal metrics)
        return menuItems.stream()
                .map(UserMenuItemResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "menuCache", key = "'user_' + #hotelId")
    public void evictUserMenuCache(String hotelId) {
        log.info("🧹 [CACHE EVICT] Cleared user menu cache for hotelId: {}", hotelId);
    }
}