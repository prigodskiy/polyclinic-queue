package polyclinic.queue.model;

import java.util.Objects;

public class HomeVisitTicket extends Ticket {

    private final String address;

    public HomeVisitTicket(String cardNumber, String fullName, int office,
                           int urgency, String address) {
        super(cardNumber, fullName, office, urgency);
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address cannot be empty");
        }
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        HomeVisitTicket that = (HomeVisitTicket) o;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), address);
    }

    @Override
    public String toString() {
        return String.format("HomeVisitTicket{card='%s', patient='%s', office=%d, urgency=%d, address='%s', time=%s}",
                getCardNumber(), getFullName(), getOffice(), getUrgency(), address, getTakenAt());
    }
}