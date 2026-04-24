package me.zed_0xff.zombie_buddy;

import static me.zed_0xff.zombie_buddy.SteamWorkshop.SteamID64;
import static me.zed_0xff.zombie_buddy.SteamWorkshop.WorkshopItemID;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class JarBatchApprovalProtocolTest {

    @TempDir
    Path tempDir;

    @Test
    void parseRequestFixture_loadsAllFields() throws IOException {
        String json = loadFixture("jar_batch_request_sample.json");
        Path reqPath = tempDir.resolve("request.json");
        Files.writeString(reqPath, json);

        List<JarBatchApprovalProtocol.Entry> entries = JarBatchApprovalProtocol.readRequest(reqPath);

        assertEquals(2, entries.size());

        // Check first entry
        JarBatchApprovalProtocol.Entry e1 = entries.get(0);
        assertEquals("TestMod", e1.modKey);
        assertEquals("TestMod", e1.modId);
        assertEquals(3709229404L, e1.workshopItemId.value());
        assertEquals("/path/to/mod.jar", e1.jarAbsolutePath);
        assertEquals("abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234", e1.sha256);
        assertEquals("2026-04-01", e1.modifiedHuman);
        assertEquals("yes", e1.priorHint);
        assertEquals("Test Mod Display Name", e1.modDisplayName);
        assertEquals("yes", e1.zbsValid);
        assertEquals(76561198043849998L, e1.zbsSteamId.value());
        assertEquals("no", e1.steamBanStatus);

        // Check second entry (no workshopItemId, no zbsSteamId)
        JarBatchApprovalProtocol.Entry e2 = entries.get(1);
        assertEquals("LocalMod", e2.modId);
        assertNull(e2.workshopItemId);
        assertNull(e2.zbsSteamId);
        assertEquals("unsigned", e2.zbsValid);
    }

    @Test
    void parseResponseFixture_loadsAllFields() throws IOException {
        String json = loadFixture("jar_batch_response_sample.json");
        Path respPath = tempDir.resolve("response.json");
        Files.writeString(respPath, json);

        List<JarBatchApprovalProtocol.OutLine> lines = JarBatchApprovalProtocol.readResponse(respPath);

        assertNotNull(lines);
        assertEquals(2, lines.size());

        // Check first line
        JarBatchApprovalProtocol.OutLine l1 = lines.get(0);
        assertEquals("TestMod", l1.modId);
        assertEquals(3709229404L, l1.workshopItemId.value());
        assertEquals("abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234", l1.sha256);
        assertEquals("ALLOW_PERSIST", l1.token);
        assertEquals(76561198043849998L, l1.trustedAuthorSteamId.value());

        // Check second line (no workshopItemId, no trustedAuthorSteamId)
        JarBatchApprovalProtocol.OutLine l2 = lines.get(1);
        assertEquals("LocalMod", l2.modId);
        assertNull(l2.workshopItemId);
        assertEquals("DENY_SESSION", l2.token);
        assertNull(l2.trustedAuthorSteamId);
    }

    @Test
    void requestRoundTrip_preservesData() throws IOException {
        List<JarBatchApprovalProtocol.Entry> original = new ArrayList<>();
        original.add(new JarBatchApprovalProtocol.Entry(
            "RoundTripMod",
            "RoundTripMod",
            new WorkshopItemID(9876543210L),
            "/path/to/roundtrip.jar",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "2026-04-24",
            "no",
            "Round Trip Mod",
            "yes",
            new SteamID64(76561198099999999L),
            "",
            "no",
            ""
        ));

        Path reqPath = tempDir.resolve("roundtrip_request.json");
        JarBatchApprovalProtocol.writeRequest(reqPath, original);
        List<JarBatchApprovalProtocol.Entry> parsed = JarBatchApprovalProtocol.readRequest(reqPath);

        assertEquals(1, parsed.size());
        JarBatchApprovalProtocol.Entry e = parsed.get(0);
        assertEquals("RoundTripMod", e.modKey);
        assertEquals(9876543210L, e.workshopItemId.value());
        assertEquals(76561198099999999L, e.zbsSteamId.value());
    }

    @Test
    void responseRoundTrip_preservesData() throws IOException {
        List<JarBatchApprovalProtocol.OutLine> original = new ArrayList<>();
        original.add(new JarBatchApprovalProtocol.OutLine(
            "RoundTripMod",
            new WorkshopItemID(9876543210L),
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "ALLOW_PERSIST",
            new SteamID64(76561198099999999L)
        ));

        Path respPath = tempDir.resolve("roundtrip_response.json");
        JarBatchApprovalProtocol.writeResponse(respPath, original);
        List<JarBatchApprovalProtocol.OutLine> parsed = JarBatchApprovalProtocol.readResponse(respPath);

        assertNotNull(parsed);
        assertEquals(1, parsed.size());
        JarBatchApprovalProtocol.OutLine l = parsed.get(0);
        assertEquals("RoundTripMod", l.modId);
        assertEquals(9876543210L, l.workshopItemId.value());
        assertEquals("ALLOW_PERSIST", l.token);
        assertEquals(76561198099999999L, l.trustedAuthorSteamId.value());
    }

    @Test
    void isValidToken_acceptsValidTokens() {
        assertTrue(JarBatchApprovalProtocol.isValidToken("ALLOW_PERSIST"));
        assertTrue(JarBatchApprovalProtocol.isValidToken("ALLOW_SESSION"));
        assertTrue(JarBatchApprovalProtocol.isValidToken("DENY_PERSIST"));
        assertTrue(JarBatchApprovalProtocol.isValidToken("DENY_SESSION"));
    }

    @Test
    void isValidToken_rejectsInvalidTokens() {
        assertFalse(JarBatchApprovalProtocol.isValidToken("INVALID"));
        assertFalse(JarBatchApprovalProtocol.isValidToken(""));
        assertFalse(JarBatchApprovalProtocol.isValidToken(null));
        assertFalse(JarBatchApprovalProtocol.isValidToken("allow_persist"));
    }

    @Test
    void readResponse_rejectsInvalidToken() throws IOException {
        String json = """
            {
              "header": "ZB_BATCH_V6_OUT",
              "lines": [
                {
                  "modId": "BadMod",
                  "sha256": "hash",
                  "token": "INVALID_TOKEN"
                }
              ]
            }
            """;
        Path respPath = tempDir.resolve("invalid_response.json");
        Files.writeString(respPath, json);

        List<JarBatchApprovalProtocol.OutLine> result = JarBatchApprovalProtocol.readResponse(respPath);
        assertNull(result);
    }

    @Test
    void readResponse_rejectsBadHeader() throws IOException {
        String json = """
            {
              "header": "WRONG_HEADER",
              "lines": []
            }
            """;
        Path respPath = tempDir.resolve("bad_header_response.json");
        Files.writeString(respPath, json);

        List<JarBatchApprovalProtocol.OutLine> result = JarBatchApprovalProtocol.readResponse(respPath);
        assertNull(result);
    }

    @Test
    void readRequest_rejectsBadHeader() throws IOException {
        String json = """
            {
              "header": "WRONG_HEADER",
              "entries": []
            }
            """;
        Path reqPath = tempDir.resolve("bad_header_request.json");
        Files.writeString(reqPath, json);

        assertThrows(IOException.class, () -> JarBatchApprovalProtocol.readRequest(reqPath));
    }

    @Test
    void serialize_writesNumbersNotStrings() throws IOException {
        List<JarBatchApprovalProtocol.Entry> entries = new ArrayList<>();
        entries.add(new JarBatchApprovalProtocol.Entry(
            "NumericTest", "NumericTest",
            new WorkshopItemID(1234567890L),
            "/path", "hash", "date", "", "",
            "yes", new SteamID64(76561198000000000L), "",
            "no", ""
        ));

        Path reqPath = tempDir.resolve("numeric_request.json");
        JarBatchApprovalProtocol.writeRequest(reqPath, entries);
        String json = Files.readString(reqPath);

        assertTrue(json.contains("\"workshopItemId\": 1234567890"),
            "workshopItemId should be numeric: " + json);
        assertTrue(json.contains("\"zbsSteamId\": 76561198000000000"),
            "zbsSteamId should be numeric: " + json);
    }

    private static String loadFixture(String name) throws IOException {
        try (InputStream is = JarBatchApprovalProtocolTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                Path p = Path.of("test/fixtures", name);
                if (Files.exists(p)) {
                    return Files.readString(p, StandardCharsets.UTF_8);
                }
                throw new IOException("Fixture not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
