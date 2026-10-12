package com.raghunath.hotelview.controller.restaurantandinstantadmin;

import com.raghunath.hotelview.dto.restaurantandinstant.credit.*;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.restaurantandinstantadmin.InstantCreditBillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.raghunath.hotelview.dto.restaurantandinstant.credit.CreditCustomerChangesResponse;
import com.raghunath.hotelview.dto.restaurantandinstant.credit.CreditCustomerPageResponse;
import com.raghunath.hotelview.dto.restaurantandinstant.credit.CreditCustomerVersionResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/instant/credit-bills")
@RequiredArgsConstructor
public class InstantCreditBillController {

    private final InstantCreditBillService creditBillService;

    private final UserTypeAccessGuard userTypeAccessGuard;


    // =========================================================
    // AUTHENTICATION
    // =========================================================

    private String getHotelId() {

        return userTypeAccessGuard
                .enforceUserTypeForCurrentUser(
                        "INSTANT"
                );
    }

    // ============================================================
    // API 1: CUSTOMER VERSION AND COUNT
    // GET /api/v1/instant/credit-bills/customers/version
    // ============================================================

    @GetMapping("/customers/version")
    public ResponseEntity<CreditCustomerVersionResponse>
    getCustomerVersion() {

        String hotelId = getHotelId();

        userTypeAccessGuard
                .enforceUserTypeForCurrentUser("INSTANT");

        return ResponseEntity.ok(
                creditBillService.getCustomerVersion(hotelId)
        );
    }


    // ============================================================
    // API 2: CUSTOMERS CHANGED AFTER TIMESTAMP
    // GET /api/v1/instant/credit-bills/customers/changes
    // ============================================================

    @GetMapping("/customers/changes")
    public ResponseEntity<CreditCustomerChangesResponse>
    getCustomerChanges(
            @RequestParam String afterVersion
    ) {
        String hotelId = getHotelId();

        userTypeAccessGuard
                .enforceUserTypeForCurrentUser("INSTANT");

        return ResponseEntity.ok(
                creditBillService.getCustomerChanges(
                        hotelId,
                        afterVersion
                )
        );
    }


    // ============================================================
    // API 3: PAGINATED CUSTOMERS
    // GET /api/v1/instant/credit-bills/customers/page
    // ============================================================

    @GetMapping("/customers/page")
    public ResponseEntity<CreditCustomerPageResponse>
    getCustomerPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String hotelId = getHotelId();

        userTypeAccessGuard
                .enforceUserTypeForCurrentUser("INSTANT");

        return ResponseEntity.ok(
                creditBillService.getCustomerPage(
                        hotelId,
                        page,
                        size
                )
        );
    }


    // =========================================================
    // 1. CREATE CREDIT BILL
    // =========================================================

    @PostMapping
    public ResponseEntity<CreditBillResponse> createCreditBill(
            @Valid @RequestBody CreateCreditBillRequest request
    ) {

        String hotelId = getHotelId();

        CreditBillResponse response =
                creditBillService.createCreditBill(
                        hotelId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // =========================================================
    // 2. ACTIVE CREDIT CUSTOMERS
    // =========================================================

    @GetMapping("/customers")
    public ResponseEntity<List<CreditCustomerResponse>>
    getActiveCustomers() {

        String hotelId = getHotelId();

        return ResponseEntity.ok(
                creditBillService.getActiveCustomers(hotelId)
        );
    }


    // =========================================================
    // 3. CUSTOMER ACTIVE BILLS
    // =========================================================


    @GetMapping("/customers/{customerId}")
    public ResponseEntity<CreditCustomerDetailsResponse> getCustomerDetails(
            @PathVariable String customerId
    ) {
        String hotelId = getHotelId();

        userTypeAccessGuard.enforceUserTypeForCurrentUser("INSTANT");

        CreditCustomerDetailsResponse response =
                creditBillService.getCustomerDetails(
                        hotelId,
                        customerId
                );

        return ResponseEntity.ok(response);
    }



    // =========================================================
    // 4. PENDING TRANSACTIONS
    // =========================================================

    @GetMapping("/pending-transactions")
    public ResponseEntity<List<PendingTransactionResponse>>
    getPendingTransactions() {

        String hotelId = getHotelId();

        return ResponseEntity.ok(
                creditBillService.getPendingTransactions(hotelId)
        );
    }


    // =========================================================
    // 5. RECEIVE PAYMENT
    // =========================================================

    @PostMapping("/customers/{customerId}/payments")
    public ResponseEntity<ReceivePaymentResponse>
    receivePayment(
            @PathVariable String customerId,
            @Valid @RequestBody ReceivePaymentRequest request
    ) {

        String hotelId = getHotelId();

        return ResponseEntity.ok(
                creditBillService.receivePayment(
                        hotelId,
                        customerId,
                        request
                )
        );
    }


    // =========================================================
    // 6. EDIT BILL
    // =========================================================

    @PutMapping("/{billId}")
    public ResponseEntity<CreditBillResponse>
    editCreditBill(
            @PathVariable String billId,
            @Valid @RequestBody EditCreditBillRequest request
    ) {

        String hotelId = getHotelId();

        return ResponseEntity.ok(
                creditBillService.editCreditBill(
                        hotelId,
                        billId,
                        request
                )
        );
    }


    // =========================================================
    // 7. CUSTOMER HISTORY
    // =========================================================

    @GetMapping("/history/customers/{customerId}")
    public ResponseEntity<List<CreditHistoryResponse>>
    getCustomerHistory(
            @PathVariable String customerId
    ) {

        String hotelId = getHotelId();

        return ResponseEntity.ok(
                creditBillService.getCustomerHistory(
                        hotelId,
                        customerId
                )
        );
    }


    // =========================================================
    // 8. ALL HISTORY
    // =========================================================

    @GetMapping("/history")
    public ResponseEntity<List<CreditHistoryResponse>>
    getAllHistory() {

        String hotelId = getHotelId();

        return ResponseEntity.ok(
                creditBillService.getAllHistory(hotelId)
        );
    }
}