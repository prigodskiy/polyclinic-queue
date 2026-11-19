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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SerializerTest {

    @TempDir Path tempDir;

    private List<Ticket> createTestData() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 23, 10, 0);
        return List.of(
                new Ticket(1L, "1000", "Иван Иванович", "101", 2, "79991112233", "1234567890123456", time),
                new HomeVisitTicket(2L, "2000", "Пётр Петрович", "102", 3, "79992223344", "2345678901234567", time, "ул. Ленина, д. 1"),
                new ClosedTicket(3L, "3000", "Анна Сергеевна", "103", 0, "79993334455", "3456789012345678", time, null)
        );
    }

    private void testSerializer(DataSerializer serializer, String filename) throws Exception {
        File file = tempDir.resolve(filename).toFile();
        List<Ticket> original = createTestData();

        serializer.save(file, original);
        assertTrue(file.exists(), "файл должен быть создан");

        List<Ticket> loaded = serializer.load(file).tickets();
        assertEquals(3, loaded.size());
        assertEquals("Иван Иванович", loaded.get(0).getFullName());
        assertEquals("1234567890123456", loaded.get(0).getPolicyNumber());
        assertInstanceOf(HomeVisitTicket.class, loaded.get(1));
        assertEquals("ул. Ленина, д. 1", ((HomeVisitTicket) loaded.get(1)).getAddress());
        assertInstanceOf(ClosedTicket.class, loaded.get(2));
    }

    @Test void testJsonSerialization()   throws Exception { testSerializer(new JsonDataSerializer(),   "test.json"); }
    @Test void testBinarySerialization() throws Exception { testSerializer(new BinaryDataSerializer(), "test.bin");  }
    @Test void testCsvSerialization()    throws Exception { testSerializer(new CsvDataSerializer(),    "test.csv");  }

    @Test
    void saveEmptyListRoundtripJson() throws Exception {
        File f = tempDir.resolve("empty.json").toFile();
        JsonDataSerializer s = new JsonDataSerializer();
        s.save(f, List.of());
        assertTrue(s.load(f).tickets().isEmpty());
    }

    @Test
    void saveEmptyListRoundtripBinary() throws Exception {
        File f = tempDir.resolve("empty.bin").toFile();
        BinaryDataSerializer s = new BinaryDataSerializer();
        s.save(f, List.of());
        assertTrue(s.load(f).tickets().isEmpty());
    }

    @Test
    void loadWrongMagicThrows() throws Exception {
        File f = tempDir.resolve("bad-magic.bin").toFile();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
            out.writeInt(0xDEADBEEF); out.writeInt(1);
        }
        assertThrows(IOException.class, () -> new BinaryDataSerializer().load(f));
    }

    @Test
    void loadFutureVersionThrows() throws Exception {
        File f = tempDir.resolve("future-version.bin").toFile();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
            out.writeInt(0x504F4C59); out.writeInt(999);
        }
        assertThrows(IOException.class, () -> new BinaryDataSerializer().load(f));
    }

    @Test
    void csvHandlesSemicolonInAddress() throws Exception {
        File f = tempDir.resolve("semicolon.csv").toFile();
        HomeVisitTicket tricky = new HomeVisitTicket(1L, "1000", "Иван Иванович", "101", 2, "79991112233", "1234567890123456", LocalDateTime.now(), "ул. Ленина; д. 1");
        CsvDataSerializer s = new CsvDataSerializer();
        s.save(f, List.of(tricky));
        List<Ticket> loaded = s.load(f).tickets();
        assertEquals("ул. Ленина; д. 1", ((HomeVisitTicket) loaded.get(0)).getAddress());
    }

    @Test
    void jsonMigratesV1ToV3() throws Exception {
        File f = tempDir.resolve("v1.json").toFile();
        Files.writeString(f.toPath(), "[{\"type\":\"REGULAR\",\"id\":1,\"cardNumber\":\"1000\",\"fullName\":\"Иван Иванович\",\"office\":\"101\",\"urgency\":2,\"takenAt\":\"2026-10-23T10:00:00\"}]");
        SerializerResult result = new JsonDataSerializer().load(f);
        assertEquals(1, result.version());
        assertEquals("Неизвестен", result.tickets().get(0).getPhoneNumber());
        assertEquals("Нет полиса", result.tickets().get(0).getPolicyNumber());
    }

    @Test
    void jsonMigratesV2ToV3() throws Exception {
        File f = tempDir.resolve("v2.json").toFile();
        Files.writeString(f.toPath(), "{\"schema_version\":2,\"tickets\":[{\"type\":\"REGULAR\",\"id\":1,\"cardNumber\":\"1000\",\"fullName\":\"Иван Иванович\",\"office\":\"101\",\"urgency\":2,\"phoneNumber\":\"79991112233\",\"takenAt\":\"2026-10-23T10:00:00\"}]}");
        SerializerResult result = new JsonDataSerializer().load(f);
        assertEquals(2, result.version());
        assertEquals("79991112233", result.tickets().get(0).getPhoneNumber());
        assertEquals("Нет полиса", result.tickets().get(0).getPolicyNumber());
    }

    @Test
    void binaryMigratesV1ToV3() throws Exception {
        File f = tempDir.resolve("v1.bin").toFile();
        BinaryDataSerializer bin = new BinaryDataSerializer();
        bin.saveAsV1(f, createTestData());
        SerializerResult result = bin.load(f);
        assertEquals(1, result.version());
        assertEquals("Неизвестен", result.tickets().get(0).getPhoneNumber());
        assertEquals("Нет полиса", result.tickets().get(0).getPolicyNumber());
    }

    @Test
    void binaryMigratesV2ToV3() throws Exception {
        File f = tempDir.resolve("v2.bin").toFile();
        BinaryDataSerializer bin = new BinaryDataSerializer();
        bin.saveAsV2(f, createTestData());
        SerializerResult result = bin.load(f);
        assertEquals(2, result.version());
        assertEquals("79991112233", result.tickets().get(0).getPhoneNumber());
        assertEquals("Нет полиса", result.tickets().get(0).getPolicyNumber());
    }

    @Test
    void csvMigratesV1ToV3() throws Exception {
        File f = tempDir.resolve("v1.csv").toFile();
        Files.writeString(f.toPath(), "id;type;card_number;patient_name;office;urgency;taken_at;address\n101;REGULAR;2001000001;Петров Николай Иванович;101;3;2026-10-23T08:05:00;");
        SerializerResult result = new CsvDataSerializer().load(f);
        assertEquals(1, result.version());
        assertEquals("Неизвестен", result.tickets().get(0).getPhoneNumber());
        assertEquals("Нет полиса", result.tickets().get(0).getPolicyNumber());
    }

    @Test
    void csvMigratesV2ToV3() throws Exception {
        File f = tempDir.resolve("v2.csv").toFile();
        Files.writeString(f.toPath(), "id;type;card_number;patient_name;office;urgency;phone_number;taken_at;address\n101;REGULAR;2001000001;Петров Николай Иванович;101;3;79991112233;2026-10-23T08:05:00;");
        SerializerResult result = new CsvDataSerializer().load(f);
        assertEquals(2, result.version());
        assertEquals("79991112233", result.tickets().get(0).getPhoneNumber());
        assertEquals("Нет полиса", result.tickets().get(0).getPolicyNumber());
    }
}