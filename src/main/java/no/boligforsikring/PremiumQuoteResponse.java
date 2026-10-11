
package no.boligforsikring;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PremiumQuoteResponse(
        UUID quoteId,
        BigDecimal monthlyPremium,
        BigDecimal annualPremium,
        String status,
        LocalDateTime expiresAt
) {

    public static PremiumQuoteResponse from(
            HouseInsuranceQuote quote,
            String status
    ) {
        return new PremiumQuoteResponse(
                quote.getId(),
                quote.getMonthlyPremium(),
                quote.getAnnualPremium(),
                status,
                quote.getExpiresAt()
        );
    }
}
