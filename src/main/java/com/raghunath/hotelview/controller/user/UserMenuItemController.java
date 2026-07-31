package com.raghunath.hotelview.controller.user;

import com.raghunath.hotelview.dto.admin.UserMenuItemResponse;
import com.raghunath.hotelview.service.user.UserMenuItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userMenuController")
@RequestMapping("/api/v1/user/menu")
@RequiredArgsConstructor
public class UserMenuItemController {

    private final UserMenuItemService userMenuService;

    @GetMapping
    public ResponseEntity<List<UserMenuItemResponse>> getMenu(@RequestParam("hotelId") String hotelId) {
        List<UserMenuItemResponse> menu = userMenuService.getMenuByHotelId(hotelId);
        return ResponseEntity.ok(menu);
    }
}