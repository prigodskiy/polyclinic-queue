package ru.polyclinic.queue.io;

import java.io.*;
import java.util.List;
import ru.polyclinic.queue.model.Ticket;

public class BinaryDataSerializer implements DataSerializer {

    // Магическое число и версия для лабы 4 (миграции)
    private static final int MAGIC = 0x504F4C59; // "POLY"
    private static final int VERSION = 1;

    @Override
    public void save(File file, List<Ticket> tickets) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);

            try (ObjectOutputStream objOut = new ObjectOutputStream(out)) {
                objOut.writeObject(tickets);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Ticket> load(File file) throws Exception {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Неверный формат файла (Magic number mismatch)");
            }
            int version = in.readInt();
            if (version > VERSION) {
                throw new IOException("Файл создан в более новой версии программы. Обновите ПО.");
            }

            try (ObjectInputStream objIn = new ObjectInputStream(in)) {
                return (List<Ticket>) objIn.readObject();
            }
        }
    }

    @Override
    public String getFormatName() { return "Binary"; }

    @Override
    public String toString() {
        return getFormatName();
    }
}