package ru.student.clinicqueue.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.student.clinicqueue.model.ClosedTicket;
import ru.student.clinicqueue.model.HomeVisitTicket;
import ru.student.clinicqueue.model.RegularTicket;
import ru.student.clinicqueue.model.Ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketCsvServiceTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 12, 9, 30);
    private final TicketCsvService service = new TicketCsvService();

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsAllSupportedTicketTypes() throws Exception {
        Path file = write("""
                type;card_number;patient_name;room;urgency;taken_at;address
                REGULAR;10004567;Анна;101;2;2026-10-12T09:30:00;
                HOME_VISIT;10007891;Иван;Терапевт;1;2026-10-12T09:31:00;ул. Ленина, 1
                CLOSED;10001234;Мария;205;0;2026-10-12T09:32:00;
                """);

        CsvLoadResult result = service.load(file);

        assertEquals(3, result.tickets().size());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void skipsInvalidRowsAndReportsCodes() throws Exception {
        Path file = write("""
                type;card_number;patient_name;room;urgency;taken_at;address
                REGULAR;10004567;Анна;101;2;2026-10-12T09:30:00;
                UNKNOWN;10000001;Ошибка;101;1;2026-10-12T09:30:00;
                REGULAR;10000002;Ошибка;101;abc;2026-10-12T09:30:00;
                HOME_VISIT;10000003;Ошибка;Терапевт;1;2026-10-12T09:30:00;
                """);

        CsvLoadResult result = service.load(file);

        assertEquals(1, result.tickets().size());
        assertEquals(
                List.of(CsvErrorCode.UNKNOWN_TYPE, CsvErrorCode.BAD_NUMBER, CsvErrorCode.INVALID_TICKET),
                result.errors().stream().map(CsvRowError::code).toList()
        );
    }

    @Test
    void roundTripPreservesQuotedFields() throws Exception {
        Path file = temporaryDirectory.resolve("tickets.csv");
        List<Ticket> tickets = List.of(
                new RegularTicket("10004567", "Анна; Мария", "101", 2, TIME),
                new HomeVisitTicket("10007891", "Иван", "Терапевт", 1, TIME, "ул. \"Мира\"; 5"),
                new ClosedTicket("10001234", "Мария", "205", 0, TIME)
        );

        service.save(file, tickets);
        CsvLoadResult result = service.load(file);

        assertEquals(tickets, result.tickets());
        assertEquals("ул. \"Мира\"; 5", ((HomeVisitTicket) result.tickets().get(1)).getAddress());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void rejectsMalformedHeader() throws Exception {
        Path file = write("name;room\nАнна;101\n");

        TicketCsvException error = assertThrows(TicketCsvException.class, () -> service.load(file));

        assertEquals(CsvErrorCode.MISSING_HEADER, error.getCode());
    }

    @Test
    void reportsMalformedDateWithoutLosingValidRows() throws Exception {
        Path file = write("""
                type;card_number;patient_name;room;urgency;taken_at;address
                REGULAR;10004567;Анна;101;2;не-дата;
                CLOSED;10001234;Мария;205;0;2026-10-12T09:32:00;
                """);

        CsvLoadResult result = service.load(file);

        assertEquals(1, result.tickets().size());
        assertEquals(CsvErrorCode.BAD_DATE_TIME, result.errors().getFirst().code());
    }

    private Path write(String content) throws Exception {
        Path file = temporaryDirectory.resolve("input.csv");
        Files.writeString(file, content);
        return file;
    }
}
