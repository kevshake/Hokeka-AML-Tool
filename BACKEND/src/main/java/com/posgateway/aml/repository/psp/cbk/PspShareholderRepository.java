package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspShareholder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PspShareholderRepository extends JpaRepository<PspShareholder, Long> {
    List<PspShareholder> findByPspId(Long pspId);
}
