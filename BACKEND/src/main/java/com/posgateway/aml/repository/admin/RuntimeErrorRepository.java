package com.posgateway.aml.repository.admin;

import com.posgateway.aml.entity.admin.RuntimeError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeErrorRepository extends JpaRepository<RuntimeError, Long> {

    Page<RuntimeError> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Page<RuntimeError> findByPspIdOrderByOccurredAtDesc(Long pspId, Pageable pageable);
}
