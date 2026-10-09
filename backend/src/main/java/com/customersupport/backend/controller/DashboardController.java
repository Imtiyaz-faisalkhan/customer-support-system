package com.customersupport.backend.controller;

import com.customersupport.backend.dto.DashboardMetrics;
import com.customersupport.backend.enums.MetricsPeriod;
import com.customersupport.backend.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final MetricsService metricsService;

    public DashboardController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public DashboardMetrics metrics(@RequestParam(defaultValue = "DAILY") MetricsPeriod period) {
        return metricsService.getMetrics(period);
    }
}
