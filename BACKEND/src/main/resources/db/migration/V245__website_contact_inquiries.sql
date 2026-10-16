-- Public marketing-site demo / contact inquiries (persisted for sales follow-up).

CREATE TABLE website_contact_inquiries (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(200)  NOT NULL,
    work_email      VARCHAR(320)  NOT NULL,
    organization    VARCHAR(300),
    phone           VARCHAR(50),
    inquiry_type    VARCHAR(50)   NOT NULL DEFAULT 'DEMO',
    message         TEXT          NOT NULL,
    source          VARCHAR(100)  NOT NULL DEFAULT 'marketing-site',
    client_ip_hash  VARCHAR(64),
    user_agent      VARCHAR(500),
    status          VARCHAR(30)   NOT NULL DEFAULT 'NEW',
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_website_contact_inquiries_email ON website_contact_inquiries (work_email);
CREATE INDEX idx_website_contact_inquiries_created ON website_contact_inquiries (created_at DESC);
