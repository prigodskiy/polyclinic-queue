package ru.polyclinic.queue.datastructure;

import java.util.Arrays;

import ru.polyclinic.queue.model.Editable;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketOrder;

/**
 * «Ближайший аналог» кучи для сравнительного обзора (максимум лабы 2):
 * собственный массив, упорядоченный тем же компаратором {@link TicketOrder}.
 * Вставка — бинарный поиск места + сдвиг O(n); peek/poll — O(1).
 *
 * <p>Один и тот же набор тестов гоняется параметрически против этой реализации
 * и {@link HeapTicketQueueStore} — одинаковый ответственный результат даёт
 * честное сравнение в отчёте без java.util.PriorityQueue в основном коде.
 */
public final class SortedArrayTicketQueueStore implements TicketQueueStore {

    private static final int DEFAULT_CAPACITY = 16;

    private Ticket[] items; // по возрастанию приоритета
    private int size;

    public SortedArrayTicketQueueStore() {
        this(DEFAULT_CAPACITY);
    }

    public SortedArrayTicketQueueStore(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        items = new Ticket[capacity];
        size = 0;
    }

    @Override
    public synchronized void add(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }
        if (!(ticket instanceof Editable)) {
            throw new IllegalArgumentException("Закрытый талон нельзя поставить в живую очередь");
        }
        int index = indexOfCardNumber(ticket.getCardNumber());
        if (index >= 0) {
            throw new DuplicateTicketException(ticket.getCardNumber());
        }
        if (size == items.length) {
            items = Arrays.copyOf(items, items.length * 2);
        }
        int insertionPoint = binarySearchInsertionPoint(ticket);
        System.arraycopy(items, insertionPoint, items, insertionPoint + 1, size - insertionPoint);
        items[insertionPoint] = ticket;
        size++;
    }

    @Override
    public synchronized Ticket peek() {
        if (size == 0) {
            throw new IllegalStateException("Queue is empty");
        }
        return items[0];
    }

    @Override
    public synchronized Ticket poll() {
        Ticket min = peek();
        System.arraycopy(items, 1, items, 0, size - 1);
        items[--size] = null;
        return min;
    }

    @Override
    public synchronized Ticket findByCardNumber(String cardNumber) {
        int index = indexOfCardNumber(cardNumber);
        return index >= 0 ? items[index] : null;
    }

    @Override
    public synchronized boolean removeByCardNumber(String cardNumber) {
        int index = indexOfCardNumber(cardNumber);
        if (index < 0) {
            return false;
        }
        System.arraycopy(items, index + 1, items, index, size - index - 1);
        items[--size] = null;
        return true;
    }

    @Override
    public synchronized boolean changePriority(String cardNumber, int newUrgency) {
        if (newUrgency < 0 || newUrgency > 3) {
            throw new IllegalArgumentException("Urgency must be in range [0, 3]");
        }
        int index = indexOfCardNumber(cardNumber);
        if (index < 0) {
            return false;
        }
        Ticket updated = items[index].withUrgency(newUrgency);
        System.arraycopy(items, index + 1, items, index, size - index - 1);
        items[--size] = null;
        add(updated);
        return true;
    }

    @Override
    public synchronized Ticket[] topK(int k) {
        if (k < 0) {
            throw new IllegalArgumentException("K must not be negative");
        }
        return Arrays.copyOfRange(items, 0, Math.min(k, size));
    }

    @Override
    public synchronized void merge(TicketQueueStore other) {
        if (other == null) {
            throw new IllegalArgumentException("Other queue cannot be null");
        }
        if (other == this) {
            return;
        }
        for (Ticket ticket : other.snapshot()) {
            if (indexOfCardNumber(ticket.getCardNumber()) < 0) {
                add(ticket);
            }
        }
        if (other instanceof SortedArrayTicketQueueStore sortedOther) {
            sortedOther.clear();
        }
    }

    @Override
    public synchronized int size() {
        return size;
    }

    @Override
    public synchronized boolean isEmpty() {
        return size == 0;
    }

    @Override
    public synchronized Ticket[] snapshot() {
        return Arrays.copyOf(items, size);
    }

    public synchronized void clear() {
        Arrays.fill(items, 0, size, null);
        size = 0;
    }

    /** Инвариант: массив строго неубывающий по порядку очереди. */
    public synchronized void assertSortedInvariant() {
        for (int i = 1; i < size; i++) {
            if (TicketOrder.INSTANCE.compare(items[i - 1], items[i]) > 0) {
                throw new AssertionError("Order broken at index " + i);
            }
        }
    }

    private int indexOfCardNumber(String cardNumber) {
        for (int i = 0; i < size; i++) {
            if (items[i].getCardNumber().equals(cardNumber)) {
                return i;
            }
        }
        return -1;
    }

    private int binarySearchInsertionPoint(Ticket ticket) {
        int low = 0;
        int high = size;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (TicketOrder.INSTANCE.compare(items[mid], ticket) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}