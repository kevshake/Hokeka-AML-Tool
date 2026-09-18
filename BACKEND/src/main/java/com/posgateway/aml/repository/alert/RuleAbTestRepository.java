package com.posgateway.aml.repository.alert;

import com.posgateway.aml.entity.alert.RuleAbTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleAbTestRepository extends JpaRepository<RuleAbTest, Long> {
    List<RuleAbTest> findByRuleName(String ruleName);
    List<RuleAbTest> findByStatus(String status);
}

