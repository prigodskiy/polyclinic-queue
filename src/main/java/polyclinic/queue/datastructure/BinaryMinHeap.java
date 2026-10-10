package polyclinic.queue.datastructure;

import polyclinic.queue.model.Ticket;
import java.util.Arrays;

public class BinaryMinHeap {

    private Ticket[] heap;
    private int size;

    public BinaryMinHeap(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        heap = new Ticket[capacity];
        size = 0;
    }

    public BinaryMinHeap() {
        this(10);
    }

    private int parent(int i) {
        return (i - 1) / 2;
    }

    private int leftChild(int i) {
        return 2 * i + 1;
    }

    private int rightChild(int i) {
        return 2 * i + 2;
    }

    private boolean hasLeftChild(int i) {
        return leftChild(i) < size;
    }

    private boolean hasRightChild(int i) {
        return rightChild(i) < size;
    }

    private void swap(int i, int j) {
        Ticket temp = heap[i];
        heap[i] = heap[j];
        heap[j] = temp;
    }

    private void ensureCapacity() {
        if (size == heap.length) {
            heap = Arrays.copyOf(heap, heap.length * 2);
        }
    }

    public void insert(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }

        if (contains(ticket.getCardNumber())) {
            throw new DuplicateTicketException(ticket.getCardNumber());  // ← своё исключение
        }

        ensureCapacity();
        heap[size] = ticket;
        size++;
        siftUp(size - 1);
    }

    public boolean contains(String cardNumber) {
        for (int i = 0; i < size; i++) {
            if (heap[i].getCardNumber().equals(cardNumber)) {
                return true;
            }
        }
        return false;
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parentIdx = parent(i);

            if (heap[i].compareTo(heap[parentIdx]) < 0) {
                swap(i, parentIdx);
                i = parentIdx;
            } else {
                break;
            }
        }
    }

    public Ticket extractMin() {
        if (size == 0) {
            throw new IllegalStateException("Heap is empty");
        }

        Ticket min = heap[0];

        size--;
        heap[0] = heap[size];
        heap[size] = null;

        if (size > 0) {
            siftDown(0);
        }

        return min;
    }

    private void siftDown(int i) {
        while (hasLeftChild(i)) {
            int smallerChild = leftChild(i);

            if (hasRightChild(i)) {
                int rightIdx = rightChild(i);
                if (heap[rightIdx].compareTo(heap[smallerChild]) < 0) {
                    smallerChild = rightIdx;
                }
            }

            if (heap[i].compareTo(heap[smallerChild]) > 0) {
                swap(i, smallerChild);
                i = smallerChild;
            } else {
                break;
            }
        }
    }

    public Ticket getMin() {
        if (size == 0) {
            throw new IllegalStateException("Heap is empty");
        }
        return heap[0];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int getSize() {
        return size;
    }

    public Ticket[] getKMostUrgent(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("K must be positive");
        }
        if (k > size) {
            k = size;
        }

        Ticket[] tempHeap = Arrays.copyOf(heap, size);
        BinaryMinHeap tempQueue = new BinaryMinHeap(size);
        tempQueue.heap = tempHeap;
        tempQueue.size = size;

        Ticket[] result = new Ticket[k];
        for (int i = 0; i < k; i++) {
            result[i] = tempQueue.extractMin();
        }

        return result;
    }

    public void changePriority(String cardNumber, int newUrgency) {
        if (newUrgency < 0 || newUrgency > 3) {
            throw new IllegalArgumentException("Urgency must be in range [0, 3]");
        }

        int index = -1;
        for (int i = 0; i < size; i++) {
            if (heap[i].getCardNumber().equals(cardNumber)) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            throw new IllegalArgumentException("Ticket not found: " + cardNumber);
        }

        Ticket oldTicket = heap[index];
        Ticket newTicket = oldTicket.withUrgency(newUrgency);

        heap[index] = newTicket;

        if (newTicket.compareTo(oldTicket) < 0) {
            siftUp(index);
        } else if (newTicket.compareTo(oldTicket) > 0) {
            siftDown(index);
        }
    }

    public void merge(BinaryMinHeap other) {
        if (other == null) {
            throw new IllegalArgumentException("Other heap cannot be null");
        }

        for (int i = 0; i < other.size; i++) {
            insert(other.heap[i]);
        }
    }

    public void printHeap() {
        System.out.println("Binary Min-Heap");
        System.out.println("Size: " + size);
        for (int i = 0; i < size; i++) {
            System.out.println("[" + i + "] " + heap[i]);
        }
    }

    public void clear() {
        Arrays.fill(heap, null);
        size = 0;
    }

    public java.util.List<Ticket> getItemsForUI() {
        java.util.List<Ticket> list = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(heap[i]);
        }
        return list;
    }
}
