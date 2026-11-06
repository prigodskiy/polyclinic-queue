package ru.polyclinic.queue.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.polyclinic.queue.model.ClosedTicket;
import ru.polyclinic.queue.model.HomeVisitTicket;
import ru.polyclinic.queue.model.Ticket;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SerializerTest {

    @TempDir
    Path tempDir;

    private List<Ticket> createTestData() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 23, 10, 0);
        return List.of(
                new Ticket(1L, "100", "Иван Иванович", "101", 2, time),
                new HomeVisitTicket(2L, "200", "Пётр Петрович", "102", 3, time, "ул. Ленина, д. 1"),
                new ClosedTicket(3L, "300", "Анна Сергеевна", "103", 0, time, null)
        );
    }

    private void testSerializer(DataSerializer serializer, String filename) throws Exception {
        File file = tempDir.resolve(filename).toFile();
        List<Ticket> original = createTestData();

        serializer.save(file, original);
        assertTrue(file.exists(), "файл должен быть создан");

        List<Ticket> loaded = serializer.load(file);
        assertEquals(3, loaded.size());

        assertInstanceOf(Ticket.class, loaded.get(0));
        assertEquals("Иван Иванович", loaded.get(0).getFullName());
        assertEquals("100", loaded.get(0).getCardNumber());

        assertInstanceOf(HomeVisitTicket.class, loaded.get(1));
        assertEquals("ул. Ленина, д. 1", ((HomeVisitTicket) loaded.get(1)).getAddress());

        assertInstanceOf(ClosedTicket.class, loaded.get(2));
        assertNull(((ClosedTicket) loaded.get(2)).getAddressOrNull());
    }

    @Test void testJsonSerialization()   throws Exception { testSerializer(new JsonDataSerializer(),   "test.json"); }
    @Test void testBinarySerialization() throws Exception { testSerializer(new BinaryDataSerializer(), "test.bin");  }
    @Test void testCsvSerialization()    throws Exception { testSerializer(new CsvDataSerializer(),    "test.csv");  }

    @Test
    void saveEmptyListRoundtripJson() throws Exception {
        File f = tempDir.resolve("empty.json").toFile();
        JsonDataSerializer s = new JsonDataSerializer();
        s.save(f, List.of());
        assertTrue(s.load(f).isEmpty());
    }

    @Test
    void saveEmptyListRoundtripBinary() throws Exception {
        File f = tempDir.resolve("empty.bin").toFile();
        BinaryDataSerializer s = new BinaryDataSerializer();
        s.save(f, List.of());
        assertTrue(s.load(f).isEmpty());
    }

    @Test
    void loadWrongMagicThrows() throws Exception {
        File f = tempDir.resolve("bad-magic.bin").toFile();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
            out.writeInt(0xDEADBEEF);
            out.writeInt(1);
        }
        assertThrows(IOException.class, () -> new BinaryDataSerializer().load(f));
    }

    @Test
    void loadFutureVersionThrows() throws Exception {
        File f = tempDir.resolve("future-version.bin").toFile();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
            out.writeInt(0x504F4C59);
            out.writeInt(999);
        }
        assertThrows(IOException.class, () -> new BinaryDataSerializer().load(f));
    }

    @Test
    void csvHandlesSemicolonInAddress() throws Exception {
        File f = tempDir.resolve("semicolon.csv").toFile();
        LocalDateTime time = LocalDateTime.of(2026, 10, 23, 10, 0);
        HomeVisitTicket tricky = new HomeVisitTicket(
                1L, "100", "Иван Иванович", "101", 2, time,
                "ул. Ленина; д. 1; кв. 5");
        CsvDataSerializer s = new CsvDataSerializer();
        s.save(f, List.of(tricky));
        List<Ticket> loaded = s.load(f);
        assertEquals(1, loaded.size());
        assertEquals("ул. Ленина; д. 1; кв. 5",
                ((HomeVisitTicket) loaded.get(0)).getAddress());
    }
}