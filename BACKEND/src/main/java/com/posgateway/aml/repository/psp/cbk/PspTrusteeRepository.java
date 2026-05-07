package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspTrustee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PspTrusteeRepository extends JpaRepository<PspTrustee, Long> {
    List<PspTrustee> findByPspId(Long pspId);
}
