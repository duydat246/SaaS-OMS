package com.saasoms.modules.dashboard.controller;

import com.saasoms.common.exception.GlobalExceptionHandler;
import com.saasoms.modules.dashboard.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void overview_defaultDays_is7() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview")).andExpect(status().isOk());

        verify(dashboardService).getOverview(7);
    }

    @Test
    void overview_daysOver90_isClamped() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview?days=999")).andExpect(status().isOk());

        verify(dashboardService).getOverview(90);
    }

    @Test
    void overview_daysZero_isClampedTo1() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview?days=0")).andExpect(status().isOk());

        verify(dashboardService).getOverview(1);
    }

    @Test
    void overview_daysNotNumber_returns400() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview?days=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tham số \"days\" có giá trị không hợp lệ"));

        verifyNoInteractions(dashboardService);
    }
}
