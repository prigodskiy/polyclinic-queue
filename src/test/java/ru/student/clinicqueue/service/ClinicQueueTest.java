package ru.student.clinicqueue.service;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.student.clinicqueue.model.ClosedTicket;
import ru.student.clinicqueue.model.RegularTicket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClinicQueueTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 12, 9, 30);

    @Test
    void addsEditableTicket() {
        var queue = new ClinicQueue();
        var ticket = regular("10004567");

        queue.add(ticket);

        assertEquals(List.of(ticket), queue.getAll());
    }

    @Test
    void rejectsAddingClosedTicket() {
        var queue = new ClinicQueue();
        var ticket = new ClosedTicket("10004567", "Анна", "101", 0, TIME);

        assertThrows(IllegalArgumentException.class, () -> queue.add(ticket));
    }

    @Test
    void allowsClosedTicketsFromImportedData() {
        var queue = new ClinicQueue();
        var ticket = new ClosedTicket("10004567", "Анна", "101", 0, TIME);

        queue.replaceAll(List.of(ticket));

        assertEquals(1, queue.size());
    }

    @Test
    void preventsEditingClosedTicket() {
        var queue = new ClinicQueue();
        queue.replaceAll(List.of(new ClosedTicket("10004567", "Анна", "101", 0, TIME)));

        assertThrows(IllegalArgumentException.class, () -> queue.update(0, regular("10007891")));
    }

    @Test
    void rejectsInvalidImportedData() {
        var queue = new ClinicQueue();
        var invalid = new RegularTicket("bad", "", "", 9, null);

        assertThrows(TicketValidationException.class, () -> queue.replaceAll(List.of(invalid)));
    }

    private RegularTicket regular(String cardNumber) {
        return new RegularTicket(cardNumber, "Анна", "101", 1, TIME);
    }
}
