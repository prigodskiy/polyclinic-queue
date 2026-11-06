package ru.polyclinic.queue.datastructure;

import java.util.Arrays;

import ru.polyclinic.queue.model.Editable;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketOrder;

public final class SortedArrayTicketQueueStore<T extends Ticket> implements TicketQueueStore<T> {

    private static final int DEFAULT_CAPACITY = 16;
    private Object[] items;
    private int size;

    public SortedArrayTicketQueueStore() {
        this(DEFAULT_CAPACITY);
    }

    public SortedArrayTicketQueueStore(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity должен быть положительным");
        items = new Object[capacity];
        size = 0;
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int i) {
        return (T) items[i];
    }

    @Override
    public synchronized void add(T ticket) {
        if (ticket == null) throw new IllegalArgumentException("талон не может быть пустым");
        if (!(ticket instanceof Editable)) throw new IllegalArgumentException("закрытый талон нельзя поставить в живую очередь");

        if (size == items.length) items = Arrays.copyOf(items, items.length * 2);

        int insertionPoint = binarySearchInsertionPoint(ticket);
        System.arraycopy(items, insertionPoint, items, insertionPoint + 1, size - insertionPoint);
        items[insertionPoint] = ticket;
        size++;
    }

    @Override
    public synchronized T peek() {
        if (size == 0) throw new IllegalStateException("очередь пуста");
        return elementAt(0);
    }

    @Override
    public synchronized T poll() {
        T min = peek();
        System.arraycopy(items, 1, items, 0, size - 1);
        items[--size] = null;
        return min;
    }

    @Override
    public synchronized T findByCardNumber(String cardNumber) {
        int index = indexOfCardNumber(cardNumber);
        return index >= 0 ? elementAt(index) : null;
    }

    @Override
    public synchronized boolean removeByCardNumber(String cardNumber) {
        int index = indexOfCardNumber(cardNumber);
        if (index < 0) return false;
        System.arraycopy(items, index + 1, items, index, size - index - 1);
        items[--size] = null;
        return true;
    }

    @Override
    public synchronized boolean changePriority(String cardNumber, int newUrgency) {
        if (newUrgency < 0 || newUrgency > 3) throw new IllegalArgumentException("urgency должен быть в диапазоне [0, 3]");
        int index = indexOfCardNumber(cardNumber);
        if (index < 0) return false;

        @SuppressWarnings("unchecked")
        T updated = (T) elementAt(index).withUrgency(newUrgency);
        System.arraycopy(items, index + 1, items, index, size - index - 1);
        items[--size] = null;
        add(updated);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized T[] topK(int k) {
        if (k < 0) throw new IllegalArgumentException("k не должно быть отрицательным");
        return (T[]) Arrays.copyOfRange(items, 0, Math.min(k, size), Ticket[].class);
    }

    @Override
    public synchronized void merge(TicketQueueStore<T> other) {
        if (other == null || other == this) return;
        for (T ticket : other.snapshot()) {
            if (indexOfCardNumber(ticket.getCardNumber()) < 0) add(ticket);
        }
        if (other instanceof SortedArrayTicketQueueStore<?> sortedOther) sortedOther.clear();
    }

    @Override public synchronized int size() { return size; }
    @Override public synchronized boolean isEmpty() { return size == 0; }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized T[] snapshot() {
        return (T[]) Arrays.copyOf(items, size, Ticket[].class);
    }

    public synchronized void clear() {
        Arrays.fill(items, 0, size, null);
        size = 0;
    }

    private int indexOfCardNumber(String cardNumber) {
        for (int i = 0; i < size; i++) {
            if (elementAt(i).getCardNumber().equals(cardNumber)) return i;
        }
        return -1;
    }

    private int binarySearchInsertionPoint(T ticket) {
        int low = 0, high = size;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (TicketOrder.INSTANCE.compare(elementAt(mid), ticket) < 0) low = mid + 1;
            else high = mid;
        }
        return low;
    }
}