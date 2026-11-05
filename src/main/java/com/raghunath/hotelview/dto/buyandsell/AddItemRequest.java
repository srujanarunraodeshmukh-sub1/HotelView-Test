package com.raghunath.hotelview.dto.buyandsell;

import com.raghunath.hotelview.entity.buyandsell.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AddItemRequest {

    private String dealerId;

    @NotNull(message = "categoryType is required")
    private CategoryType categoryType;

    @NotBlank(message = "title is required")
    private String title;

    @NotNull(message = "sellingPrice is required")
    private BigDecimal sellingPrice;

    private BigDecimal costPrice;
    private Integer stockQuantity = 1;
    private String condition = "Used";
    private List<String> images;

    @NotNull(message = "attributes map is required")
    private Map<String, Object> attributes;

    // Getters and Setters
    public String getDealerId() { return dealerId; }
    public void setDealerId(String dealerId) { this.dealerId = dealerId; }

    public CategoryType getCategoryType() { return categoryType; }
    public void setCategoryType(CategoryType categoryType) { this.categoryType = categoryType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}