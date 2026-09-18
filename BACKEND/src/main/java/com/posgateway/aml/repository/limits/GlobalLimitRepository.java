package com.posgateway.aml.repository.limits;

import com.posgateway.aml.entity.limits.GlobalLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GlobalLimitRepository extends JpaRepository<GlobalLimit, Long> {
    Optional<GlobalLimit> findByNameAndPspId(String name, Long pspId);
    Optional<GlobalLimit> findByNameAndPspIdIsNull(String name);
    List<GlobalLimit> findByPspId(Long pspId);
    List<GlobalLimit> findByPspIdIsNull();
    List<GlobalLimit> findByStatus(String status);
    List<GlobalLimit> findByLimitType(String limitType);
}

