package ru.polyclinic.queue.service;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import ru.polyclinic.queue.model.HomeVisitTicket;
import ru.polyclinic.queue.model.Ticket;

/**
 * Генератор случайных талонов для тестирования и демонстрации.
 *
 * <p>Формат данных соответствует demo_tickets.csv:
 * <ul>
 *   <li>ФИО — полное (Иванов Иван Иванович)</li>
 *   <li>Номер карты — 10 цифр, глобально уникальный между вызовами</li>
 *   <li>~20% талонов — на дому</li>
 * </ul>
 *
 * <p><b>Важно:</b> номера карт выдаются из статического {@link AtomicLong},
 * а не из локального random-base. Раньше при count близком к 100k два
 * последовательных вызова генератора гарантированно перекрывались по
 * диапазону карт (bound = 9e9/count ≈ 90k, а карт 100k) — второй прогон
 * давал почти стопроцентные дубликаты.
 */
public final class TicketGenerator {

    /**
     * Глобальный счётчик номеров карт. Стартует в 10-значном диапазоне
     * (1_000_000_000+), инкрементируется на count за вызов. Гарантирует,
     * что два вызова generate() не пересекаются даже частично.
     */
    private static final AtomicLong CARD_COUNTER = new AtomicLong(1_000_000_000L);

    private static final String[] SURNAMES_MALE = {
            "Иванов", "Петров", "Сидоров", "Козлов", "Новиков",
            "Морозов", "Волков", "Соловьёв", "Васильев", "Зайцев",
            "Павлов", "Семёнов", "Голубев", "Виноградов", "Богданов",
            "Воробьёв", "Фёдоров", "Михайлов", "Беляев", "Тарасов",
            "Белов", "Комаров", "Орлов", "Киселёв", "Макаров",
            "Андреев", "Ковалёв", "Ильин", "Гусев", "Титов",
            "Смирнов", "Попов"
    };

    private static final String[] SURNAMES_FEMALE = {
            "Иванова", "Петрова", "Сидорова", "Козлова", "Новикова",
            "Морозова", "Волкова", "Соловьёва", "Васильева", "Зайцева",
            "Павлова", "Семёнова", "Голубева", "Виноградова", "Богданова",
            "Воробьёва", "Фёдорова", "Михайлова", "Беляева", "Тарасова",
            "Белова", "Комарова", "Орлова", "Киселёва", "Макарова",
            "Андреева", "Ковалёва", "Ильина", "Гусева", "Титова",
            "Смирнова", "Попова"
    };

    private static final String[] NAMES_MALE = {
            "Иван", "Пётр", "Сергей", "Андрей", "Михаил",
            "Николай", "Владимир", "Александр", "Дмитрий", "Алексей",
            "Артём", "Максим", "Егор", "Павел", "Роман",
            "Юрий", "Олег", "Игорь", "Виктор", "Константин"
    };

    private static final String[] NAMES_FEMALE = {
            "Анна", "Мария", "Елена", "Ольга", "Наталья",
            "Ирина", "Светлана", "Татьяна", "Юлия", "Екатерина",
            "Дарья", "Виктория", "Анастасия", "Ксения", "Полина",
            "Софья", "Вера", "Надежда", "Любовь", "Галина"
    };

    private static final String[] PATRONYMICS_MALE = {
            "Иванович", "Петрович", "Сергеевич", "Андреевич", "Михайлович",
            "Николаевич", "Владимирович", "Александрович", "Дмитриевич", "Алексеевич",
            "Артёмович", "Максимович", "Егорович", "Павлович", "Романович",
            "Юрьевич", "Олегович", "Игоревич", "Викторович", "Константинович"
    };

    private static final String[] PATRONYMICS_FEMALE = {
            "Ивановна", "Петровна", "Сергеевна", "Андреевна", "Михайловна",
            "Николаевна", "Владимировна", "Александровна", "Дмитриевна", "Алексеевна",
            "Артёмовна", "Максимовна", "Егоровна", "Павловна", "Романовна",
            "Юрьевна", "Олеговна", "Игоревна", "Викторовна", "Константиновна"
    };

    private static final String[] OFFICES = {
            "101", "102", "103", "104", "105",
            "201", "202", "203", "204", "205",
            "301", "302", "303", "310", "311",
            "401", "402", "405", "410", "415",
            "104а"  // кириллическая 'а' — так в демо-данных
    };

    private static final String[] STREETS = {
            "ул. Ленина", "пр. Мира", "ул. Садовая", "ул. Пушкина",
            "пер. Тихий", "ул. Гагарина", "пр. Победы", "ул. Советская",
            "бул. Цветной", "ул. Набережная", "ул. Центральная", "ул. Лесная"
    };

    private static final double HOME_VISIT_PROBABILITY = 0.2;
    private static final double FEMALE_PROBABILITY = 0.5;

    private final Random random;

    public TicketGenerator() {
        this.random = new Random();
    }

    public TicketGenerator(long seed) {
        this.random = new Random(seed);
    }

    public Ticket[] generate(int count) {
        return generate(count, null);
    }

    /**
     * Генерирует массив талонов с колбэком прогресса.
     *
     * <p>Номера карт выдаются из глобального {@link #CARD_COUNTER}: каждый
     * вызов резервирует непрерывный блок [start, start+count). Пересечений
     * между вызовами нет, даже если один и тот же экземпляр используется
     * многократно или создаются разные экземпляры.
     *
     * @param count    количество талонов (≥ 0)
     * @param progress колбэк (готово, всего); может быть null
     */
    public Ticket[] generate(int count, BiConsumer<Integer, Integer> progress) {
        if (count < 0) {
            throw new IllegalArgumentException("Count must not be negative");
        }
        if (count == 0) {
            return new Ticket[0];
        }

        Ticket[] result = new Ticket[count];

        // Резервируем непрерывный блок карт — getAndAdd потокобезопасен,
        // два параллельных generate() получат непересекающиеся диапазоны.
        long startCard = CARD_COUNTER.getAndAdd(count);

        for (int i = 0; i < count; i++) {
            if (Thread.currentThread().isInterrupted()) {
                // Откатываем счётчик: карты из этого блока не будут использованы,
                // но если не вернуть — следующая генерация начнётся дальше, что
                // тоже корректно, просто диапазон "съеден". Для лабораторной
                // проще не возвращать (иначе гонка), а просто выбросить.
                throw new RuntimeException("Генерация отменена пользователем");
            }

            String cardNumber = String.valueOf(startCard + i);
            result[i] = generateOne(cardNumber);

            if (progress != null && (i % 100 == 0 || i == count - 1)) {
                progress.accept(i + 1, count);
            }
        }
        return result;
    }

    private Ticket generateOne(String cardNumber) {
        boolean isFemale = random.nextDouble() < FEMALE_PROBABILITY;

        String surname = isFemale
                ? SURNAMES_FEMALE[random.nextInt(SURNAMES_FEMALE.length)]
                : SURNAMES_MALE[random.nextInt(SURNAMES_MALE.length)];
        String name = isFemale
                ? NAMES_FEMALE[random.nextInt(NAMES_FEMALE.length)]
                : NAMES_MALE[random.nextInt(NAMES_MALE.length)];
        String patronymic = isFemale
                ? PATRONYMICS_FEMALE[random.nextInt(PATRONYMICS_FEMALE.length)]
                : PATRONYMICS_MALE[random.nextInt(PATRONYMICS_MALE.length)];

        String fullName = surname + " " + name + " " + patronymic;
        String office = OFFICES[random.nextInt(OFFICES.length)];
        int urgency = random.nextInt(4);

        if (random.nextDouble() < HOME_VISIT_PROBABILITY) {
            String address = randomAddress();
            return new HomeVisitTicket(cardNumber, fullName, office, urgency, address);
        }

        return new Ticket(cardNumber, fullName, office, urgency);
    }

    private String randomAddress() {
        String street = STREETS[random.nextInt(STREETS.length)];
        int house = 1 + random.nextInt(50);
        int apartment = 1 + random.nextInt(200);
        return "%s, д. %d, кв. %d".formatted(street, house, apartment);
    }
}