package com.raghunath.hotelview.dto.buyandsell;

import com.raghunath.hotelview.entity.buyandsell.CategoryType;
import com.raghunath.hotelview.entity.buyandsell.ItemStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class UpdateItemRequest {

    private String categoryId;
    private CategoryType categoryType;
    private String title;
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private Integer stockQuantity;
    private String condition;
    private List<String> images;
    private ItemStatus status;
    private Map<String, Object> attributes;

    // Getters and Setters
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

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

    public ItemStatus getStatus() { return status; }
    public void setStatus(ItemStatus status) { this.status = status; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}