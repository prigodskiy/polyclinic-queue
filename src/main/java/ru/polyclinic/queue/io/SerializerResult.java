package ru.polyclinic.queue.io;

import ru.polyclinic.queue.model.Ticket;
import java.util.List;

public record SerializerResult(List<Ticket> tickets, int version) {
    public SerializerResult {
        tickets = List.copyOf(tickets);
    }
}