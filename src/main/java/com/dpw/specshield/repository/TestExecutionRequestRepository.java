package com.dpw.specshield.repository;

import com.dpw.specshield.model.TestExecutionRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestExecutionRequestRepository extends MongoRepository<TestExecutionRequest, String> {
}