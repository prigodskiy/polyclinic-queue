package ru.student.clinicqueue.model;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketValidatorTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 12, 9, 30);

    @Test
    void acceptsValidRegularTicket() {
        var ticket = new RegularTicket("10004567", "Анна Смирнова", "101", 2, TIME);

        assertTrue(ticket.validate().isEmpty());
    }

    @Test
    void reportsInvalidCardNumberAndUrgency() {
        var ticket = new RegularTicket("ABC", "Анна", "101", 4, TIME);

        assertEquals(2, ticket.validate().size());
    }

    @Test
    void requiresAddressForHomeVisit() {
        var ticket = new HomeVisitTicket("10004567", "Анна", "Терапевт", 1, TIME, "");

        assertTrue(ticket.validate().contains("Для талона на дому нужен адрес"));
    }

    @Test
    void closedTicketIsReadOnly() {
        Ticket ticket = new ClosedTicket("10004567", "Анна", "101", 0, TIME);

        assertFalse(ticket instanceof Editable);
        assertTrue(TicketValidator.validate(ticket).isEmpty());
    }

    @Test
    void homeVisitEqualityIncludesAddress() {
        var first = new HomeVisitTicket("10004567", "Анна", "Терапевт", 1, TIME, "ул. Ленина, 1");
        var second = new HomeVisitTicket("10004567", "Анна", "Терапевт", 1, TIME, "ул. Ленина, 2");

        assertFalse(first.equals(second));
    }

    @Test
    void baseTicketDoesNotExposeHomeVisitAddress() {
        assertThrows(NoSuchMethodException.class, () -> Ticket.class.getDeclaredMethod("getAddress"));
    }

    @Test
    void homeVisitKeepsAndNormalizesItsAddress() {
        var ticket = new HomeVisitTicket(
                " 10004567 ",
                " Анна Смирнова ",
                " Терапевт ",
                1,
                TIME,
                "  ул. Ленина, 1  "
        );

        assertEquals("10004567", ticket.getCardNumber());
        assertEquals("Анна Смирнова", ticket.getPatientName());
        assertEquals("Терапевт", ticket.getRoom());
        assertEquals("ул. Ленина, 1", ticket.getAddress());
    }

    @Test
    void rejectsNullStringsInsteadOfReplacingThemWithEmptyValues() {
        NullPointerException baseError = assertThrows(
                NullPointerException.class,
                () -> new RegularTicket(null, "Анна", "101", 1, TIME)
        );
        NullPointerException subtypeError = assertThrows(
                NullPointerException.class,
                () -> new HomeVisitTicket("10004567", "Анна", "Терапевт", 1, TIME, null)
        );

        assertEquals("cardNumber must not be null", baseError.getMessage());
        assertEquals("address must not be null", subtypeError.getMessage());
    }

    @Test
    void blankStringsRemainForBusinessValidation() {
        var ticket = new RegularTicket("    ", "   ", "   ", 1, TIME);

        assertEquals("", ticket.getCardNumber());
        assertTrue(ticket.validate().contains("ФИО обязательно"));
        assertTrue(ticket.validate().contains("Кабинет обязателен"));
    }
}
