package ru.polyclinic.queue.service;

import java.util.ArrayList;
import java.util.List;

import ru.polyclinic.queue.datastructure.DuplicateTicketException;
import ru.polyclinic.queue.datastructure.HeapTicketQueueStore;
import ru.polyclinic.queue.datastructure.TicketQueueStore;
import ru.polyclinic.queue.model.ClosedTicket;
import ru.polyclinic.queue.model.Editable;
import ru.polyclinic.queue.model.Ticket;

/**
 * Фасад «очередь + архив». Единственная точка входа для GUI (и будущих REST/Android).
 *
 * <p>Живая очередь — min-куча ({@link TicketQueueStore}), в неё попадают только
 * редактируемые талоны. Закрытые талоны — read-only архив: отдельный список,
 * не участвующий в выборке следующего пациента. Списки здесь допустимы:
 * основное хранилище по варианту — куча, архив же просто хранится и показывается.
 */
public final class QueueService {

    private final TicketQueueStore queue = new HeapTicketQueueStore();
    private final List<ClosedTicket> archive = new ArrayList<>();

    // ---------- живая очередь ----------

    /** Вставить обычный или домашний талон. ClosedTicket сюда попасть не может. */
    public void add(Ticket ticket) {
        if (ticket instanceof ClosedTicket) {
            throw new IllegalArgumentException("Закрытый талон нельзя поставить в очередь");
        }
        queue.add(ticket);
    }

    /** Принять следующего пациента: извлечение минимума из кучи. */
    public Ticket acceptNext() {
        return queue.poll();
    }

    /**
     * Правка выбранного талона одним диалогом (ФИО, кабинет, срочность).
     * Отдельная кнопка «Срочность…» не нужна — приоритет меняется здесь,
     * а куча сама просеивает обновлённую копию на своё место.
     */
    public void updateTicket(Ticket updated) {
        if (!(updated instanceof Editable)) {
            throw new IllegalArgumentException("Закрытый талон нельзя редактировать");
        }
        // id и cardNumber неизменяемы (withChanges их сохраняет) — замена безопасна
        queue.removeByCardNumber(updated.getCardNumber());
        queue.add(updated);
    }

    public Ticket peekNext() {
        return queue.isEmpty() ? null : queue.peek();
    }

    public boolean removeByCardNumber(String cardNumber) {
        return queue.removeByCardNumber(cardNumber);
    }

    public boolean changePriority(String cardNumber, int newUrgency) {
        return queue.changePriority(cardNumber, newUrgency);
    }

    public Ticket[] topK(int k) {
        return queue.topK(k);
    }

    /** Слияние другой очереди в эту (донор очищается). */
    public void merge(QueueService other) {
        queue.merge(other.queue);
        archive.addAll(other.archive);
        other.archive.clear();
    }

    public int queueSize() {
        return queue.size();
    }

    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    public Ticket[] snapshot() {
        int size = queue.size();
        return size == 0 ? new Ticket[0] : queue.topK(size);
    }

    public Ticket findByCardNumber(String cardNumber) {
        return queue.findByCardNumber(cardNumber);
    }

    // ---------- архив закрытых талонов ----------

    public void addToArchive(ClosedTicket ticket) {
        archive.add(ticket);
    }

    public List<ClosedTicket> getArchive() {
        return List.copyOf(archive);
    }

    /** Удаление записи из архива закрытых талонов (по id, данные read-only). */
    public boolean removeFromArchive(long ticketId) {
        return archive.removeIf(t -> t.getId() == ticketId);
    }

    public int archiveSize() {
        return archive.size();
    }

    /** Снимок архива в типе Ticket[] — таблица архива принимает тот же тип, что и таблица очереди. */
    public Ticket[] closedSnapshot() {
        return archive.toArray(Ticket[]::new);
    }

    // ---------- импорт/экспорт ----------

    /**
     * Загрузка из CSV: closed-талоны уходят в архив, остальные — в кучу.
     * Дубликаты по № карты (и в файле, и с текущей очередью) молча
     * пропускаются: одна битая строка не должна ронять всю загрузку.
     */
    public void loadAll(List<Ticket> tickets) {
        for (Ticket ticket : tickets) {
            if (ticket instanceof ClosedTicket closed) {
                archive.add(closed);
            } else {
                try {
                    queue.add(ticket);
                } catch (DuplicateTicketException ignored) {
                    // дубликат карты — пропускаем строку
                }
            }
        }
    }

    /** Выгрузка всего состояния (куча + архив) одним списком для CSV-кодека. */
    public List<Ticket> exportAll() {
        List<Ticket> all = new ArrayList<>();
        for (Ticket ticket : queue.snapshot()) {
            all.add(ticket);
        }
        all.addAll(archive);
        return all;
    }

    public void clearAll() {
        if (queue instanceof HeapTicketQueueStore heap) {
            heap.clear();
        }
        archive.clear();
    }

    /** Для отладки GUI и тестов: бросает AssertionError при порче структуры. */
    public void assertQueueInvariant() {
        if (queue instanceof HeapTicketQueueStore heap) {
            heap.assertHeapInvariant();
        }
    }
}