package no.boligforsikring;

public record ContactRequest(
        String firstName,
        String email,
        String phone
) {
}
