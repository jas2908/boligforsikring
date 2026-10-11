package no.boligforsikring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InsuranceQuoteRepository {

    private final JdbcTemplate db;

    public InsuranceQuoteRepository(JdbcTemplate db) {
        this.db = db;
    }

    public void save(HouseInsuranceQuote quote) {
        String sql = """
            INSERT INTO insurance_quotes (
                id,
                street_name,
                house_number,
                postal_code,
                square_meters,
                byggeaar,
                monthly_premium,
                annual_premium,
                status,
                created_at,
                expires_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        db.update(
                sql,
                quote.getId(),
                quote.getStreetName(),
                quote.getHouseNumber(),
                quote.getPostalCode(),
                quote.getSquareMeters(),
                quote.getByggeaar(),
                quote.getMonthlyPremium(),
                quote.getAnnualPremium(),
                quote.getStatus(),
                quote.getCreatedAt(),
                quote.getExpiresAt()
        );
    }

    public void saveOfferRequest(UUID quoteId, ContactRequest request) {
        String sql = """
            INSERT INTO offer_requests (
                id,
                quote_id,
                first_name,
                email,
                phone,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        db.update(
                sql,
                UUID.randomUUID(),
                quoteId,
                request.firstName().trim(),
                request.email().trim(),
                cleanPhone(request.phone()),
                LocalDateTime.now()
        );
    }

    public void saveCustomerServiceRequest(
            UUID quoteId,
            ContactRequest request
    ) {
        String sql = """
            INSERT INTO customer_service_requests (
                id,
                quote_id,
                first_name,
                email,
                phone,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        db.update(
                sql,
                UUID.randomUUID(),
                quoteId,
                request.firstName().trim(),
                request.email().trim(),
                request.phone().trim(),
                LocalDateTime.now()
        );
    }

    private String cleanPhone(String phone) {
        return phone == null || phone.isBlank()
                ? null
                : phone.trim();
    }

    public Optional<HouseInsuranceQuote> findById(UUID id) {
        String sql = """
            SELECT
                id,
                street_name,
                house_number,
                postal_code,
                square_meters,
                byggeaar,
                monthly_premium,
                annual_premium,
                status,
                created_at,
                expires_at
            FROM insurance_quotes
            WHERE id = ?
            """;

        List<HouseInsuranceQuote> quotes = db.query(
                sql,
                (rs, rowNum) -> {
                    HouseInsuranceQuote quote = new HouseInsuranceQuote();

                    quote.setId(rs.getObject("id", UUID.class));
                    quote.setStreetName(rs.getString("street_name"));
                    quote.setHouseNumber(rs.getInt("house_number"));
                    quote.setPostalCode(rs.getString("postal_code"));
                    quote.setSquareMeters(rs.getDouble("square_meters"));
                    quote.setByggeaar(rs.getInt("byggeaar"));
                    quote.setMonthlyPremium(
                            rs.getBigDecimal("monthly_premium")
                    );
                    quote.setAnnualPremium(
                            rs.getBigDecimal("annual_premium")
                    );
                    quote.setStatus(rs.getString("status"));
                    quote.setCreatedAt(
                            rs.getTimestamp("created_at").toLocalDateTime()
                    );
                    quote.setExpiresAt(
                            rs.getTimestamp("expires_at").toLocalDateTime()
                    );

                    return quote;
                },
                id
        );

        return quotes.stream().findFirst();
    }

    public int expireQuote(UUID id) {
        String sql = """
            UPDATE insurance_quotes
            SET status = 'EXPIRED'
            WHERE id = ?
              AND status = 'OFFERED'
              AND expires_at <= CURRENT_TIMESTAMP
            """;

        return db.update(sql, id);
    }
}
