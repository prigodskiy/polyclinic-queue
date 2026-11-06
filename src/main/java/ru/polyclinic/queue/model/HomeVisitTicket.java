package ru.polyclinic.queue.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HomeVisitTicket extends Ticket implements Editable, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final String address;

    public HomeVisitTicket(String cardNumber, String fullName, String office,
                           int urgency, String address) {
        super(cardNumber, fullName, office, urgency);
        this.address = requireAddress(address);
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public HomeVisitTicket(
            @com.fasterxml.jackson.annotation.JsonProperty("id") long id,
            @com.fasterxml.jackson.annotation.JsonProperty("cardNumber") String cardNumber,
            @com.fasterxml.jackson.annotation.JsonProperty("fullName") String fullName,
            @com.fasterxml.jackson.annotation.JsonProperty("office") String office,
            @com.fasterxml.jackson.annotation.JsonProperty("urgency") int urgency,
            @com.fasterxml.jackson.annotation.JsonProperty("takenAt") LocalDateTime takenAt,
            @com.fasterxml.jackson.annotation.JsonProperty("address") String address) {
        super(id, cardNumber, fullName, office, urgency, takenAt);
        this.address = requireAddress(address);
    }

    private static String requireAddress(String address) {
        String stripped = Objects.requireNonNull(address, "address не должен быть null").strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("address не может быть blank");
        }
        return stripped;
    }

    @Override
    public TicketType getType() {
        return TicketType.HOME_VISIT;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public Ticket withChanges(String newFullName, String newOffice, Integer newUrgency) {
        return withAddress(newFullName, newOffice, newUrgency, null);
    }

    public HomeVisitTicket withAddress(String newFullName, String newOffice,
                                       Integer newUrgency, String newAddress) {
        return new HomeVisitTicket(
                getId(),
                getCardNumber(),
                newFullName != null ? newFullName : getFullName(),
                newOffice != null ? newOffice : getOffice(),
                newUrgency != null ? newUrgency : getUrgency(),
                getTakenAt(),
                newAddress != null ? newAddress : address);
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>(TicketValidator.validate(this));
        if (address.isBlank()) {
            errors.add("Для талона на дому нужен адрес");
        } else if (TicketValidator.containsLineBreak(address)) {
            errors.add("Адрес должен занимать одну строку");
        }
        return List.copyOf(errors);
    }

    @Override
    public String toString() {
        return "HomeVisitTicket{card='%s', patient='%s', office='%s', urgency=%d, address='%s', time=%s}"
                .formatted(getCardNumber(), getFullName(), getOffice(),
                        getUrgency(), address, getTakenAt());
    }
}