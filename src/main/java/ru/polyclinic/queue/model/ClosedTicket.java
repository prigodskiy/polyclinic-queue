package ru.polyclinic.queue.model;

import java.time.LocalDateTime;

/**
 * Закрытый талон — read-only архивная запись.
 *
 * <p>Операции «закрыть талон» в задании нет: закрытый талон нельзя создать
 * или изменить из GUI — он появляется только при загрузке файла (строка типа
 * CLOSED). Класс НЕ реализует {@link Editable}: кнопки «Изменить»/«Удалить»
 * блокируются по {@code instanceof Editable}, а {@code QueueService} отказывает
 * такому талону в добавлении. В живую очередь (min-кучу) они не попадают —
 * closed-талон уже обслужен и не участвует в выборке следующего пациента.
 *
 * <p>Адрес — nullable-поле: заполняется, если закрыт был талон на дому,
 * поэтому адрес не теряется и четвёртый класс ClosedHomeVisitTicket не нужен.
 */
public final class ClosedTicket extends Ticket {

    private final String address; // nullable: есть только у закрытых домашних

    public ClosedTicket(long id, String cardNumber, String fullName, String office,
                        int urgency, LocalDateTime takenAt, String address) {
        super(id, cardNumber, fullName, office, urgency, takenAt);
        this.address = (address == null || address.isBlank()) ? null : address.strip();
    }

    @Override
    public TicketType getType() {
        return TicketType.CLOSED;
    }

    /** @return адрес или {@code null}, если закрытый талон был обычным. */
    public String getAddressOrNull() {
        return address;
    }

    public boolean isHomeVisit() {
        return address != null;
    }

    // equals/hashCode — по id (наследуются от Ticket): архивные записи
    // сравниваются как объекты, а не как данные; данные проверяются roundtrip-тестами.

    @Override
    public String toString() {
        return "ClosedTicket{card='%s', patient='%s', office='%s', urgency=%d, address='%s', time=%s}"
                .formatted(getCardNumber(), getFullName(), getOffice(),
                        getUrgency(), address == null ? "" : address, getTakenAt());
    }
}