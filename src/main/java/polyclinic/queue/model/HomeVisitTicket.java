package polyclinic.queue.model;

import java.util.Objects;
import java.time.LocalDateTime;

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

    HomeVisitTicket(String cardNumber, String fullName, int office,
                    int urgency, String address, LocalDateTime takenAt) {
        super(cardNumber, fullName, office, urgency, takenAt);
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address cannot be empty");
        }
        this.address = address;
    }

    public static HomeVisitTicket createWithTime(String cardNumber, String fullName,
                                                 int office, int urgency,
                                                 String address, LocalDateTime takenAt) {
        return new HomeVisitTicket(cardNumber, fullName, office, urgency, address, takenAt);
    }

    public HomeVisitTicket withChanges(String newFullName, Integer newOffice,
                                       Integer newUrgency, String newAddress) {
        String fullName = (newFullName != null) ? newFullName : this.getFullName();
        int office = (newOffice != null) ? newOffice : this.getOffice();
        int urgency = (newUrgency != null) ? newUrgency : this.getUrgency();
        String address = (newAddress != null) ? newAddress : this.address;

        return HomeVisitTicket.createWithTime(
                this.getCardNumber(), fullName, office, urgency, address, this.getTakenAt());
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
