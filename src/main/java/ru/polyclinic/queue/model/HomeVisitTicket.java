package ru.polyclinic.queue.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Талон на дому: редактируемый талон с дополнительным полем «адрес». */
public final class HomeVisitTicket extends Ticket implements Editable {

    private final String address;

    public HomeVisitTicket(String cardNumber, String fullName, String office,
                           int urgency, String address) {
        super(cardNumber, fullName, office, urgency);
        this.address = requireAddress(address);
    }

    /** Заводской конструктор для десериализации и копий. */
    public HomeVisitTicket(long id, String cardNumber, String fullName, String office,
                           int urgency, LocalDateTime takenAt, String address) {
        super(id, cardNumber, fullName, office, urgency, takenAt);
        this.address = requireAddress(address);
    }

    private static String requireAddress(String address) {
        String stripped = Objects.requireNonNull(address, "address must not be null").strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("Address cannot be blank");
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

    /** Копия с новым адресом; {@code null} = оставить прежний. */
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

    // equals/hashCode наследуются от Ticket: идентичность по id — безопасно
    // для кучи (объекты в массиве не «исчезают» после changePriority).

    @Override
    public String toString() {
        return "HomeVisitTicket{card='%s', patient='%s', office='%s', urgency=%d, address='%s', time=%s}"
                .formatted(getCardNumber(), getFullName(), getOffice(),
                        getUrgency(), address, getTakenAt());
    }
}