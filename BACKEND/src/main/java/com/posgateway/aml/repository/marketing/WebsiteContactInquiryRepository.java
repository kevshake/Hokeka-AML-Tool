package com.posgateway.aml.repository.marketing;

import com.posgateway.aml.entity.marketing.WebsiteContactInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link WebsiteContactInquiry} rows submitted from the marketing site.
 */
public interface WebsiteContactInquiryRepository extends JpaRepository<WebsiteContactInquiry, Long> {
}
