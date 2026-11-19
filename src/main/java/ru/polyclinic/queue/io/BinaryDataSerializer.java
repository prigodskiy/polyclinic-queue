package ru.polyclinic.queue.io;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import ru.polyclinic.queue.model.*;

public class BinaryDataSerializer implements DataSerializer {
    private static final int MAGIC = 0x504F4C59;
    private static final int CURRENT_VERSION = 3;

    @Override
    public void save(File file, List<Ticket> tickets) throws Exception {
        writeVersion(file, CURRENT_VERSION, tickets);
    }

    public void saveAsV1(File file, List<Ticket> tickets) throws Exception {
        writeVersion(file, 1, tickets);
    }

    public void saveAsV2(File file, List<Ticket> tickets) throws Exception {
        writeVersion(file, 2, tickets);
    }

    private void writeVersion(File file, int version, List<Ticket> tickets) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(version);
            out.writeInt(tickets.size());

            for (Ticket t : tickets) {
                out.writeUTF(t.getType().name());
                out.writeLong(t.getId());
                out.writeUTF(t.getCardNumber());
                out.writeUTF(t.getFullName());
                out.writeUTF(t.getOffice());
                out.writeInt(t.getUrgency());
                out.writeUTF(t.getTakenAt().toString());

                String address = extractAddress(t);
                out.writeBoolean(address != null);
                if (address != null) {
                    out.writeUTF(address);
                }

                if (version >= 2) {
                    out.writeUTF(t.getPhoneNumber());
                }
                if (version >= 3) {
                    out.writeUTF(t.getPolicyNumber());
                }
            }
        }
    }

    @Override
    public SerializerResult load(File file) throws Exception {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Неверный формат файла (не совпадает сигнатура)");
            }

            int version = in.readInt();
            if (version < 1) {
                throw new IOException("Неподдерживаемая версия формата: " + version);
            }
            if (version > CURRENT_VERSION) {
                throw new IOException("Файл создан в более новой версии программы (" + version + ")");
            }

            int count = in.readInt();
            if (count < 0) {
                throw new IOException("Повреждённый файл: отрицательное количество записей");
            }

            List<Ticket> tickets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                TicketType type = TicketType.valueOf(in.readUTF());
                long id = in.readLong();
                String cardNumber = in.readUTF();
                String fullName = in.readUTF();
                String office = in.readUTF();
                int urgency = in.readInt();
                LocalDateTime takenAt = LocalDateTime.parse(in.readUTF());

                boolean hasAddress = in.readBoolean();
                String address = hasAddress ? in.readUTF() : null;

                String phoneNumber = (version >= 2) ? in.readUTF() : "Неизвестен";
                String policyNumber = (version >= 3) ? in.readUTF() : "Нет полиса";

                Ticket ticket = switch (type) {
                    case REGULAR -> new Ticket(id, cardNumber, fullName, office, urgency, phoneNumber, policyNumber, takenAt);
                    case HOME_VISIT -> new HomeVisitTicket(id, cardNumber, fullName, office, urgency, phoneNumber, policyNumber, takenAt, address);
                    case CLOSED -> new ClosedTicket(id, cardNumber, fullName, office, urgency, phoneNumber, policyNumber, takenAt, address);
                };

                List<String> errors = TicketValidator.validate(ticket);
                if (!errors.isEmpty()) {
                    throw new IOException("Ошибка валидации записи #" + (i + 1) + ": " + String.join("; ", errors));
                }

                tickets.add(ticket);
            }

            return new SerializerResult(tickets, version);
        } catch (EOFException e) {
            throw new IOException("Файл повреждён или оборван (неожиданный конец файла)", e);
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            throw new IOException("Ошибка структуры или данных в файле при миграции: " + e.getMessage(), e);
        }
    }

    private String extractAddress(Ticket t) {
        if (t instanceof HomeVisitTicket hvt) return hvt.getAddress();
        if (t instanceof ClosedTicket ct) return ct.getAddressOrNull();
        return null;
    }

    @Override
    public String getFormatName() { return "Binary"; }
    @Override
    public String toString() { return getFormatName(); }
}