document.addEventListener("DOMContentLoaded", () => {

    const form = document.getElementById("insuranceForm");

    const slider = document.getElementById("squareMeters");
    const areaInput = document.getElementById("squareMetersInput");
    const sliderValue = document.getElementById("squareMetersValue");
    const sliderMaxLabel = document.getElementById("sliderMaxLabel");

    const calculateButton = document.getElementById("calculateButton");
    const offerButton = document.getElementById("offerButton");
    const contactButton = document.getElementById("contactButton");
    const contactForm = document.getElementById("contactForm");
    const contactMessage = document.getElementById("contactMessage");

    const placeholder = document.getElementById("resultPlaceholder");
    const loadingIndicator = document.getElementById("loadingIndicator");
    const quoteResult = document.getElementById("quoteResult");
    const formError = document.getElementById("formError");

    let currentQuoteId = null;
    let contactType = null;
    let captchaNeedsReset = false;
    let isCalculating = false;

    calculateButton.disabled = true;

    // Turnstile callbacks must be globally accessible.
    window.onTurnstileSuccess = () => {
        if (!isCalculating && !captchaNeedsReset) {
            calculateButton.disabled = false;
        }
    };

    window.onTurnstileExpired = () => {
        calculateButton.disabled = true;
    };

    window.onTurnstileError = () => {
        calculateButton.disabled = true;
    };

    const currencyFormatter = new Intl.NumberFormat("nb-NO", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });

    const defaultSliderMax = 500;

    function updateSliderMax(value) {
        const max = Math.max(defaultSliderMax, value);

        slider.max = String(max);
        sliderMaxLabel.textContent =
            `${max.toLocaleString("nb-NO")} m²`;
    }

    slider.addEventListener("input", () => {
        areaInput.value = slider.value;
        sliderValue.textContent = slider.value;
    });

    areaInput.addEventListener("input", () => {
        const value = Number(areaInput.value);
        const min = Number(slider.min);

        if (
            areaInput.value === "" ||
            !Number.isFinite(value) ||
            value < min
        ) {
            return;
        }

        updateSliderMax(value);

        slider.value = String(value);
        sliderValue.textContent = String(value);
    });

    updateSliderMax(Number(areaInput.value));
    slider.value = areaInput.value;
    sliderValue.textContent = areaInput.value;

    document.getElementById("byggeaar").max =
        new Date().getFullYear();

    form.addEventListener("input", () => {
        hideError();

        if (captchaNeedsReset) {
            captchaNeedsReset = false;
            calculateButton.disabled = true;
            window.turnstile?.reset();
        }
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        hideError();

        if (isCalculating || captchaNeedsReset) {
            return;
        }

        if (!form.reportValidity()) {
            return;
        }

        const turnstileToken = window.turnstile?.getResponse();

        if (!turnstileToken) {
            showError(
                "Vennligst fullfør sikkerhetskontrollen før du beregner prisen."
            );
            calculateButton.disabled = true;
            return;
        }

        const request = {
            streetName: document.getElementById("streetName").value.trim(),
            houseNumber: Number(document.getElementById("houseNumber").value),
            postalCode: document.getElementById("postalCode").value.trim(),
            squareMeters: Number(areaInput.value),
            byggeaar: Number(document.getElementById("byggeaar").value),
            turnstileToken: turnstileToken
        };

        currentQuoteId = null;
        contactType = null;

        contactForm.classList.add("d-none");
        contactMessage.classList.add("d-none");

        // Reset both buttons for a new quote.
        offerButton.disabled = false;
        contactButton.disabled = false;
        offerButton.textContent = "Send meg tilbud";
        contactButton.textContent = "Jeg vil snakke med rådgiver";

        setLoading(true);

        try {
            const response = await fetch("/api/insurance/quotes", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(request)
            });

            if (!response.ok) {
                throw new Error(await getErrorMessage(response));
            }

            const quote = await response.json();

            if (
                !quote.quoteId ||
                quote.monthlyPremium == null ||
                quote.annualPremium == null
            ) {
                throw new Error("Serveren returnerte et ugyldig tilbud.");
            }

            currentQuoteId = quote.quoteId;

            document.getElementById("monthlyPremium").textContent =
                currencyFormatter.format(quote.monthlyPremium);

            document.getElementById("annualPremium").textContent =
                currencyFormatter.format(quote.annualPremium);

            document.getElementById("resultSquareMeters").textContent =
                request.squareMeters;

            document.getElementById("resultBuildingYear").textContent =
                request.byggeaar;

            updateQuoteStatus(quote.status || "OFFERED");

            placeholder.classList.add("d-none");
            quoteResult.classList.remove("d-none");

        } catch (error) {
            quoteResult.classList.add("d-none");
            placeholder.classList.remove("d-none");

            showError(
                error.message || "Kunne ikke beregne prisestimat."
            );

        } finally {
            captchaNeedsReset = true;
            setLoading(false);
            calculateButton.disabled = true;
        }
    });

    offerButton.addEventListener("click", () => {
        showContactForm("offer");
    });

    contactButton.addEventListener("click", () => {
        showContactForm("contact");
    });

    contactForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (!currentQuoteId || !contactForm.reportValidity()) {
            return;
        }

        const submitButton =
            document.getElementById("contactSubmitButton");

        // Prevent another submission of a completed request type.
        if (
            (contactType === "offer" && offerButton.disabled) ||
            (contactType === "contact" && contactButton.disabled)
        ) {
            return;
        }

        const submittedType = contactType;
        const submittedQuoteId = currentQuoteId;

        const endpoint = submittedType === "offer"
            ? "offer-request"
            : "contact-request";

        const request = {
            firstName: document.getElementById("firstName").value.trim(),
            email: document.getElementById("email").value.trim(),
            phone: document.getElementById("phone").value.trim()
        };

        submitButton.disabled = true;

        try {
            const response = await fetch(
                `/api/insurance/quotes/${encodeURIComponent(submittedQuoteId)}/${endpoint}`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(request)
                }
            );

            if (!response.ok) {
                throw new Error(await getErrorMessage(response));
            }

            contactForm.classList.add("d-none");

            if (submittedType === "offer") {
                offerButton.disabled = true;
                offerButton.textContent = "Tilbud forespurt";

                contactMessage.textContent =
                    "Takk! Tilbudsforespørselen din er registrert.";
            } else {
                contactButton.disabled = true;
                contactButton.textContent = "Rådgiver forespurt";

                contactMessage.textContent =
                    "Takk! En rådgiver tar kontakt med deg.";
            }

            contactMessage.classList.remove("d-none");

        } catch (error) {
            showError(
                error.message ||
                "Kunne ikke sende kontaktopplysningene."
            );

        } finally {
            submitButton.disabled = false;
        }
    });

    function showContactForm(type) {
        if (!currentQuoteId) {
            return;
        }

        // Do not reopen a request that has already succeeded.
        if (type === "offer" && offerButton.disabled) {
            return;
        }

        if (type === "contact" && contactButton.disabled) {
            return;
        }

        contactType = type;

        contactMessage.classList.add("d-none");
        contactForm.classList.remove("d-none");

        const isContact = type === "contact";

        document.getElementById("contactTitle").textContent = isContact
            ? "Be en rådgiver kontakte deg"
            : "Få tilbudet på e-post";

        document.getElementById("phone").required = isContact;

        document.getElementById("phoneOptional")
            .classList.toggle("d-none", isContact);

        document.getElementById("firstName").focus();
    }

    function setLoading(isLoading) {
        isCalculating = isLoading;

        calculateButton.disabled =
            isLoading ||
            captchaNeedsReset ||
            !window.turnstile?.getResponse();

        calculateButton.textContent = isLoading
            ? "Beregner ..."
            : "Beregn prisestimat";

        loadingIndicator.classList.toggle("d-none", !isLoading);

        if (isLoading) {
            placeholder.classList.add("d-none");
            quoteResult.classList.add("d-none");
        }
    }

    function updateQuoteStatus(status) {
        const statusElement = document.getElementById("quoteStatus");

        statusElement.textContent = status;
        statusElement.className = "badge";

        if (status === "ACCEPTED") {
            statusElement.classList.add("text-bg-success");
        } else if (status === "EXPIRED") {
            statusElement.classList.add("text-bg-secondary");
        } else {
            statusElement.classList.add("text-bg-info");
        }
    }

    function showError(message) {
        formError.textContent = message;
        formError.classList.remove("d-none");
    }

    function hideError() {
        formError.textContent = "";
        formError.classList.add("d-none");
    }

    async function getErrorMessage(response) {
        const fallback =
            `Forespørselen feilet (${response.status}).`;

        const contentType =
            response.headers.get("content-type") || "";

        if (contentType.includes("application/json")) {
            const data = await response.json();

            return data.message || data.error || fallback;
        }

        const text = await response.text();

        return text && text.length < 200 ? text : fallback;
    }
});