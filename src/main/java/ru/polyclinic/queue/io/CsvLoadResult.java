package ru.polyclinic.queue.io;

import java.util.List;

import ru.polyclinic.queue.model.Ticket;

/**
 * Результат загрузки: корректные талоны отдельно, битые строки — списком
 * предупреждений. Требование лабы 1: «битые строки пропускаются», но молча
 * терять данные нельзя — GUI показывает warnings.
 */
public record CsvLoadResult(List<Ticket> tickets, List<CsvRowError> errors) {

    public CsvLoadResult {
        tickets = List.copyOf(tickets);
        errors = List.copyOf(errors);
    }
}
