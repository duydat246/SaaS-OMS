package com.saasoms.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardOverviewResponse {

    private int days;
    private String timezone;
    /** Ngày theo giờ Việt Nam, dạng yyyy-MM-dd. */
    private String fromDate;
    private String toDate;
    private List<DayRevenue> revenueByDay;
    private List<CategoryAmount> categories;
    private List<TopProduct> topProducts;

    public enum CategoryKind {
        CATEGORY, UNCATEGORIZED, OTHER
    }

    @Data
    @AllArgsConstructor
    public static class DayRevenue {
        private String date;
        private BigDecimal deliveredRevenue;
        private BigDecimal openAmount;
    }

    @Data
    @AllArgsConstructor
    public static class CategoryAmount {
        private CategoryKind kind;
        /** Null khi kind không phải CATEGORY, frontend tự gắn nhãn. */
        private String name;
        private BigDecimal amount;
        private long quantity;
    }

    @Data
    @AllArgsConstructor
    public static class TopProduct {
        /** Null với item cũ không có sku_id. */
        private Long productId;
        private String name;
        private long quantity;
        private BigDecimal amount;
    }
}
