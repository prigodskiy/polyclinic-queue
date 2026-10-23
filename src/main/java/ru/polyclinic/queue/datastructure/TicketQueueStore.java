package ru.polyclinic.queue.datastructure;

import ru.polyclinic.queue.model.Ticket;

/**
 * Контракт хранилища очереди под конкретную сущность (не дженерик —
 * перевод на {@code <K, T>} будет в лабе 2 как «максимум»).
 */
public interface TicketQueueStore {

    /** Вставка талона («всплытие» вверх). */
    void add(Ticket ticket);

    /** Следующий по приоритету без извлечения. */
    Ticket peek();

    /** Извлечение минимума — «обслужить пациента». */
    Ticket poll();

    Ticket findByCardNumber(String cardNumber);

    boolean removeByCardNumber(String cardNumber);

    /** Изменение приоритета с просеиванием в нужную сторону. */
    boolean changePriority(String cardNumber, int newUrgency);

    /** K самых срочных — копия массива, отсортированная по порядку очереди. */
    Ticket[] topK(int k);

    /** Слияние: все талоны другой очереди переходят в эту (другая очищается). */
    void merge(TicketQueueStore other);

    int size();

    boolean isEmpty();

    /** Независимый снимок внутреннего массива (для GUI/визуализации без гонок). */
    Ticket[] snapshot();
}