package ru.polyclinic.queue.io;

import java.util.List;
import ru.polyclinic.queue.model.Ticket;

public record CsvLoadResult(List<Ticket> tickets, List<CsvRowError> errors, int version) {
    public CsvLoadResult {
        tickets = List.copyOf(tickets);
        errors = List.copyOf(errors);
    }
}