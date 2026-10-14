package ru.student.clinicqueue.io;

import java.util.List;

import ru.student.clinicqueue.model.Ticket;

public record CsvLoadResult(List<Ticket> tickets, List<CsvRowError> errors) {
    public CsvLoadResult {
        tickets = List.copyOf(tickets);
        errors = List.copyOf(errors);
    }
}
