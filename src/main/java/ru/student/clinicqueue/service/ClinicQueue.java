package ru.student.clinicqueue.service;

import java.util.ArrayList;
import java.util.List;

import ru.student.clinicqueue.model.Editable;
import ru.student.clinicqueue.model.Ticket;
import ru.student.clinicqueue.model.TicketValidator;

public final class ClinicQueue {
    private final List<Ticket> tickets = new ArrayList<>();

    public List<Ticket> getAll() {
        return List.copyOf(tickets);
    }

    public void add(Ticket ticket) {
        requireEditable(ticket);
        requireValid(ticket);
        tickets.add(ticket);
    }

    public void update(int index, Ticket replacement) {
        requireEditable(tickets.get(index));
        requireEditable(replacement);
        requireValid(replacement);
        tickets.set(index, replacement);
    }

    public void replaceAll(List<Ticket> loadedTickets) {
        for (Ticket ticket : loadedTickets) {
            requireValid(ticket);
        }
        tickets.clear();
        tickets.addAll(loadedTickets);
    }

    public int size() {
        return tickets.size();
    }

    private void requireEditable(Ticket ticket) {
        if (!(ticket instanceof Editable)) {
            throw new IllegalArgumentException("Закрытый талон доступен только для чтения");
        }
    }

    private void requireValid(Ticket ticket) {
        List<String> errors = TicketValidator.validate(ticket);
        if (!errors.isEmpty()) {
            throw new TicketValidationException(errors);
        }
    }
}
