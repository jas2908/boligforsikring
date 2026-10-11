package no.boligforsikring;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/insurance/quotes")
public class InsuranceController {

    private final InsuranceService insuranceService;

    public InsuranceController(InsuranceService insuranceService) {
        this.insuranceService = insuranceService;
    }

    @PostMapping
    public ResponseEntity<PremiumQuoteResponse> createQuote(
            @RequestBody QuoteRequest request) {

        PremiumQuoteResponse response =
                insuranceService.createQuote(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PremiumQuoteResponse> getQuote(
            @PathVariable UUID id) {

        PremiumQuoteResponse response =
                insuranceService.getQuote(id);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/offer-request")
    public ResponseEntity<Void> requestOffer(
            @PathVariable UUID id,
            @RequestBody ContactRequest request) {

        insuranceService.requestOffer(id, request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/contact-request")
    public ResponseEntity<Void> requestContact(
            @PathVariable UUID id,
            @RequestBody ContactRequest request) {

        insuranceService.requestCustomerService(id, request);

        return ResponseEntity.noContent().build();
    }
}
