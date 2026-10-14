package ru.student.clinicqueue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ru.student.clinicqueue.model.ClosedTicket;
import ru.student.clinicqueue.model.HomeVisitTicket;
import ru.student.clinicqueue.model.RegularTicket;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClinicQueueAppTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 12, 9, 30);

    @Test
    void addressColumnShowsSubtypeAddressAndEmptyValueForOtherTypes() {
        var homeVisit = new HomeVisitTicket(
                "10004567", "Анна", "Терапевт", 1, TIME, "ул. Ленина, 1"
        );
        var regular = new RegularTicket("10007891", "Иван", "101", 2, TIME);
        var closed = new ClosedTicket("10001234", "Мария", "205", 0, TIME);

        assertEquals("ул. Ленина, 1", ClinicQueueApp.addressForDisplay(homeVisit));
        assertEquals("", ClinicQueueApp.addressForDisplay(regular));
        assertEquals("", ClinicQueueApp.addressForDisplay(closed));
    }
}
