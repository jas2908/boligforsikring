
package no.boligforsikring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;

public class PremiumCalculator {

    private static final BigDecimal PRICE_PER_SQUARE_METER =
            BigDecimal.valueOf(60);

    public PremiumResult calculate(double squareMeters, int buildingYear) {

        int currentYear = Year.now().getValue();

        if (!Double.isFinite(squareMeters) || squareMeters <= 0) {
            throw new IllegalArgumentException(
                    "Boligareal må være større enn null."
            );
        }

        if (buildingYear < 1800 || buildingYear > currentYear) {
            throw new IllegalArgumentException(
                    "Ugyldig byggeår."
            );
        }

        int buildingAge = currentYear - buildingYear;

        BigDecimal ageMultiplier = getAgeMultiplier(buildingAge);

        BigDecimal basePremium = BigDecimal.valueOf(squareMeters)
                .multiply(PRICE_PER_SQUARE_METER);

        BigDecimal annualPremium = basePremium
                .multiply(ageMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal monthlyPremium = annualPremium
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        return new PremiumResult(
                basePremium.setScale(2, RoundingMode.HALF_UP),
                ageMultiplier,
                annualPremium,
                monthlyPremium
        );
    }

    private BigDecimal getAgeMultiplier(int age) {
        if (age <= 10) {
            return BigDecimal.valueOf(1.00);
        }
        if (age <= 30) {
            return BigDecimal.valueOf(1.10);
        }
        if (age <= 50) {
            return BigDecimal.valueOf(1.20);
        }
        return BigDecimal.valueOf(1.30);
    }

    public record PremiumResult(
            BigDecimal basePremium,
            BigDecimal ageMultiplier,
            BigDecimal annualPremium,
            BigDecimal monthlyPremium
    ) {}
}
