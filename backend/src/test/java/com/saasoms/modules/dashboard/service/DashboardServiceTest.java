package com.saasoms.modules.dashboard.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse.CategoryAmount;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse.CategoryKind;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse.DayRevenue;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse.TopProduct;
import com.saasoms.modules.dashboard.repository.DashboardRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 10:00 ngày 04/11/2026 giờ Việt Nam. */
    private static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");

    @Mock
    private DashboardRepository dashboardRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = serviceAt(NOW, ZoneOffset.UTC);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getOverview_noOrders_returnsZeroFilledDaysAndEmptyLists() {
        DashboardOverviewResponse response = service.getOverview(7);

        assertThat(response.getDays()).isEqualTo(7);
        assertThat(response.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(response.getFromDate()).isEqualTo("2026-10-29");
        assertThat(response.getToDate()).isEqualTo("2026-11-04");
        assertThat(response.getRevenueByDay()).hasSize(7).allSatisfy(day -> {
            assertThat(day.getDeliveredRevenue()).isEqualByComparingTo("0");
            assertThat(day.getOpenAmount()).isEqualByComparingTo("0");
        });
        assertThat(response.getCategories()).isEmpty();
        assertThat(response.getTopProducts()).isEmpty();
    }

    @Test
    void getOverview_fillsMissingDaysInOrder() {
        when(dashboardRepository.sumByDay(eq(TENANT_ID), any(), any(), anyInt())).thenReturn(List.of(
                dayRow("2026-11-01", "500000.00", "20000.00"),
                dayRow("2026-11-03", "100000.00", "0.00")));

        List<DayRevenue> days = service.getOverview(7).getRevenueByDay();

        assertThat(days).extracting(DayRevenue::getDate).containsExactly(
                "2026-10-29", "2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04");
        assertThat(days.get(3).getDeliveredRevenue()).isEqualByComparingTo("500000.00");
        assertThat(days.get(3).getOpenAmount()).isEqualByComparingTo("20000.00");
        assertThat(days.get(4).getDeliveredRevenue()).isEqualByComparingTo("0");
        assertThat(days.get(5).getDeliveredRevenue()).isEqualByComparingTo("100000.00");
    }

    @Test
    void getOverview_utcJvm_usesVietnamDayBoundaries() {
        service.getOverview(7);

        verify(dashboardRepository).sumByDay(TENANT_ID,
                LocalDateTime.parse("2026-10-28T17:00:00"), LocalDateTime.parse("2026-11-04T17:00:00"), 420);
        verify(dashboardRepository).sumByCategory(TENANT_ID,
                LocalDateTime.parse("2026-10-28T17:00:00"), LocalDateTime.parse("2026-11-04T17:00:00"));
    }

    @Test
    void getOverview_utcJvm_todayFollowsVietnamMidnight() {
        DashboardOverviewResponse before =
                serviceAt(Instant.parse("2026-11-03T16:59:59Z"), ZoneOffset.UTC).getOverview(1);
        DashboardOverviewResponse after =
                serviceAt(Instant.parse("2026-11-03T17:00:00Z"), ZoneOffset.UTC).getOverview(1);

        assertThat(before.getToDate()).isEqualTo("2026-11-03");
        assertThat(after.getToDate()).isEqualTo("2026-11-04");
    }

    @Test
    void getOverview_vietnamJvm_usesZeroOffset() {
        serviceAt(NOW, VIETNAM).getOverview(7);

        verify(dashboardRepository).sumByDay(TENANT_ID,
                LocalDateTime.parse("2026-10-29T00:00:00"), LocalDateTime.parse("2026-11-05T00:00:00"), 0);
    }

    @Test
    void getOverview_moreThanFiveCategories_groupsIntoOther() {
        when(dashboardRepository.sumByCategory(eq(TENANT_ID), any(), any())).thenReturn(List.of(
                categoryRow("G", 7, "1.00"),
                categoryRow("A", 1, "700.00"),
                categoryRow("B", 2, "600.00"),
                categoryRow("C", 3, "500.00"),
                categoryRow("D", 4, "400.00"),
                categoryRow("E", 5, "300.00"),
                categoryRow("F", 6, "200.00")));

        List<CategoryAmount> categories = service.getOverview(7).getCategories();

        assertThat(categories).hasSize(6);
        assertThat(categories).extracting(CategoryAmount::getKind, CategoryAmount::getName).containsExactly(
                tuple(CategoryKind.CATEGORY, "A"),
                tuple(CategoryKind.CATEGORY, "B"),
                tuple(CategoryKind.CATEGORY, "C"),
                tuple(CategoryKind.CATEGORY, "D"),
                tuple(CategoryKind.CATEGORY, "E"),
                tuple(CategoryKind.OTHER, null));
        assertThat(categories.get(5).getAmount()).isEqualByComparingTo("201.00");
        assertThat(categories.get(5).getQuantity()).isEqualTo(13);
    }

    @Test
    void getOverview_otherLargerThanLastGroup_staysLast() {
        when(dashboardRepository.sumByCategory(eq(TENANT_ID), any(), any())).thenReturn(List.of(
                categoryRow("A", 1, "100.00"),
                categoryRow("B", 1, "100.00"),
                categoryRow("C", 1, "100.00"),
                categoryRow("D", 1, "100.00"),
                categoryRow("E", 1, "100.00"),
                categoryRow("F", 1, "90.00"),
                categoryRow("G", 1, "90.00")));

        List<CategoryAmount> categories = service.getOverview(7).getCategories();

        assertThat(categories.get(5).getKind()).isEqualTo(CategoryKind.OTHER);
        assertThat(categories.get(5).getAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    void getOverview_nullCategory_mapsToUncategorized() {
        when(dashboardRepository.sumByCategory(eq(TENANT_ID), any(), any())).thenReturn(List.of(
                categoryRow("Áo", 1, "100.00"),
                categoryRow(null, 2, "900.00")));

        List<CategoryAmount> categories = service.getOverview(7).getCategories();

        assertThat(categories).extracting(CategoryAmount::getKind, CategoryAmount::getName).containsExactly(
                tuple(CategoryKind.UNCATEGORIZED, null),
                tuple(CategoryKind.CATEGORY, "Áo"));
    }

    @Test
    void getOverview_legacyItem_hasNullProductIdAndSnapshotName() {
        when(dashboardRepository.findTopProducts(eq(TENANT_ID), any(), any(), any())).thenReturn(List.of(
                topRow(12L, "Tên mới", "Tên cũ", 8, "800000.00"),
                topRow(null, null, "Sản phẩm cũ", 2, "50000.00")));

        List<TopProduct> top = service.getOverview(7).getTopProducts();

        assertThat(top).extracting(TopProduct::getProductId, TopProduct::getName, TopProduct::getQuantity).containsExactly(
                tuple(12L, "Tên mới", 8L),
                tuple(null, "Sản phẩm cũ", 2L));
    }

    @Test
    void getOverview_topProducts_requestsFiveRows() {
        service.getOverview(7);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(dashboardRepository).findTopProducts(eq(TENANT_ID), any(), any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getOverview_negativeRevenue_isNotClamped() {
        when(dashboardRepository.sumByDay(eq(TENANT_ID), any(), any(), anyInt())).thenReturn(List.of(
                dayRow("2026-11-04", "-5000.00", "-100.00")));

        List<DayRevenue> days = service.getOverview(7).getRevenueByDay();

        assertThat(days.get(6).getDeliveredRevenue()).isEqualByComparingTo("-5000.00");
        assertThat(days.get(6).getOpenAmount()).isEqualByComparingTo("-100.00");
    }

    @Test
    void getOverview_usesTenantFromContext() {
        TenantContext.set(2L);

        service.getOverview(7);

        verify(dashboardRepository).sumByDay(eq(2L), any(), any(), anyInt());
        verify(dashboardRepository).sumByCategory(eq(2L), any(), any());
        verify(dashboardRepository).findTopProducts(eq(2L), any(), any(), any());
    }

    @Test
    void getOverview_missingTenant_throws403() {
        TenantContext.clear();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getOverview(7));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(dashboardRepository);
    }

    // ---------------------------------------------------------------- FIXTURE

    private DashboardService serviceAt(Instant instant, ZoneId jvmZone) {
        return new DashboardService(dashboardRepository, Clock.fixed(instant, jvmZone));
    }

    private static DashboardRepository.DayBucketRow dayRow(String bucket, String delivered, String open) {
        return new DashboardRepository.DayBucketRow() {
            @Override
            public String getBucket() {
                return bucket;
            }

            @Override
            public BigDecimal getDeliveredRevenue() {
                return new BigDecimal(delivered);
            }

            @Override
            public BigDecimal getOpenAmount() {
                return new BigDecimal(open);
            }
        };
    }

    private static DashboardRepository.CategoryRow categoryRow(String category, long quantity, String amount) {
        return new DashboardRepository.CategoryRow() {
            @Override
            public String getCategory() {
                return category;
            }

            @Override
            public Long getQuantity() {
                return quantity;
            }

            @Override
            public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private static DashboardRepository.TopProductRow topRow(Long productId, String productName, String snapshotName,
                                                            long quantity, String amount) {
        return new DashboardRepository.TopProductRow() {
            @Override
            public Long getProductId() {
                return productId;
            }

            @Override
            public String getProductName() {
                return productName;
            }

            @Override
            public String getSnapshotName() {
                return snapshotName;
            }

            @Override
            public Long getQuantity() {
                return quantity;
            }

            @Override
            public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }
        };
    }
}
