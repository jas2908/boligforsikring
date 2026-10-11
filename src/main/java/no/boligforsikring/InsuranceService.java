package no.boligforsikring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.Map;
import java.util.UUID;

@Service
public class InsuranceService {

    private final InsuranceQuoteRepository repository;
    private final KartverketClient kartverketClient;
    private final PremiumCalculator calculator = new PremiumCalculator();

    private final RestTemplate restTemplate = new RestTemplate();
    private final String turnstileSecretKey;

    public InsuranceService(
            InsuranceQuoteRepository repository,
            KartverketClient kartverketClient,
            @Value("${TURNSTILE_SECRET_KEY}") String turnstileSecretKey
    ) {
        this.repository = repository;
        this.kartverketClient = kartverketClient;
        this.turnstileSecretKey = turnstileSecretKey;
    }

    public PremiumQuoteResponse createQuote(QuoteRequest request) {
        validateRequest(request);

        // Verify CAPTCHA before calling Kartverket or saving anything.
        verifyTurnstile(request.turnstileToken());

        try {
            kartverketClient.verifyAddress(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        } catch (KartverketClient.KartverketUnavailableException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception.getMessage()
            );
        }

        PremiumCalculator.PremiumResult premium =
                calculator.calculate(
                        request.squareMeters(),
                        request.byggeaar()
                );

        LocalDateTime now = LocalDateTime.now();

        HouseInsuranceQuote quote = new HouseInsuranceQuote();

        quote.setId(UUID.randomUUID());
        quote.setStreetName(request.streetName().trim());
        quote.setHouseNumber(request.houseNumber());
        quote.setPostalCode(request.postalCode().trim());
        quote.setSquareMeters(request.squareMeters());
        quote.setByggeaar(request.byggeaar());
        quote.setMonthlyPremium(premium.monthlyPremium());
        quote.setAnnualPremium(premium.annualPremium());
        quote.setStatus("OFFERED");
        quote.setCreatedAt(now);
        quote.setExpiresAt(now.plusDays(30));

        repository.save(quote);

        return PremiumQuoteResponse.from(quote, "OFFERED");
    }

    private void verifyTurnstile(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Fullfør sikkerhetskontrollen."
            );
        }

        String verificationUrl =
                "https://challenges.cloudflare.com/turnstile/v0/siteverify";

        MultiValueMap<String, String> requestBody =
                new LinkedMultiValueMap<>();

        requestBody.add("secret", turnstileSecretKey);
        requestBody.add("response", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        HttpEntity<MultiValueMap<String, String>> entity =
                new HttpEntity<>(requestBody, headers);

        try {
            Map<?, ?> result = restTemplate.postForObject(
                    verificationUrl,
                    entity,
                    Map.class
            );

            if (result == null ||
                    !Boolean.TRUE.equals(result.get("success"))) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Sikkerhetskontrollen feilet. Prøv igjen."
                );
            }

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Sikkerhetskontrollen er midlertidig utilgjengelig."
            );
        }
    }

    public PremiumQuoteResponse getQuote(UUID id) {
        HouseInsuranceQuote quote = findQuote(id);

        if ("OFFERED".equals(quote.getStatus())
                && !quote.getExpiresAt().isAfter(LocalDateTime.now())) {

            repository.expireQuote(id);
            quote = findQuote(id);
        }

        return PremiumQuoteResponse.from(
                quote,
                quote.getStatus()
        );
    }

    public void requestOffer(UUID quoteId, ContactRequest request) {
        HouseInsuranceQuote quote = findQuote(quoteId);

        // An expired offer cannot be requested by email.
        if ("OFFERED".equals(quote.getStatus())
                && !quote.getExpiresAt().isAfter(LocalDateTime.now())) {

            repository.expireQuote(quoteId);

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tilbudet har utløpt. Beregn et nytt prisestimat."
            );
        }

        if (!"OFFERED".equals(quote.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tilbudet er ikke lenger tilgjengelig."
            );
        }

        validateContact(request, false);
        repository.saveOfferRequest(quoteId, request);
    }

    public void requestCustomerService(
            UUID quoteId,
            ContactRequest request
    ) {
        // Customers can contact an advisor even if the quote has expired.
        findQuote(quoteId);
        validateContact(request, true);
        repository.saveCustomerServiceRequest(quoteId, request);
    }

    private void validateContact(
            ContactRequest request,
            boolean phoneRequired
    ) {
        if (request == null
                || request.firstName() == null
                || request.firstName().isBlank()
                || request.firstName().trim().length() > 100
                || request.email() == null
                || request.email().trim().length() > 255
                || !request.email().trim()
                    .matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Kontroller navn og e-postadresse."
            );
        }

        String phone = request.phone() == null
                ? ""
                : request.phone().trim();

        if (phoneRequired && phone.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Telefonnummer er påkrevd."
            );
        }

        if (!phone.isBlank()
                && (phone.length() > 20
                || phone.length() < 8
                || !phone.matches("^\\+?[0-9][0-9 -]*$"))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ugyldig telefonnummer."
            );
        }
    }

    private HouseInsuranceQuote findQuote(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Fant ikke tilbudet."
                ));
    }

    private void validateRequest(QuoteRequest request) {
        if (request == null
                || request.streetName() == null
                || request.streetName().isBlank()
                || request.streetName().trim().length() > 255
                || request.houseNumber() <= 0
                || request.postalCode() == null
                || !request.postalCode().matches("\\d{4}")
                || !Double.isFinite(request.squareMeters())
                || request.squareMeters() <= 0
                || request.squareMeters() > 2000
                || request.byggeaar() < 1800
                || request.byggeaar() > Year.now().getValue()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ugyldige boligopplysninger."
            );
        }
    }
}
