package ru.polyclinic.queue.model;

import java.util.Comparator;

/**
 * Единый источник порядка очереди: (срочность, время взятия, id).
 *
 * <p>Используется просеиванием кучи, topK, сортировкой таблицы GUI,
 * визуализацией и сериализацией — нигде больше сравнивать талоны нельзя,
 * иначе миграция формата рассинхронизирует структуру.
 *
 * <p>Tie-break по стабильному {@code id} делает порядок полным: две очереди
 * с одинаковым содержимым имеют идентичное представление (roundtrip-тесты
 * сериализации не flaky, визуализация детерминирована).
 */
public final class TicketOrder implements Comparator<Ticket> {

    public static final TicketOrder INSTANCE = new TicketOrder();

    private TicketOrder() {
    }

    @Override
    public int compare(Ticket a, Ticket b) {
        int byUrgency = Integer.compare(a.getUrgency(), b.getUrgency());
        if (byUrgency != 0) {
            return byUrgency;
        }
        int byTime = a.getTakenAt().compareTo(b.getTakenAt());
        if (byTime != 0) {
            return byTime;
        }
        return Long.compare(a.getId(), b.getId());
    }
}