package com.dpw.specshield.services;

import com.dpw.specshield.dto.TestReportResponse;
import com.dpw.specshield.model.TestExecution;

public interface IReportCollector {
    TestReportResponse getReportById(String reportId, int page, int size);
    TestExecution getTestCaseDetail(String reportId, String testCaseId);
}
