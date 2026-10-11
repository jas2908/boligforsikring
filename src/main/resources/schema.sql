
CREATE TABLE IF NOT EXISTS insurance_quotes (
    id UUID PRIMARY KEY,

    street_name VARCHAR(255) NOT NULL,
    house_number INT NOT NULL,    
    postal_code VARCHAR(4) NOT NULL,

    square_meters DOUBLE PRECISION NOT NULL,
    byggeaar INT NOT NULL,

    monthly_premium DECIMAL(12, 2) NOT NULL,
    annual_premium DECIMAL(12, 2) NOT NULL,

    status VARCHAR(20) NOT NULL
        CHECK (status IN ('OFFERED', 'ACCEPTED', 'EXPIRED')),

    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,

    CHECK (house_number > 0),
    CHECK (square_meters > 0),
    CHECK (monthly_premium >= 0),
    CHECK (annual_premium >= 0),
    CHECK (expires_at > created_at)
);

ALTER TABLE insurance_quotes
    ADD COLUMN IF NOT EXISTS street_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS house_number INT;

CREATE INDEX IF NOT EXISTS idx_insurance_quotes_status
    ON insurance_quotes (status);

CREATE TABLE IF NOT EXISTS offer_requests (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL REFERENCES insurance_quotes (id),
    first_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS customer_service_requests (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL REFERENCES insurance_quotes (id),
    first_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
