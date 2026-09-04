package com.raghunath.hotelview.controller.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.CreateClientRequest;
import com.raghunath.hotelview.dto.buyandsell.SyncInfoDTO;
import com.raghunath.hotelview.entity.buyandsell.ClientRequirement;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.buyandsell.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/buyandsell/clients")
public class ClientController {

    @Autowired
    private ClientService clientService;

    @Autowired
    private UserTypeAccessGuard userTypeAccessGuard;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createClientRequirement(@RequestBody CreateClientRequest request) {
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        ClientRequirement savedClient = clientService.createClient(currentDealerId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Client requirement created successfully!");
        response.put("serverVersion", savedClient.getVersion());
        response.put("data", savedClient);

        return ResponseEntity.ok(response);
    }

    /**
     * API 1: Metadata Check Endpoint
     * GET /api/v1/buyandsell/clients/sync-info
     */
    @GetMapping("/sync-info")
    public ResponseEntity<Map<String, Object>> getSyncInfo() {
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        SyncInfoDTO syncInfo = clientService.getClientSyncInfo(currentDealerId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", syncInfo);
        return ResponseEntity.ok(response);
    }

    /**
     * API 2: Data Fetch Endpoint (Full or Incremental)
     * - GET /api/v1/buyandsell/clients
     * - GET /api/v1/buyandsell/clients?sinceVersion=5
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getClients(
            @RequestParam(value = "sinceVersion", required = false) Long sinceVersion) {

        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        List<ClientRequirement> clients = clientService.getClients(currentDealerId, sinceVersion);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", clients);
        return ResponseEntity.ok(response);
    }
}