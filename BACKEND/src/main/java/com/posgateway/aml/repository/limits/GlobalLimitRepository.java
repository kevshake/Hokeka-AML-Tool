package com.posgateway.aml.repository.limits;

import com.posgateway.aml.entity.limits.GlobalLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GlobalLimitRepository extends JpaRepository<GlobalLimit, Long> {
    Optional<GlobalLimit> findByName(String name);
    List<GlobalLimit> findByStatus(String status);
    List<GlobalLimit> findByLimitType(String limitType);
}

