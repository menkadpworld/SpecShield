package com.dpw.specshield.repository;

import com.dpw.specshield.model.TestResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestResultRepository extends MongoRepository<TestResult, String> {
}