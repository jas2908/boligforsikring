package no.boligforsikring;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Component
public class KartverketClient {

    private static final String API_URL =
            "https://ws.geonorge.no/adresser/v1/sok";

    private final RestTemplate restTemplate;

    public KartverketClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void verifyAddress(QuoteRequest request) {

        URI uri = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("adressenavn", request.streetName())
                .queryParam("nummer", request.houseNumber())
                .queryParam("postnummer", request.postalCode())
                .queryParam("treffPerSide", 100)
                .build()
                .encode()
                .toUri();

        JsonNode response;

        try {
            ResponseEntity<JsonNode> apiResponse =
                    restTemplate.getForEntity(uri, JsonNode.class);

            response = apiResponse.getBody();

        } catch (RestClientException exception) {
            throw new KartverketUnavailableException(
                    "Adressetjenesten er midlertidig utilgjengelig.",
                    exception
            );
        }

        if (response == null
                || !response.has("adresser")
                || !response.get("adresser").isArray()) {

            throw new KartverketUnavailableException(
                    "Adressetjenesten returnerte et ugyldig svar."
            );
        }

        for (JsonNode address : response.get("adresser")) {

            String streetName =
                    address.path("adressenavn").asText("");

            int houseNumber =
                    address.path("nummer").asInt(-1);

            String postalCode =
                    address.path("postnummer").asText("");

            boolean matches =
                    normalize(streetName)
                            .equals(normalize(request.streetName()))
                    && houseNumber == request.houseNumber()
                    && postalCode.equals(request.postalCode());

            if (matches) {
                return;
            }
        }

        throw new IllegalArgumentException(
                "Fant ikke adressen hos Kartverket. " +
                "Kontroller gatenavn, husnummer og postnummer."
        );
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                        .replaceAll("\\s+", " ")
                        .toLowerCase(Locale.ROOT);
    }

    public static class KartverketUnavailableException
            extends RuntimeException {

        public KartverketUnavailableException(String message) {
            super(message);
        }

        public KartverketUnavailableException(
                String message,
                Throwable cause
        ) {
            super(message, cause);
        }
    }
}
