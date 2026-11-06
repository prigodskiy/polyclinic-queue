package ru.polyclinic.queue.model;

import java.util.Comparator;

public final class TicketOrder implements Comparator<Ticket> {

    public static final TicketOrder INSTANCE = new TicketOrder();

    private TicketOrder() {
    }

    @Override
    public int compare(Ticket a, Ticket b) {
        int byUrgency = Integer.compare(a.getUrgency(), b.getUrgency());
        if (byUrgency != 0) {
            return byUrgency;
        }
        int byTime = a.getTakenAt().compareTo(b.getTakenAt());
        if (byTime != 0) {
            return byTime;
        }
        return Long.compare(a.getId(), b.getId());
    }
}