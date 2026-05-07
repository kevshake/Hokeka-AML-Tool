package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PspProductRepository extends JpaRepository<PspProduct, Long> {
    List<PspProduct> findByPspId(Long pspId);
    List<PspProduct> findByPspIdAndStatusCode(Long pspId, String statusCode);
}
