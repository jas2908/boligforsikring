package no.boligforsikring;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Year;
import static org.junit.jupiter.api.Assertions.*;

class PremiumCalculatorTest {

    private final PremiumCalculator calculator =
            new PremiumCalculator();

    private final int currentYear = Year.now().getValue();

    @Test
    void calculatesPremiumForNewHouse() {
        PremiumCalculator.PremiumResult result =
                calculator.calculate(120, currentYear - 5);

        assertEquals(
                new BigDecimal("7200.00"),
                result.annualPremium()
        );
        assertEquals(
                new BigDecimal("600.00"),
                result.monthlyPremium()
        );
    }

    @Test
    void calculatesPremiumForHouseBetween11And30Years() {
        PremiumCalculator.PremiumResult result =
                calculator.calculate(120, currentYear - 20);

        assertEquals(
                new BigDecimal("7920.00"),
                result.annualPremium()
        );
        assertEquals(
                new BigDecimal("660.00"),
                result.monthlyPremium()
        );
    }

    @Test
    void calculatesPremiumForHouseBetween31And50Years() {
        PremiumCalculator.PremiumResult result =
                calculator.calculate(120, currentYear - 40);

        assertEquals(
                new BigDecimal("8640.00"),
                result.annualPremium()
        );
        assertEquals(
                new BigDecimal("720.00"),
                result.monthlyPremium()
        );
    }

    @Test
    void calculatesPremiumForHouseOlderThan50Years() {
        PremiumCalculator.PremiumResult result =
                calculator.calculate(120, currentYear - 60);

        assertEquals(
                new BigDecimal("9360.00"),
                result.annualPremium()
        );
        assertEquals(
                new BigDecimal("780.00"),
                result.monthlyPremium()
        );
    }

    @Test
    void appliesCorrectMultiplierAtAgeBoundaries() {
        assertEquals(
                new BigDecimal("1.0"),
                calculator.calculate(100, currentYear - 10)
                        .ageMultiplier()
        );

        assertEquals(
                new BigDecimal("1.1"),
                calculator.calculate(100, currentYear - 11)
                        .ageMultiplier()
        );

        assertEquals(
                new BigDecimal("1.1"),
                calculator.calculate(100, currentYear - 30)
                        .ageMultiplier()
        );

        assertEquals(
                new BigDecimal("1.2"),
                calculator.calculate(100, currentYear - 31)
                        .ageMultiplier()
        );

        assertEquals(
                new BigDecimal("1.2"),
                calculator.calculate(100, currentYear - 50)
                        .ageMultiplier()
        );

        assertEquals(
                new BigDecimal("1.3"),
                calculator.calculate(100, currentYear - 51)
                        .ageMultiplier()
        );
    }

    @Test
    void roundsPremiumToTwoDecimalPlaces() {
        PremiumCalculator.PremiumResult result =
                calculator.calculate(100.123, currentYear - 5);

        assertEquals(
                new BigDecimal("6007.38"),
                result.annualPremium()
        );

        assertEquals(
                new BigDecimal("500.62"),
                result.monthlyPremium()
        );
    }

    @Test
    void rejectsZeroSquareMeters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(0, currentYear - 5)
        );
    }

    @Test
    void rejectsNegativeSquareMeters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(-50, currentYear - 5)
        );
    }

    @Test
    void rejectsNonFiniteSquareMeters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(Double.NaN, currentYear - 5)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(Double.POSITIVE_INFINITY, currentYear - 5)
        );
    }

    @Test
    void rejectsBuildingYearBefore1800() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(120, 1799)
        );
    }

    @Test
    void rejectsFutureBuildingYear() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(120, currentYear + 1)
        );
    }
}
