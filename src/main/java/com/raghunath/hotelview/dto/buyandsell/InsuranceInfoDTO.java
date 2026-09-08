package com.raghunath.hotelview.dto.buyandsell;

public class InsuranceInfoDTO {
    private String name;
    private String mobile;
    private String lapseDate;
    private String vehicleNumber;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getLapseDate() { return lapseDate; }
    public void setLapseDate(String lapseDate) { this.lapseDate = lapseDate; }
    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
}