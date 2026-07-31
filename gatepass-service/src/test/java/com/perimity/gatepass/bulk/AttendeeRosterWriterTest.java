package com.perimity.gatepass.bulk;

import static org.assertj.core.api.Assertions.assertThat;

import com.perimity.gatepass.entity.GatePass;
import com.perimity.gatepass.entity.enums.PassStatus;
import com.perimity.gatepass.entity.enums.PassType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The attendee roster looks harmless - it is a list of names. It is not.
 * Every holderName here arrived through a bulk spreadsheet upload, and the
 * organiser is about to open the file in Excel.
 */
@DisplayName("AttendeeRosterWriter - the Export CSV button")
class AttendeeRosterWriterTest {

    private final AttendeeRosterWriter writer = new AttendeeRosterWriter();

    private String csv(List<GatePass> passes) {
        return new String(writer.write(passes), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("writes a header and one row per pass")
    void writesRows() {
        String out = csv(List.of(
                pass("Asha Menon", PassStatus.ACTIVE, LocalDate.of(2026, 8, 12)),
                pass("Ravi Iyer", PassStatus.PENDING, LocalDate.of(2026, 8, 12))));

        assertThat(out).contains("Name,Pass ID,Status,Valid From,Valid To,Issued");
        assertThat(out).contains("\"Asha Menon\"");
        assertThat(out).contains("\"Ravi Iyer\"");
        assertThat(out.split("\r\n")).hasSize(3);
    }

    @Test
    @DisplayName("a standing DAILY pass says 'No end date' rather than leaving a blank")
    void nullValidToIsExplained() {
        // A blank cell reads as missing data. "No end date" is the actual fact.
        String out = csv(List.of(pass("Standing Student", PassStatus.ACTIVE, null)));

        assertThat(out).contains("No end date");
    }

    @Test
    @DisplayName("neutralises a formula in a name that came from a spreadsheet")
    void neutralisesFormula() {
        String out = csv(List.of(
                pass("=HYPERLINK(\"http://evil/\",\"x\")", PassStatus.ACTIVE, null)));

        assertThat(out).contains("\"'=HYPERLINK");
    }

    @Test
    @DisplayName("carries a UTF-8 BOM so non-ASCII names survive Excel on Windows")
    void hasBom() {
        assertThat(csv(List.of(pass("\u0905\u0936\u093e", PassStatus.ACTIVE, null))))
                .startsWith("\uFEFF");
    }

    @Test
    @DisplayName("an event with no passes still produces a valid file")
    void emptyRoster() {
        assertThat(csv(List.of()))
                .isEqualTo("\uFEFFName,Pass ID,Status,Valid From,Valid To,Issued\r\n");
    }

    private GatePass pass(String name, PassStatus status, LocalDate validTo) {
        return GatePass.builder()
                .id(1L).holderUserId(5L).holderName(name).campusId(1L)
                .passType(PassType.EVENT).eventId(7L)
                .validFrom(LocalDate.of(2026, 8, 10)).validTo(validTo)
                .status(status).build();
    }
}
