package ru.polyclinic.queue.service;

import org.junit.jupiter.api.Test;

import ru.polyclinic.queue.datastructure.DuplicateTicketException;
import ru.polyclinic.queue.model.ClosedTicket;
import ru.polyclinic.queue.model.Ticket;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueueServiceTest {

    private static Ticket regular(String card) {
        return new Ticket(card, "Иван Иванович", "101", 2);
    }

    @Test
    void addDuplicateThrows() {
        QueueService s = new QueueService();
        s.add(regular("100"));
        assertThrows(DuplicateTicketException.class, () -> s.add(regular("100")));
    }

    @Test
    void acceptNextFreesCard() {
        QueueService s = new QueueService();
        s.add(regular("100"));
        s.acceptNext();
        // после обслуживания карта освобождается — можно добавить снова
        assertDoesNotThrow(() -> s.add(regular("100")));
    }

    @Test
    void changePriorityMissingReturnsFalse() {
        assertFalse(new QueueService().changePriority("000", 1));
    }

    @Test
    void topKZeroReturnsEmpty() {
        QueueService s = new QueueService();
        s.add(regular("100"));
        assertEquals(0, s.topK(0).length);
    }

    @Test
    void topKMoreThanSizeReturnsAll() {
        QueueService s = new QueueService();
        s.add(regular("100"));
        s.add(regular("200"));
        assertEquals(2, s.topK(10).length);
    }

    @Test
    void topKNegativeThrows() {
        QueueService s = new QueueService();
        assertThrows(IllegalArgumentException.class, () -> s.topK(-1));
    }

    /** РЕГРЕССИЯ: до фикса merge не пополнял knownCards — дубликат проходил. */
    @Test
    void mergeKeepsDuplicateDetection() {
        QueueService a = new QueueService();
        QueueService b = new QueueService();
        b.add(regular("100"));
        a.merge(b);
        assertThrows(DuplicateTicketException.class, () -> a.add(regular("100")));
        assertTrue(b.isQueueEmpty(), "очередь-источник должна очиститься");
    }

    @Test
    void mergeMovesArchive() {
        QueueService a = new QueueService();
        QueueService b = new QueueService();
        ClosedTicket c = new ClosedTicket(1, "100", "Иван Иванович", "101", 0,
                LocalDateTime.now(), null);
        b.addToArchive(c);
        a.merge(b);
        assertEquals(1, a.archiveSize());
        assertEquals(0, b.archiveSize());
    }

    @Test
    void archiveDedupOnLoad() {
        QueueService s = new QueueService();
        ClosedTicket c = new ClosedTicket(1, "100", "Иван Иванович", "101", 0,
                LocalDateTime.now(), null);
        s.loadAll(List.of(c, c));
        assertEquals(1, s.archiveSize(), "дубликат по id не должен попасть в архив");
    }

    @Test
    void closedTicketCannotBeAddedToQueue() {
        QueueService s = new QueueService();
        ClosedTicket c = new ClosedTicket(1, "100", "Иван Иванович", "101", 0,
                LocalDateTime.now(), null);
        assertThrows(IllegalArgumentException.class, () -> s.add(c));
    }

    @Test
    void removeByCardNumberFreesCard() {
        QueueService s = new QueueService();
        s.add(regular("100"));
        assertTrue(s.removeByCardNumber("100"));
        assertDoesNotThrow(() -> s.add(regular("100")));
    }
}