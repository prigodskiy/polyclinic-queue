package ru.polyclinic.queue.model;

import java.io.Serial;
import java.time.LocalDateTime;

public final class ClosedTicket extends Ticket implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String address;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ClosedTicket(
            @com.fasterxml.jackson.annotation.JsonProperty("id") long id,
            @com.fasterxml.jackson.annotation.JsonProperty("cardNumber") String cardNumber,
            @com.fasterxml.jackson.annotation.JsonProperty("fullName") String fullName,
            @com.fasterxml.jackson.annotation.JsonProperty("office") String office,
            @com.fasterxml.jackson.annotation.JsonProperty("urgency") int urgency,
            @com.fasterxml.jackson.annotation.JsonProperty("phoneNumber") String phoneNumber,
            @com.fasterxml.jackson.annotation.JsonProperty("policyNumber") String policyNumber,
            @com.fasterxml.jackson.annotation.JsonProperty("takenAt") LocalDateTime takenAt,
            @com.fasterxml.jackson.annotation.JsonProperty("address") String address) {
        super(id, cardNumber, fullName, office, urgency, phoneNumber, policyNumber, takenAt);
        this.address = (address == null || address.isBlank()) ? null : address.strip();
    }

    @Override
    public TicketType getType() {
        return TicketType.CLOSED;
    }

    public String getAddressOrNull() {
        return address;
    }

    public boolean isHomeVisit() {
        return address != null;
    }

    @Override
    public Ticket withChanges(String newFullName, String newOffice, Integer newUrgency, String newPhoneNumber, String newPolicyNumber) {
        if (newFullName != null || newOffice != null || newUrgency != null) {
            throw new UnsupportedOperationException("Закрытый талон нельзя редактировать");
        }
        return new ClosedTicket(
                getId(),
                getCardNumber(),
                getFullName(),
                getOffice(),
                getUrgency(),
                newPhoneNumber != null ? newPhoneNumber : getPhoneNumber(),
                newPolicyNumber != null ? newPolicyNumber : getPolicyNumber(),
                getTakenAt(),
                address);
    }

    @Override
    public String toString() {
        return "ClosedTicket{card='%s', patient='%s', office='%s', urgency=%d, phone='%s', address='%s', time=%s}"
                .formatted(getCardNumber(), getFullName(), getOffice(),
                        getUrgency(), getPhoneNumber(), address == null ? "" : address, getTakenAt());
    }
}