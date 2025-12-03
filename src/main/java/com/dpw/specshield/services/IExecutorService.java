package com.dpw.specshield.services;

import com.dpw.specshield.model.TestSuite;
import java.util.concurrent.CompletableFuture;

public interface IExecutorService {
    CompletableFuture<String> executeTestSuite(TestSuite testSuite);
}
