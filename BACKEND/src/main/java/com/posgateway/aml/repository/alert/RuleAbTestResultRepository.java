package com.posgateway.aml.repository.alert;

import com.posgateway.aml.entity.alert.RuleAbTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleAbTestResultRepository extends JpaRepository<RuleAbTestResult, Long> {
    List<RuleAbTestResult> findByTestId(Long testId);
    List<RuleAbTestResult> findByTestIdAndVariant(Long testId, String variant);
}

