package com.posgateway.aml.repository.onprem;

import com.posgateway.aml.entity.onprem.OnPremInstance;
import com.posgateway.aml.entity.onprem.OnPremInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OnPremInstanceRepository extends JpaRepository<OnPremInstance, Long> {

    Optional<OnPremInstance> findByClientId(String clientId);

    Optional<OnPremInstance> findByInstanceId(String instanceId);

    List<OnPremInstance> findByPspId(Long pspId);

    List<OnPremInstance> findByStatus(OnPremInstanceStatus status);

    boolean existsByInstanceId(String instanceId);

    boolean existsByClientId(String clientId);
}
