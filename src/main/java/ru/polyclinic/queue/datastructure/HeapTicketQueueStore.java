package ru.polyclinic.queue.datastructure;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    // ═══════════════════════════════════════════════════════════════
    // НОВОЕ: Поле для отслеживания посещённых узлов при операциях
    // ═══════════════════════════════════════════════════════════════
    private Set<Integer> lastVisited = new HashSet<>();

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

    // ═══════════════════════════════════════════════════════════════
    // НОВОЕ: Методы для визуализации кучи
    // ═══════════════════════════════════════════════════════════════

    /**
     * Получить индексы узлов, посещённых при последней операции (siftUp/siftDown).
     * Используется для визуализации — пометки звёздочкой (*) в дампе.
     *
     * @return неизменяемое множество индексов
     */
    public synchronized Set<Integer> getLastVisited() {
        return Set.copyOf(lastVisited);
    }

    /**
     * ASCII-дамп кучи в виде дерева с линиями / и \\ между родителем и детьми.
     * Посещённые узлы помечены символом *.
     *
     * <p>Пример вывода:
     * <pre>
     * ═══ Структура кучи (размер: 7) ═══
     *                  [A1(0)*]
     *                 /        \\
     *          [B3(3)]          [A2(2)]
     *          /     \\         /     \\
     *    [C5(5)] [B4(4)]  [D3(3)] [C6(6)]
     * ═══════════════════════════════════════════════
     * </pre>
     */
    public synchronized String dump() {
        if (size == 0) {
            return "(куча пуста)";
        }

        // 1. Вычисляем максимальную длину метки узла
        int maxLabel = 0;
        for (int i = 0; i < size; i++) {
            maxLabel = Math.max(maxLabel, formatLabel(i).length());
        }
        int nodeWidth = maxLabel + 2; // +2 для скобок []

        // 2. Глубина дерева
        int depth = 0;
        for (int t = size; t > 0; t >>= 1) depth++;

        // 3. Ширина холста: на нижнем уровне leafCount ячеек по cellWidth
        int leafCount = 1 << (depth - 1);
        int cellWidth = 2 * nodeWidth + 2; // зазор, чтобы линии не залезали на узлы
        int totalWidth = leafCount * cellWidth;

        // 4. Рисуем по уровням
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Структура кучи (размер: ").append(size).append(") ═══\n");

        for (int level = 0; level < depth; level++) {
            int start = (1 << level) - 1;
            int end = Math.min(start + (1 << level), size);

            // --- Строка с узлами ---
            char[] nodeLine = new char[totalWidth];
            java.util.Arrays.fill(nodeLine, ' ');

            for (int i = start; i < end; i++) {
                int posInLevel = i - start;
                int center = nodeCenter(level, posInLevel, totalWidth);
                String node = "[" + formatLabel(i) + "]";
                int nodeStart = center - node.length() / 2;
                for (int c = 0; c < node.length(); c++) {
                    int col = nodeStart + c;
                    if (col >= 0 && col < totalWidth) {
                        nodeLine[col] = node.charAt(c);
                    }
                }
            }
            sb.append(trimRight(nodeLine)).append('\n');

            // --- Строка с линиями / и \\ ---
            if (level < depth - 1 && start < size) {
                char[] slashLine = new char[totalWidth];
                java.util.Arrays.fill(slashLine, ' ');

                for (int i = start; i < end; i++) {
                    int posInLevel = i - start;
                    int parentCenter = nodeCenter(level, posInLevel, totalWidth);

                    int leftChild = 2 * i + 1;
                    int rightChild = 2 * i + 2;

                    // Линия / к левому ребёнку
                    if (leftChild < size) {
                        int childPos = posInLevel * 2;
                        int childCenter = nodeCenter(level + 1, childPos, totalWidth);
                        int slashPos = (parentCenter + childCenter) / 2;
                        if (slashPos >= 0 && slashPos < totalWidth) {
                            slashLine[slashPos] = '/';
                        }
                    }

                    // Линия \\ к правому ребёнку
                    if (rightChild < size) {
                        int childPos = posInLevel * 2 + 1;
                        int childCenter = nodeCenter(level + 1, childPos, totalWidth);
                        int slashPos = (parentCenter + childCenter) / 2;
                        if (slashPos >= 0 && slashPos < totalWidth) {
                            slashLine[slashPos] = '\\';
                        }
                    }
                }
                sb.append(trimRight(slashLine)).append('\n');
            }
        }

        sb.append("══════════════════════════════════════════════════");
        return sb.toString();
    }

    /**
     * Вычисляет позицию центра узла на холсте.
     * Узлы равномерно распределены: на уровне L их 2^L,
     * и каждый стоит по центру своей «ячейки».
     */
    private static int nodeCenter(int level, int posInLevel, int totalWidth) {
        int nodesOnLevel = 1 << level;
        return (2 * posInLevel + 1) * totalWidth / (2 * nodesOnLevel);
    }

    /** Формат метки узла: номер карты + срочность + пометка *. */
    private String formatLabel(int index) {
        String label = heap[index].getCardNumber() + "(" + heap[index].getUrgency() + ")";
        if (lastVisited.contains(index)) label += "*";
        return label;
    }

    /** Удаление trailing-пробелов из массива char. */
    private static String trimRight(char[] chars) {
        int end = chars.length;
        while (end > 0 && chars[end - 1] == ' ') end--;
        return new String(chars, 0, end);
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

    // ═══════════════════════════════════════════════════════════════
    // ИЗМЕНЕНО: siftUp теперь отслеживает посещённые узлы
    // ═══════════════════════════════════════════════════════════════
    private void siftUp(int i) {
        lastVisited.clear(); // Очищаем перед новой операцией
        while (i > 0) {
            int p = parent(i);
            lastVisited.add(i);   // Помечаем текущий узел
            lastVisited.add(p);   // Помечаем родителя
            if (TicketOrder.INSTANCE.compare(heap[i], heap[p]) < 0) {
                swap(i, p);
                i = p;
            } else {
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ИЗМЕНЕНО: siftDown теперь отслеживает посещённые узлы
    // ═══════════════════════════════════════════════════════════════
    private void siftDown(int i) {
        lastVisited.clear(); // Очищаем перед новой операцией
        while (leftChild(i) < size) {
            int smallest = leftChild(i);
            int right = rightChild(i);
            lastVisited.add(i); // Помечаем текущий узел
            if (right < size && TicketOrder.INSTANCE.compare(heap[right], heap[smallest]) < 0) {
                smallest = right;
                lastVisited.add(right); // Помечаем правого ребёнка, если он меньше
            }
            lastVisited.add(smallest); // Помечаем наименьшего ребёнка
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