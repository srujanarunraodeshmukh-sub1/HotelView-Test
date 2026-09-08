package com.raghunath.hotelview.dto.buyandsell;

public class SellItemRequest {
    private ClientInfoDTO client;
    private InsuranceInfoDTO insurance;

    public ClientInfoDTO getClient() { return client; }
    public void setClient(ClientInfoDTO client) { this.client = client; }
    public InsuranceInfoDTO getInsurance() { return insurance; }
    public void setInsurance(InsuranceInfoDTO insurance) { this.insurance = insurance; }
}