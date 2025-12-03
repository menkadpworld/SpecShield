package com.dpw.specshield.services;

import com.dpw.specshield.dto.TestReportResponse;

public interface IReportCollector {
    TestReportResponse getReportById(String reportId);
}
