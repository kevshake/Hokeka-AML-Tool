package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspDirector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PspDirectorRepository extends JpaRepository<PspDirector, Long> {
    List<PspDirector> findByPspId(Long pspId);
}
