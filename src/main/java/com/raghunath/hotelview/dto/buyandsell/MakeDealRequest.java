package com.raghunath.hotelview.dto.buyandsell;

public class MakeDealRequest {
    private String itemId;
    private InsuranceInfoDTO insurance;

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public InsuranceInfoDTO getInsurance() { return insurance; }
    public void setInsurance(InsuranceInfoDTO insurance) { this.insurance = insurance; }
}