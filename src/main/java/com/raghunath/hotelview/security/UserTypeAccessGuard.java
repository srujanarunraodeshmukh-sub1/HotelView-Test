package com.raghunath.hotelview.security;

import com.raghunath.hotelview.entity.restaurantandinstant.Admin;
import com.raghunath.hotelview.repository.restaurantandinstant.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * 🌟 Shared account-type access gate.
 *
 * Any controller can inject this and call enforceUserType(hotelId, "RESTAURANT", ...)
 * at the top of an endpoint to restrict it to specific account types
 * (RESTAURANT / INSTANT / CAR). Centralized here so the check lives in ONE
 * place instead of being copy-pasted into every controller — if the lookup
 * strategy ever changes (e.g. reading userType from the JWT claims instead
 * of re-querying Mongo), it only needs to change here.
 */
@Component
@RequiredArgsConstructor
public class UserTypeAccessGuard {

    private final AdminRepository adminRepository;

    public String currentHotelId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /**
     * Throws 401 if the admin can't be found, or 403 if their stored userType
     * isn't one of the allowed types for this endpoint.
     */
    public void enforceUserType(String hotelId, String... allowedTypes) {
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin not found"));

        String userType = admin.getUserType() != null ? admin.getUserType().trim().toUpperCase() : null;
        Set<String> allowed = Set.of(allowedTypes);

        if (userType == null || !allowed.contains(userType)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This action is not permitted for your account type.");
        }
    }

    /**
     * Convenience overload: resolves the current hotelId from the security
     * context itself, so callers don't need their own currentHotelId() helper.
     * Returns the resolved hotelId so it can still be reused for the rest of
     * the endpoint body.
     */
    public String enforceUserTypeForCurrentUser(String... allowedTypes) {
        String hotelId = currentHotelId();
        enforceUserType(hotelId, allowedTypes);
        return hotelId;
    }
}