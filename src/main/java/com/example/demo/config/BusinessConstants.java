package com.example.demo.config;

import java.math.BigDecimal;

public final class BusinessConstants {

    private BusinessConstants() {}

    // Per-diem rates
    public static final BigDecimal PER_DIEM_DOMESTIC = new BigDecimal("25.00");
    public static final BigDecimal PER_DIEM_INTERNATIONAL = new BigDecimal("50.00");

    // Policy limits (must match frontend lib/constants.ts)
    public static final BigDecimal MILEAGE_RATE = new BigDecimal("0.67");
    public static final BigDecimal MEAL_LUNCH_AMOUNT = new BigDecimal("25.00");
    public static final BigDecimal MEAL_DINNER_AMOUNT = new BigDecimal("50.00");
    public static final BigDecimal MAX_ITEM_AMOUNT = new BigDecimal("999999.99");
}
