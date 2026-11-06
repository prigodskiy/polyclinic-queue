package ru.polyclinic.queue.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.polyclinic.queue.datastructure.DuplicateTicketException;
import ru.polyclinic.queue.datastructure.HeapTicketQueueStore;
import ru.polyclinic.queue.datastructure.TicketQueueStore;
import ru.polyclinic.queue.model.ClosedTicket;
import ru.polyclinic.queue.model.TicketValidator;
import ru.polyclinic.queue.model.Ticket;

public final class QueueService {

    private final TicketQueueStore<Ticket> queue = new HeapTicketQueueStore<>();
    private final List<ClosedTicket> archive = new ArrayList<>();

    private final Set<String> knownCards = new HashSet<>();
    private final Set<Long> archivedIds = new HashSet<>();

    public synchronized void add(Ticket ticket) {
        if (ticket instanceof ClosedTicket) {
            throw new IllegalArgumentException("закрытый талон нельзя поставить в очередь");
        }
        if (!knownCards.add(ticket.getCardNumber())) {
            throw new DuplicateTicketException(ticket.getCardNumber());
        }
        queue.add(ticket);
    }

    public synchronized Ticket acceptNext() {
        Ticket next = queue.poll();
        if (next != null) {
            knownCards.remove(next.getCardNumber());
        }
        return next;
    }

    public synchronized void updateTicket(Ticket updated) {
        if (updated == null || updated instanceof ClosedTicket) {
            throw new IllegalArgumentException("закрытый талон нельзя редактировать");
        }
        queue.removeByCardNumber(updated.getCardNumber());
        queue.add(updated);
    }

    public synchronized Ticket peekNext() {
        return queue.isEmpty() ? null : queue.peek();
    }

    public synchronized boolean removeByCardNumber(String cardNumber) {
        boolean removed = queue.removeByCardNumber(cardNumber);
        if (removed) knownCards.remove(cardNumber);
        return removed;
    }

    public synchronized boolean changePriority(String cardNumber, int newUrgency) {
        return queue.changePriority(cardNumber, newUrgency);
    }

    public synchronized Ticket[] topK(int k) { return queue.topK(k); }
    public synchronized int queueSize()      { return queue.size(); }
    public synchronized boolean isQueueEmpty() { return queue.isEmpty(); }

    public synchronized void merge(QueueService other) {
        if (other == null || other == this) return;

        Ticket[] incomingQueue = other.queue.snapshot();

        queue.merge(other.queue);

        for (Ticket t : incomingQueue) {
            knownCards.add(t.getCardNumber());
        }

        for (ClosedTicket c : other.getArchive()) {
            if (archivedIds.add(c.getId())) {
                archive.add(c);
            }
        }
        other.archive.clear();
    }

    public synchronized Ticket[] snapshot() {
        int size = queue.size();
        return size == 0 ? new Ticket[0] : queue.topK(size);
    }

    public synchronized Ticket findByCardNumber(String cardNumber) {
        return queue.findByCardNumber(cardNumber);
    }

    public synchronized void addToArchive(ClosedTicket ticket) {
        if (archivedIds.add(ticket.getId())) {
            archive.add(ticket);
        }
    }

    public synchronized List<ClosedTicket> getArchive() {
        return List.copyOf(archive);
    }

    public synchronized boolean removeFromArchive(long ticketId) {
        boolean removed = archive.removeIf(t -> t.getId() == ticketId);
        if (removed) {
            archivedIds.remove(ticketId);
        }
        return removed;
    }

    public synchronized int archiveSize() { return archive.size(); }
    public synchronized Ticket[] closedSnapshot() { return archive.toArray(Ticket[]::new); }

    public synchronized void loadAll(List<Ticket> tickets) {
        for (Ticket ticket : tickets) {
            List<String> errors = TicketValidator.validate(ticket);
            if (!errors.isEmpty()) {
                System.err.println("Пропущен битый талон: " + ticket + ". Ошибки: " + errors);
                continue;
            }

            if (ticket instanceof ClosedTicket closed) {
                if (archivedIds.add(closed.getId())) archive.add(closed);
            } else {
                if (knownCards.add(ticket.getCardNumber())) queue.add(ticket);
            }
        }
    }
    public synchronized List<Ticket> exportAll() {
        List<Ticket> all = new ArrayList<>();
        for (Ticket ticket : queue.snapshot()) all.add(ticket);
        all.addAll(archive);
        return all;
    }

    public synchronized void clearAll() {
        if (queue instanceof HeapTicketQueueStore<?> heap) heap.clear();
        archive.clear();
        knownCards.clear();
        archivedIds.clear();
    }

    public synchronized void printStructure() {
        if (queue instanceof HeapTicketQueueStore<?> heap) {
            System.out.println("\n" + heap.dump() + "\n");
        }
    }
}