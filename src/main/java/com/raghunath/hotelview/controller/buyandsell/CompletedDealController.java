package com.raghunath.hotelview.controller.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.MakeDealRequest;
import com.raghunath.hotelview.dto.buyandsell.SellItemRequest;
import com.raghunath.hotelview.dto.buyandsell.SyncInfoDTO;
import com.raghunath.hotelview.entity.buyandsell.CompletedDeal;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.buyandsell.CompletedDealService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/buyandsell/completed-deals")
public class CompletedDealController {

    @Autowired
    private CompletedDealService completedDealService;

    @Autowired
    private UserTypeAccessGuard userTypeAccessGuard;

    @PostMapping("/sell/{itemId}")
    public ResponseEntity<Map<String, Object>> sellItem(
            @PathVariable("itemId") String itemId,
            @RequestBody SellItemRequest request) {

        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        CompletedDeal savedDeal = completedDealService.sellItem(currentDealerId, itemId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Item sold and deal recorded successfully!");
        response.put("serverVersion", savedDeal.getVersion());
        response.put("data", savedDeal);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/deal/{clientId}")
    public ResponseEntity<Map<String, Object>> makeDeal(
            @PathVariable("clientId") String clientId,
            @RequestBody MakeDealRequest request) {

        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        CompletedDeal savedDeal = completedDealService.makeDeal(currentDealerId, clientId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Deal made and recorded successfully!");
        response.put("serverVersion", savedDeal.getVersion());
        response.put("data", savedDeal);

        return ResponseEntity.ok(response);
    }

    /**
     * API 1: Metadata Check Endpoint
     * GET /api/v1/buyandsell/completed-deals/sync-info
     */
    @GetMapping("/sync-info")
    public ResponseEntity<Map<String, Object>> getSyncInfo() {
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        SyncInfoDTO syncInfo = completedDealService.getCompletedDealSyncInfo(currentDealerId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", syncInfo);
        return ResponseEntity.ok(response);
    }

    /**
     * API 2: Data Fetch Endpoint (Full or Incremental)
     * - GET /api/v1/buyandsell/completed-deals
     * - GET /api/v1/buyandsell/completed-deals?sinceVersion=8
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCompletedDeals(
            @RequestParam(value = "sinceVersion", required = false) Long sinceVersion) {

        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        List<CompletedDeal> deals = completedDealService.getCompletedDeals(currentDealerId, sinceVersion);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", deals);
        return ResponseEntity.ok(response);
    }
}