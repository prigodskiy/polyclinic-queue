package ru.polyclinic.queue.service;

import java.util.PriorityQueue;
import ru.polyclinic.queue.datastructure.HeapTicketQueueStore;
import ru.polyclinic.queue.datastructure.SortedArrayTicketQueueStore;
import ru.polyclinic.queue.datastructure.TicketQueueStore;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketOrder;

public class Benchmark {

    public static String run() {
        int[] sizes = {10_000, 100_000};
        StringBuilder report = new StringBuilder("ЗАМЕРЫ ВРЕМЕНИ (в миллисекундах):\n\n");

        for (int size : sizes) {
            System.out.println("[БЕНЧМАРК] Размер " + size + " — генерирую талоны...");
            Ticket[] rawTickets = new TicketGenerator().generate(size, (c, t) -> {});

            report.append("--- Размер структуры: ").append(size).append(" ---\n");

            System.out.println("[БЕНЧМАРК]   Собственная Куча...");
            report.append("Собственная Куча:        ").append(testStore(new HeapTicketQueueStore<>(), rawTickets)).append("\n");

            System.out.println("[БЕНЧМАРК]   Сортированный Массив (может быть долго!)...");
            report.append("Сортированный Массив:    ").append(testStore(new SortedArrayTicketQueueStore<>(), rawTickets)).append("\n");

            System.out.println("[БЕНЧМАРК]   java.util.PriorityQueue...");
            report.append("java.util.PriorityQueue: ").append(testJavaUtilPQ(rawTickets)).append("\n\n");
        }
        return report.toString();
    }

    private static String testStore(TicketQueueStore<Ticket> store, Ticket[] tickets) {
        // ВСТАВКА
        long t1 = System.nanoTime();
        for (Ticket t : tickets) {
            try { store.add(t); } catch (Exception ignored) {}
        }
        long addTime = (System.nanoTime() - t1) / 1_000_000;

        // ПОИСК (1000 запросов)
        long t2 = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            store.findByCardNumber(tickets[i].getCardNumber());
        }
        long searchTime = (System.nanoTime() - t2) / 1_000_000;

        // УДАЛЕНИЕ по номеру карты (1000 штук)
        long t3 = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            store.removeByCardNumber(tickets[i].getCardNumber());
        }
        long removeTime = (System.nanoTime() - t3) / 1_000_000;

        // TOP-K (первые 100)
        long t4 = System.nanoTime();
        store.topK(100);
        long topKTime = (System.nanoTime() - t4) / 1_000_000;

        // ИЗВЛЕЧЕНИЕ (poll)
        long t5 = System.nanoTime();
        while (!store.isEmpty()) store.poll();
        long pollTime = (System.nanoTime() - t5) / 1_000_000;

        return String.format("add=%d, search=%d, remove=%d, topK=%d, poll=%d",
                addTime, searchTime, removeTime, topKTime, pollTime);
    }

    private static String testJavaUtilPQ(Ticket[] tickets) {
        PriorityQueue<Ticket> pq = new PriorityQueue<>(TicketOrder.INSTANCE);

        long t1 = System.nanoTime();
        for (Ticket t : tickets) pq.add(t);
        long addTime = (System.nanoTime() - t1) / 1_000_000;

        // В PriorityQueue findByCardNumber нет — используем contains (поиск по объекту)
        long t2 = System.nanoTime();
        for (int i = 0; i < 1000; i++) pq.contains(tickets[i]);
        long searchTime = (System.nanoTime() - t2) / 1_000_000;

        // remove(Object) в PQ — это O(n)
        long t3 = System.nanoTime();
        for (int i = 0; i < 1000; i++) pq.remove(tickets[i]);
        long removeTime = (System.nanoTime() - t3) / 1_000_000;

        long t4 = System.nanoTime();
        // topK в PQ нет — просто извлечём 100
        PriorityQueue<Ticket> copy = new PriorityQueue<>(pq);
        for (int i = 0; i < 100 && !copy.isEmpty(); i++) copy.poll();
        long topKTime = (System.nanoTime() - t4) / 1_000_000;

        long t5 = System.nanoTime();
        while (!pq.isEmpty()) pq.poll();
        long pollTime = (System.nanoTime() - t5) / 1_000_000;

        return String.format("add=%d, search=%d, remove=%d, topK=%d, poll=%d",
                addTime, searchTime, removeTime, topKTime, pollTime);
    }
}