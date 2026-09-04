package com.raghunath.hotelview.controller.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.AddItemRequest;
import com.raghunath.hotelview.dto.buyandsell.SyncInfoDTO;
import com.raghunath.hotelview.dto.buyandsell.UpdateItemRequest;
import com.raghunath.hotelview.entity.buyandsell.Item;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.buyandsell.ItemService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/buyandsell/items")
public class ItemController {

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserTypeAccessGuard userTypeAccessGuard;

    @PostMapping
    public ResponseEntity<Map<String, Object>> addItem(@Valid @RequestBody AddItemRequest request) {
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        Item savedItem = itemService.addItem(currentDealerId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Item added successfully!");
        response.put("serverVersion", savedItem.getVersion());
        response.put("data", savedItem);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * API 1: Metadata Check Endpoint
     * GET /api/v1/buyandsell/items/sync-info
     */
    @GetMapping("/sync-info")
    public ResponseEntity<Map<String, Object>> getSyncInfo() {
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        SyncInfoDTO syncInfo = itemService.getItemSyncInfo(currentDealerId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", syncInfo);
        return ResponseEntity.ok(response);
    }

    /**
     * API 2: Data Fetch Endpoint (Full or Incremental)
     * - GET /api/v1/buyandsell/items
     * - GET /api/v1/buyandsell/items?sinceVersion=8
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getItems(
            @RequestParam(value = "sinceVersion", required = false) Long sinceVersion) {

        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        List<Item> items = itemService.getItems(currentDealerId, sinceVersion);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", items);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateItem(
            @PathVariable("id") String itemId,
            @RequestBody UpdateItemRequest request) {

        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        Item updatedItem = itemService.updateItem(currentDealerId, itemId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Item updated successfully!");
        response.put("serverVersion", updatedItem.getVersion());
        response.put("data", updatedItem);

        return ResponseEntity.ok(response);
    }
}