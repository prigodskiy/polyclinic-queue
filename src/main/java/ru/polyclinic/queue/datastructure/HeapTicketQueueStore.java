package ru.polyclinic.queue.datastructure;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import ru.polyclinic.queue.model.Editable;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketOrder;

public final class HeapTicketQueueStore<T extends Ticket> implements TicketQueueStore<T> {

    private static final int DEFAULT_CAPACITY = 16;

    private Object[] heap;
    private int size;
    private Set<Integer> lastVisited = new HashSet<>();

    public HeapTicketQueueStore() {
        this(DEFAULT_CAPACITY);
    }

    public HeapTicketQueueStore(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity должен быть положительным");
        heap = new Object[capacity];
        size = 0;
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int i) {
        return (T) heap[i];
    }

    private static int parent(int i) { return (i - 1) / 2; }
    private static int leftChild(int i) { return 2 * i + 1; }
    private static int rightChild(int i) { return 2 * i + 2; }

    @Override
    public synchronized void add(T ticket) {
        if (ticket == null) throw new IllegalArgumentException("талон не может быть пустым");
        if (!(ticket instanceof Editable)) throw new IllegalArgumentException("закрытый талон нельзя поставить в живую очередь");

        ensureCapacity();
        heap[size] = ticket;
        size++;
        siftUp(size - 1);
    }

    @Override
    public synchronized T peek() {
        if (size == 0) throw new IllegalStateException("куча пуста");
        return elementAt(0);
    }

    @Override
    public synchronized T poll() {
        if (size == 0) throw new IllegalStateException("куча пуста");
        T min = elementAt(0);
        size--;
        heap[0] = heap[size];
        heap[size] = null;
        if (size > 0) siftDown(0);
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
        removeAt(index);
        return true;
    }

    @Override
    public synchronized boolean changePriority(String cardNumber, int newUrgency) {
        if (newUrgency < 0 || newUrgency > 3) throw new IllegalArgumentException("urgency должен быть в диапазоне [0, 3]");
        int index = indexOfCardNumber(cardNumber);
        if (index < 0) return false;

        T oldTicket = elementAt(index);
        @SuppressWarnings("unchecked")
        T newTicket = (T) oldTicket.withUrgency(newUrgency);
        heap[index] = newTicket;

        int cmp = TicketOrder.INSTANCE.compare(newTicket, oldTicket);
        if (cmp < 0) siftUp(index);
        else if (cmp > 0) siftDown(index);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized T[] topK(int k) {
        if (k < 0) throw new IllegalArgumentException("k не должно быть отрицательным");
        int take = Math.min(k, size);
        T[] copy = (T[]) Arrays.copyOf(heap, size, Ticket[].class);
        for (int i = size / 2 - 1; i >= 0; i--) siftDownArray(copy, size, i);

        T[] result = (T[]) new Ticket[take];
        int end = size;
        for (int i = 0; i < take; i++) {
            result[i] = copy[0];
            end--;
            copy[0] = copy[end];
            copy[end] = null;
            siftDownArray(copy, end, 0);
        }
        return result;
    }

    @Override
    public synchronized void merge(TicketQueueStore<T> other) {
        if (other == null || other == this) return;
        for (T ticket : other.snapshot()) {
            if (indexOfCardNumber(ticket.getCardNumber()) < 0) {
                ensureCapacity();
                heap[size++] = ticket;
            }
        }
        for (int i = size / 2 - 1; i >= 0; i--) siftDown(i);
        if (other instanceof HeapTicketQueueStore<?> heapOther) heapOther.clear();
    }

    @Override public synchronized int size() { return size; }
    @Override public synchronized boolean isEmpty() { return size == 0; }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized T[] snapshot() {
        return (T[]) Arrays.copyOf(heap, size, Ticket[].class);
    }

    public synchronized void clear() {
        Arrays.fill(heap, 0, size, null);
        size = 0;
    }

    public synchronized String dump() {
        if (size == 0) return "(куча пуста)";
        StringBuilder sb = new StringBuilder("двоичная min-куча (массив):\n");
        for (int i = 0; i < size; i++) {
            T t = elementAt(i);
            sb.append("  [").append(i).append("] ")
                    .append(t.getCardNumber())
                    .append(" / ").append(t.getFullName())
                    .append(" / ur=").append(t.getUrgency())
                    .append('\n');
        }
        return sb.toString();
    }

    public synchronized Set<Integer> getLastVisited() { return Set.copyOf(lastVisited); }

    private void ensureCapacity() {
        if (size == heap.length) {
            heap = Arrays.copyOf(heap, heap.length * 2);
        }
    }

    private int indexOfCardNumber(String cardNumber) {
        for (int i = 0; i < size; i++) {
            if (elementAt(i).getCardNumber().equals(cardNumber)) return i;
        }
        return -1;
    }

    private void removeAt(int index) {
        size--;
        if (index != size) {
            heap[index] = heap[size];
            heap[size] = null;
            if (index > 0 && TicketOrder.INSTANCE.compare(elementAt(index), elementAt(parent(index))) < 0) {
                siftUp(index);
            } else {
                siftDown(index);
            }
        } else {
            heap[size] = null;
        }
    }

    private void siftUp(int i) {
        lastVisited.clear();
        while (i > 0) {
            int p = parent(i);
            lastVisited.add(i);
            lastVisited.add(p);
            if (TicketOrder.INSTANCE.compare(elementAt(i), elementAt(p)) < 0) {
                swap(i, p);
                i = p;
            } else break;
        }
    }

    private void siftDown(int i) {
        lastVisited.clear();
        while (leftChild(i) < size) {
            int smallest = leftChild(i);
            int right = rightChild(i);
            lastVisited.add(i);
            if (right < size && TicketOrder.INSTANCE.compare(elementAt(right), elementAt(smallest)) < 0) {
                smallest = right;
                lastVisited.add(right);
            }
            lastVisited.add(smallest);
            if (TicketOrder.INSTANCE.compare(elementAt(i), elementAt(smallest)) > 0) {
                swap(i, smallest);
                i = smallest;
            } else break;
        }
    }

    private void swap(int i, int j) {
        Object tmp = heap[i];
        heap[i] = heap[j];
        heap[j] = tmp;
    }

    private static <T extends Ticket> void siftDownArray(T[] array, int length, int i) {
        while (leftChild(i) < length) {
            int smallest = leftChild(i);
            int right = rightChild(i);
            if (right < length && TicketOrder.INSTANCE.compare(array[right], array[smallest]) < 0) {
                smallest = right;
            }
            if (TicketOrder.INSTANCE.compare(array[i], array[smallest]) > 0) {
                T tmp = array[i];
                array[i] = array[smallest];
                array[smallest] = tmp;
                i = smallest;
            } else break;
        }
    }
}