package ru.polyclinic.queue.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Базовый талон электронной очереди.
 *
 * <p>Конкретный класс (не абстрактный): «Обычный» талон создаётся напрямую —
 * этого требует методичка. Срочность и время взятия лежат в базовом классе:
 * компаратор кучи читает их без downcast'ов.
 *
 * <p>Объекты неизменяемы (effective-immutable): «изменение» талона —
 * создание копии через {@link #withChanges}, что защищает кучу от порчи
 * приоритета «из-под себя».
 */
public class Ticket implements Editable {

    private static final java.util.concurrent.atomic.AtomicLong ID_SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong(1);

    /** Стабильный идентификатор: tie-break компаратора кучи и ключ removeByKey/changePriority. */
    private final long id;
    private final String cardNumber;
    private final String fullName;
    private final String office;
    private final int urgency;
    private final LocalDateTime takenAt;

    /** Публичный конструктор: время взятия — «сейчас», id — автогенерация. */
    public Ticket(String cardNumber, String fullName, String office, int urgency) {
        this(nextId(), cardNumber, fullName, office, urgency, LocalDateTime.now());
    }

    /** Публичный заводской конструктор: для демо-данных, десериализации и тестов. */
    public Ticket(long id, String cardNumber, String fullName, String office,
                  int urgency, LocalDateTime takenAt) {
        if (urgency < 0 || urgency > 3) {
            throw new IllegalArgumentException("Urgency must be in range [0, 3]");
        }
        this.id = id;
        this.cardNumber = normalize(cardNumber, "cardNumber");
        this.fullName = normalize(fullName, "fullName");
        this.office = normalize(office, "office");
        this.urgency = urgency;
        this.takenAt = Objects.requireNonNull(takenAt, "takenAt must not be null");
    }

    protected static long nextId() {
        return ID_SEQUENCE.getAndIncrement();
    }

    /**
     * Публичный доступ к генератору id — используется диалогом GUI при создании
     * нового талона (для правки id берётся из существующей записи).
     */
    public static long nextIdForUi() {
        return nextId();
    }

    public long getId() { return id; }

    /** Тип талона — переопределяется в подклассах (без instanceof-цепочек в GUI/IO). */
    public TicketType getType() {
        return TicketType.REGULAR;
    }

    public String getCardNumber() { return cardNumber; }
    public String getFullName() { return fullName; }
    public String getOffice() { return office; }
    public int getUrgency() { return urgency; }
    public LocalDateTime getTakenAt() { return takenAt; }

    /** Копия талона с изменёнными полями; {@code null} аргумент = оставить как было. id сохраняется. */
    public Ticket withChanges(String newFullName, String newOffice, Integer newUrgency) {
        return new Ticket(
                id,
                cardNumber,
                newFullName != null ? newFullName : fullName,
                newOffice != null ? newOffice : office,
                newUrgency != null ? newUrgency : urgency,
                takenAt);
    }

    /** Только изменение срочности — используется очередью в changePriority. */
    public Ticket withUrgency(int newUrgency) {
        return withChanges(null, null, newUrgency);
    }

    static String normalize(String value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null").strip();
    }

    @Override
    public List<String> validate() {
        return TicketValidator.validate(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return id == ticket.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "%s{card='%s', patient='%s', office='%s', urgency=%d, time=%s}".formatted(
                getType(), cardNumber, fullName, office, urgency, takenAt);
    }
}