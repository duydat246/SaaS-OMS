package com.saasoms.modules.dashboard.controller;

import com.saasoms.common.response.ApiResponse;
import com.saasoms.modules.dashboard.dto.DashboardOverviewResponse;
import com.saasoms.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Tổng quan dashboard theo kỳ: doanh thu theo ngày, danh mục và sản phẩm bán chạy")
    public ApiResponse<DashboardOverviewResponse> overview(@RequestParam(defaultValue = "7") int days) {
        int safeDays = Math.min(Math.max(days, 1), 90);
        return ApiResponse.ok(dashboardService.getOverview(safeDays));
    }
}
