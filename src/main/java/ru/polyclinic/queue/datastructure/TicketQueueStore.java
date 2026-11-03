package ru.polyclinic.queue.datastructure;

import ru.polyclinic.queue.model.Ticket;

public interface TicketQueueStore<T extends Ticket> {
    void add(T ticket);
    T peek();
    T poll();
    T findByCardNumber(String cardNumber);
    boolean removeByCardNumber(String cardNumber);
    boolean changePriority(String cardNumber, int newUrgency);
    T[] topK(int k);
    void merge(TicketQueueStore<T> other);
    int size();
    boolean isEmpty();
    T[] snapshot();
}