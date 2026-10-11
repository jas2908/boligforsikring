
package no.boligforsikring;

public record QuoteRequest(
        String streetName,
        int houseNumber,
        String postalCode,
        double squareMeters,
        int byggeaar,
        String turnstileToken
) {
}
