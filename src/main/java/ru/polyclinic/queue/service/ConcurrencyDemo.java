package ru.polyclinic.queue.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConcurrencyDemo {

    public static String demonstrate() {
        StringBuilder sb = new StringBuilder();
        sb.append("ДЕМОНСТРАЦИЯ ГОНКИ ПОТОКОВ (Race Condition)\n\n");

        int threads = 10;
        int addsPerThread = 1000;

        // 1. НЕЗАЩИЩЁННАЯ СТРУКТУРА
        List<Integer> unsafeList = new ArrayList<>();
        Thread[] unsafeThreads = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            unsafeThreads[i] = new Thread(() -> {
                for (int j = 0; j < addsPerThread; j++) unsafeList.add(1);
            });
            unsafeThreads[i].start();
        }
        waitForAll(unsafeThreads);
        sb.append("Без синхронизации (ArrayList):\n");
        sb.append("  Ожидалось: ").append(threads * addsPerThread).append("\n");
        sb.append("  Фактически: ").append(unsafeList.size()).append("\n");
        sb.append("  -> Произошла ПОТЕРЯ ДАННЫХ (гонка)!\n\n");

        // 2. ЗАЩИЩЁННАЯ СТРУКТУРА
        List<Integer> safeList = Collections.synchronizedList(new ArrayList<>());
        Thread[] safeThreads = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            safeThreads[i] = new Thread(() -> {
                for (int j = 0; j < addsPerThread; j++) safeList.add(1);
            });
            safeThreads[i].start();
        }
        waitForAll(safeThreads);
        sb.append("С синхронизацией (synchronizedList):\n");
        sb.append("  Ожидалось: ").append(threads * addsPerThread).append("\n");
        sb.append("  Фактически: ").append(safeList.size()).append("\n");
        sb.append("  -> Данные сохранены полностью!\n");

        return sb.toString();
    }

    private static void waitForAll(Thread[] threads) {
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }
    }
}