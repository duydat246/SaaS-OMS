package com.saasoms.modules.dashboard.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse.CategoryAmount;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse.CategoryKind;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse.DayRevenue;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse.TopProduct;
import com.saasoms.modules.dashboard.repository.DashboardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    /** Ngày trên dashboard luôn là ngày Việt Nam, không phụ thuộc múi giờ của máy chủ. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_NAMED_CATEGORIES = 5;
    private static final int TOP_PRODUCT_LIMIT = 5;

    private final DashboardRepository dashboardRepository;
    /** Zone của clock là múi giờ JVM: giá trị thời gian trong orders do JVM ghi theo wall clock của nó. */
    private final Clock clock;

    @Autowired
    public DashboardService(DashboardRepository dashboardRepository) {
        this(dashboardRepository, Clock.system(ZoneId.systemDefault()));
    }

    DashboardService(DashboardRepository dashboardRepository, Clock clock) {
        this.dashboardRepository = dashboardRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview(int days) {
        Long tenantId = requireTenant();

        Instant now = clock.instant();
        ZoneId jvmZone = clock.getZone();
        LocalDate toDate = now.atZone(BUSINESS_ZONE).toLocalDate();
        LocalDate fromDate = toDate.minusDays(days - 1L);
        LocalDateTime from = toJvmWallClock(fromDate, jvmZone);
        LocalDateTime to = toJvmWallClock(toDate.plusDays(1), jvmZone);
        int offsetMinutes = (BUSINESS_ZONE.getRules().getOffset(now).getTotalSeconds()
                - jvmZone.getRules().getOffset(now).getTotalSeconds()) / 60;

        return DashboardOverviewResponse.builder()
                .days(days)
                .timezone(BUSINESS_ZONE.getId())
                .fromDate(fromDate.toString())
                .toDate(toDate.toString())
                .revenueByDay(fillDays(fromDate, days,
                        dashboardRepository.sumByDay(tenantId, from, to, offsetMinutes)))
                .categories(groupCategories(dashboardRepository.sumByCategory(tenantId, from, to)))
                .topProducts(dashboardRepository
                        .findTopProducts(tenantId, from, to, PageRequest.of(0, TOP_PRODUCT_LIMIT)).stream()
                        .map(DashboardService::toTopProduct)
                        .toList())
                .build();
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }
        return tenantId;
    }

    private static LocalDateTime toJvmWallClock(LocalDate businessDate, ZoneId jvmZone) {
        return businessDate.atStartOfDay(BUSINESS_ZONE).withZoneSameInstant(jvmZone).toLocalDateTime();
    }

    private static List<DayRevenue> fillDays(LocalDate fromDate, int days, List<DashboardRepository.DayBucketRow> rows) {
        Map<String, DashboardRepository.DayBucketRow> byDate = rows.stream()
                .collect(Collectors.toMap(DashboardRepository.DayBucketRow::getBucket, Function.identity()));

        List<DayRevenue> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String date = fromDate.plusDays(i).toString();
            DashboardRepository.DayBucketRow row = byDate.get(date);
            result.add(row == null
                    ? new DayRevenue(date, BigDecimal.ZERO, BigDecimal.ZERO)
                    : new DayRevenue(date, row.getDeliveredRevenue(), row.getOpenAmount()));
        }
        return result;
    }

    private static List<CategoryAmount> groupCategories(List<DashboardRepository.CategoryRow> rows) {
        List<CategoryAmount> sorted = rows.stream()
                .map(DashboardService::toCategoryAmount)
                .sorted(Comparator.comparing(CategoryAmount::getAmount).reversed()
                        .thenComparing(CategoryAmount::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (sorted.size() <= MAX_NAMED_CATEGORIES) {
            return sorted;
        }

        List<CategoryAmount> rest = sorted.subList(MAX_NAMED_CATEGORIES, sorted.size());
        BigDecimal otherAmount = rest.stream().map(CategoryAmount::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long otherQuantity = rest.stream().mapToLong(CategoryAmount::getQuantity).sum();

        List<CategoryAmount> result = new ArrayList<>(sorted.subList(0, MAX_NAMED_CATEGORIES));
        result.add(new CategoryAmount(CategoryKind.OTHER, null, otherAmount, otherQuantity));
        return result;
    }

    private static CategoryAmount toCategoryAmount(DashboardRepository.CategoryRow row) {
        CategoryKind kind = row.getCategory() == null ? CategoryKind.UNCATEGORIZED : CategoryKind.CATEGORY;
        return new CategoryAmount(kind, row.getCategory(), row.getAmount(), row.getQuantity());
    }

    private static TopProduct toTopProduct(DashboardRepository.TopProductRow row) {
        String name = row.getProductName() != null ? row.getProductName() : row.getSnapshotName();
        return new TopProduct(row.getProductId(), name, row.getQuantity(), row.getAmount());
    }
}
