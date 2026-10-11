package no.boligforsikring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InsuranceServiceTest {

    private InsuranceQuoteRepository repository;
    private InsuranceService service;

    private UUID quoteId;
    private HouseInsuranceQuote quote;

    @BeforeEach
    void setUp() {
        repository = mock(InsuranceQuoteRepository.class);
        KartverketClient kartverketClient = mock(KartverketClient.class);

        service = new InsuranceService(
                repository,
                kartverketClient,
                "test-secret"
        );

        quoteId = UUID.randomUUID();

        quote = new HouseInsuranceQuote();
        quote.setId(quoteId);
        quote.setStatus("OFFERED");
        quote.setCreatedAt(LocalDateTime.now().minusDays(1));
        quote.setExpiresAt(LocalDateTime.now().plusDays(29));

        when(repository.findById(quoteId))
                .thenReturn(Optional.of(quote));
    }

    @Test
    void returnsActiveQuoteWithoutExpiringIt() {
        PremiumQuoteResponse response = service.getQuote(quoteId);

        assertNotNull(response);
        verify(repository, never()).expireQuote(any());
    }

    @Test
    void expiresQuoteWhenRetrievedAfterExpiration() {
        quote.setExpiresAt(LocalDateTime.now().minusDays(1));

        HouseInsuranceQuote expiredQuote = new HouseInsuranceQuote();
        expiredQuote.setId(quoteId);
        expiredQuote.setStatus("EXPIRED");
        expiredQuote.setCreatedAt(quote.getCreatedAt());
        expiredQuote.setExpiresAt(quote.getExpiresAt());

       when(repository.findById(quoteId))
        .thenReturn(Optional.of(quote))
        .thenReturn(Optional.of(expiredQuote));

        service.getQuote(quoteId);

        verify(repository).expireQuote(quoteId);
        verify(repository, times(2)).findById(quoteId);
    }

    @Test
    void rejectsOfferRequestForExpiredQuote() {
        quote.setExpiresAt(LocalDateTime.now().minusDays(1));

        ContactRequest contact = new ContactRequest(
                "Ola",
                "ola@example.com",
                "12345678"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requestOffer(quoteId, contact)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(repository).expireQuote(quoteId);
        verify(repository, never())
                .saveOfferRequest(any(), any());
    }

    @Test
    void savesOfferRequestForActiveQuote() {
        ContactRequest contact = new ContactRequest(
                "Ola",
                "ola@example.com",
                ""
        );

        service.requestOffer(quoteId, contact);

        verify(repository).saveOfferRequest(quoteId, contact);
    }

    @Test
    void rejectsOfferRequestWithInvalidEmail() {
        ContactRequest contact = new ContactRequest(
                "Ola",
                "invalid-email",
                "12345678"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requestOffer(quoteId, contact)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verify(repository, never())
                .saveOfferRequest(any(), any());
    }

    @Test
    void rejectsAdvisorRequestWithoutPhone() {
        ContactRequest contact = new ContactRequest(
                "Ola",
                "ola@example.com",
                ""
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requestCustomerService(quoteId, contact)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verify(repository, never())
                .saveCustomerServiceRequest(any(), any());
    }

    @Test
    void allowsAdvisorContactForExpiredQuote() {
        quote.setStatus("EXPIRED");
        quote.setExpiresAt(LocalDateTime.now().minusDays(1));

        ContactRequest contact = new ContactRequest(
                "Ola",
                "ola@example.com",
                "12345678"
        );

        service.requestCustomerService(quoteId, contact);

        verify(repository)
                .saveCustomerServiceRequest(quoteId, contact);
    }

    @Test
    void returnsNotFoundForUnknownQuote() {
        UUID unknownId = UUID.randomUUID();

        when(repository.findById(unknownId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getQuote(unknownId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}