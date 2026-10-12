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
    public ResponseEntity<CreditCustomerDetailsResponse>
    getCustomerDetails(
            @PathVariable String customerId
    ) {
        String hotelId = getHotelId();

        return ResponseEntity.ok(
                creditBillService.getCustomerDetails(
                        hotelId,
                        customerId
                )
        );
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