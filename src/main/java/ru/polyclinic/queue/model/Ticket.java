package ru.polyclinic.queue.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Ticket.class, name = "REGULAR"),
        @JsonSubTypes.Type(value = ClosedTicket.class, name = "CLOSED"),
        @JsonSubTypes.Type(value = HomeVisitTicket.class, name = "HOME_VISIT")
})
public class Ticket implements Editable, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private static final AtomicLong ID_SEQUENCE = new AtomicLong(1);

    private final long id;
    private final String cardNumber;
    private final String fullName;
    private final String office;
    private final int urgency;
    private final LocalDateTime takenAt;

    public Ticket(String cardNumber, String fullName, String office, int urgency) {
        this(nextId(), cardNumber, fullName, office, urgency, LocalDateTime.now());
    }

    @JsonCreator
    public Ticket(
            @JsonProperty("id") long id,
            @JsonProperty("cardNumber") String cardNumber,
            @JsonProperty("fullName") String fullName,
            @JsonProperty("office") String office,
            @JsonProperty("urgency") int urgency,
            @JsonProperty("takenAt") LocalDateTime takenAt) {
        if (urgency < 0 || urgency > 3) {
            throw new IllegalArgumentException("urgency должна быть в диапазоне [0, 3]");
        }
        this.id = id;
        this.cardNumber = normalize(cardNumber, "cardNumber");
        this.fullName = normalize(fullName, "fullName");
        this.office = normalize(office, "office");
        this.urgency = urgency;
        this.takenAt = Objects.requireNonNull(takenAt, "значение takenAt не должно быть null");
    }

    static String normalize(String value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " не должно быть null").strip();
    }

    protected static long nextId() {
        return ID_SEQUENCE.getAndIncrement();
    }
    public static long nextIdForUi() {
        return nextId();
    }
    public long getId() { return id; }
    public TicketType getType() {
        return TicketType.REGULAR;
    }
    public String getCardNumber() { return cardNumber; }
    public String getFullName() { return fullName; }
    public String getOffice() { return office; }
    public int getUrgency() { return urgency; }
    public LocalDateTime getTakenAt() { return takenAt; }

    public Ticket withChanges(String newFullName, String newOffice, Integer newUrgency) {
        return new Ticket(
                id,
                cardNumber,
                newFullName != null ? newFullName : fullName,
                newOffice != null ? newOffice : office,
                newUrgency != null ? newUrgency : urgency,
                takenAt);
    }

    public Ticket withUrgency(int newUrgency) {
        return withChanges(null, null, newUrgency);
    }

    @Override
    public List<String> validate() {
        return TicketValidator.validate(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return id == ticket.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "%s{card='%s', patient='%s', office='%s', urgency=%d, time=%s}".formatted(
                getType(), cardNumber, fullName, office, urgency, takenAt);
    }
}