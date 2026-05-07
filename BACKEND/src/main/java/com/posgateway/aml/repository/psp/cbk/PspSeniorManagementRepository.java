package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspSeniorManagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PspSeniorManagementRepository extends JpaRepository<PspSeniorManagement, Long> {
    List<PspSeniorManagement> findByPspId(Long pspId);
}
