package com.rushcrew.product.domain.vo;

public enum Category {
    CLOTHES("옷"),
    SHOES("신발"),
    BAG("가방"),
    HEADWEAR("모자"),
    ACCESSORY("악세사리"),
    UNDERWEAR("속옷");

    private final String description;

    Category(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
