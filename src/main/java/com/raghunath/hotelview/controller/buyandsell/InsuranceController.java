package com.raghunath.hotelview.controller.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.CreateInsuranceRequest;
import com.raghunath.hotelview.dto.buyandsell.SyncInfoDTO;
import com.raghunath.hotelview.dto.buyandsell.UpdateInsuranceRequest;
import com.raghunath.hotelview.entity.buyandsell.Insurance;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.buyandsell.InsuranceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/buyandsell/insurances")
public class InsuranceController {

    @Autowired
    private InsuranceService insuranceService;

    @Autowired
    private UserTypeAccessGuard userTypeAccessGuard;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createInsurance(@RequestBody CreateInsuranceRequest request){
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        Insurance saved = insuranceService.createInsurance(currentDealerId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Insurance record added successfully!");
        response.put("serverVersion", saved.getVersion());
        response.put("data", saved);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateInsurance(
            @PathVariable("id") String insuranceId,
            @RequestBody UpdateInsuranceRequest request){

        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        Insurance updated = insuranceService.updateInsurance(currentDealerId, insuranceId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Insurance record updated successfully!");
        response.put("ServerVersion", updated.getVersion());
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteInsurance(@PathVariable("id") String insuranceId){
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        insuranceService.deleteInsurance(currentDealerId, insuranceId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Insurance record deleted successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sync-info")
    public ResponseEntity<Map<String, Object>> getSyncInfo(){
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        SyncInfoDTO syncInfo = insuranceService.getInsuranceSyncInfo(currentDealerId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", syncInfo);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getInsurances(
            @RequestParam(value = "sinceVersion", required = false) Long sinceVersion){
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        List<Insurance> insurances = insuranceService.getInsurances(currentDealerId, sinceVersion);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", insurances);
        return ResponseEntity.ok(response);
    }
}
