package ru.polyclinic.queue.datastructure;

import java.util.Arrays;

import ru.polyclinic.queue.model.Editable;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketOrder;

/**
 * Двоичная min-куча талонов, полное дерево прямо в собственном массиве:
 * приоритет родителя ≤ приоритетов детей. Вставка — «всплытие» вверх,
 * извлечение минимума — «погружение» вниз.
 *
 * <p>Порядок задаёт единственный компаратор {@link TicketOrder}
 * (срочность → время взятия → id) — просеивание нигде не сравнивает поля сам.
 *
 * <p>В кучу попадают только редактируемые талоны ({@link Editable}):
 * read-only {@code ClosedTicket} — архивная запись, следующий пациент из неё
 * не выбирается. Закрытые талоны живут в архиве {@code QueueService}.
 *
 * <p>Все мутирующие операции {@code synchronized}, а {@link #snapshot()}
 * возвращает копию — GUI-поток рисует спокойно, пока фоновый поток
 * перестраивает структуру (гонка из лабы 2 закрыта заранее).
 */
public final class HeapTicketQueueStore implements TicketQueueStore {

    private static final int DEFAULT_CAPACITY = 16;

    private Ticket[] heap;
    private int size;

    public HeapTicketQueueStore() {
        this(DEFAULT_CAPACITY);
    }

    public HeapTicketQueueStore(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        heap = new Ticket[capacity];
        size = 0;
    }

    // ---------- базовые индексы ----------

    private static int parent(int i) {
        return (i - 1) / 2;
    }

    private static int leftChild(int i) {
        return 2 * i + 1;
    }

    private static int rightChild(int i) {
        return 2 * i + 2;
    }

    // ---------- операции ----------

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
        ensureCapacity();
        heap[size] = ticket;
        size++;
        siftUp(size - 1);
    }

    @Override
    public synchronized Ticket peek() {
        if (size == 0) {
            throw new IllegalStateException("Heap is empty");
        }
        return heap[0];
    }

    @Override
    public synchronized Ticket poll() {
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

    @Override
    public synchronized Ticket findByCardNumber(String cardNumber) {
        int index = indexOfCardNumber(cardNumber);
        return index >= 0 ? heap[index] : null;
    }

    @Override
    public synchronized boolean removeByCardNumber(String cardNumber) {
        int index = indexOfCardNumber(cardNumber);
        if (index < 0) {
            return false;
        }
        removeAt(index);
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
        Ticket oldTicket = heap[index];
        Ticket newTicket = oldTicket.withUrgency(newUrgency);
        heap[index] = newTicket;
        // withUrgency сохраняет id и takenAt: сравнение сводится к срочности
        int cmp = TicketOrder.INSTANCE.compare(newTicket, oldTicket);
        if (cmp < 0) {
            siftUp(index);
        } else if (cmp > 0) {
            siftDown(index);
        }
        return true;
    }

    /**
     * K самых срочных: копия массива + heapsort на том же компараторе.
     * O(n + k log n), исходная куча не меняется.
     */
    @Override
    public synchronized Ticket[] topK(int k) {
        if (k < 0) {
            throw new IllegalArgumentException("K must not be negative");
        }
        int take = Math.min(k, size);
        Ticket[] copy = Arrays.copyOf(heap, size);
        // build-heap поверх копии (siftDown с конца)
        for (int i = size / 2 - 1; i >= 0; i--) {
            siftDownArray(copy, size, i);
        }
        Ticket[] result = new Ticket[take];
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

    /**
     * Слияние двух очередей: переносим элементы другой кучи без дубликатов
     * и перестраиваем свою за O(n) (build-heap), вместо n вставок по O(log n).
     * Другая очередь очищается — талон не может стоять в двух очередях сразу.
     */
    @Override
    public synchronized void merge(TicketQueueStore other) {
        if (other == null) {
            throw new IllegalArgumentException("Other queue cannot be null");
        }
        if (other == this) {
            return;
        }
        Ticket[] incoming = other.snapshot();
        for (Ticket ticket : incoming) {
            if (indexOfCardNumber(ticket.getCardNumber()) < 0) {
                ensureCapacity();
                heap[size++] = ticket;
            }
        }
        for (int i = size / 2 - 1; i >= 0; i--) {
            siftDown(i);
        }
        if (other instanceof HeapTicketQueueStore heapOther) {
            heapOther.clear();
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
        return Arrays.copyOf(heap, size);
    }

    /** Очистка (используется импортом из файла и слиянием как «донором»). */
    public synchronized void clear() {
        Arrays.fill(heap, 0, size, null);
        size = 0;
    }

    /**
     * Проверка инварианта: родитель ≤ детей, размер бьётся, null-дыр нет.
     * Вызывается из тестов после каждой операции и из debug-логи GUI.
     */
    public synchronized void assertHeapInvariant() {
        for (int i = 0; i < size; i++) {
            if (heap[i] == null) {
                throw new AssertionError("Null at index " + i);
            }
            if (i > 0 && TicketOrder.INSTANCE.compare(heap[parent(i)], heap[i]) > 0) {
                throw new AssertionError("Heap invariant broken at index " + i);
            }
        }
    }

    // ---------- внутренние ----------

    private void ensureCapacity() {
        if (size == heap.length) {
            heap = Arrays.copyOf(heap, heap.length * 2);
        }
    }

    private int indexOfCardNumber(String cardNumber) {
        for (int i = 0; i < size; i++) {
            if (heap[i].getCardNumber().equals(cardNumber)) {
                return i;
            }
        }
        return -1;
    }

    private void removeAt(int index) {
        size--;
        if (index != size) {
            heap[index] = heap[size];
            heap[size] = null;
            // удалённый элемент мог нарушить порядок в обе стороны
            if (index > 0 && TicketOrder.INSTANCE.compare(heap[index], heap[parent(index)]) < 0) {
                siftUp(index);
            } else {
                siftDown(index);
            }
        } else {
            heap[size] = null;
        }
    }

    private void siftUp(int i) {
        while (i > 0) {
            int p = parent(i);
            if (TicketOrder.INSTANCE.compare(heap[i], heap[p]) < 0) {
                swap(i, p);
                i = p;
            } else {
                break;
            }
        }
    }

    private void siftDown(int i) {
        while (leftChild(i) < size) {
            int smallest = leftChild(i);
            int right = rightChild(i);
            if (right < size && TicketOrder.INSTANCE.compare(heap[right], heap[smallest]) < 0) {
                smallest = right;
            }
            if (TicketOrder.INSTANCE.compare(heap[i], heap[smallest]) > 0) {
                swap(i, smallest);
                i = smallest;
            } else {
                break;
            }
        }
    }

    private void swap(int i, int j) {
        Ticket tmp = heap[i];
        heap[i] = heap[j];
        heap[j] = tmp;
    }

    /** Просеивание вниз по произвольному массиву заданной длины (для topK/heapsort). */
    private static void siftDownArray(Ticket[] array, int length, int i) {
        while (leftChild(i) < length) {
            int smallest = leftChild(i);
            int right = rightChild(i);
            if (right < length && TicketOrder.INSTANCE.compare(array[right], array[smallest]) < 0) {
                smallest = right;
            }
            if (TicketOrder.INSTANCE.compare(array[i], array[smallest]) > 0) {
                Ticket tmp = array[i];
                array[i] = array[smallest];
                array[smallest] = tmp;
                i = smallest;
            } else {
                break;
            }
        }
    }
}