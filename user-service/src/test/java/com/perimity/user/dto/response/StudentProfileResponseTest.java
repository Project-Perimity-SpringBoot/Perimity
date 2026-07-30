package com.perimity.user.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.perimity.user.entity.StudentProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Day 5 gate: confirm the government id masking works end to end.
 *
 * This is worth a test rather than an eyeball because the failure is silent and
 * total. If mask() ever returns the input unchanged, nothing breaks, no error
 * appears, and twelve real digits go out in every profile response and every
 * page of the student directory. Nobody notices until someone opens the browser
 * network tab.
 */
class StudentProfileResponseTest {

    @Test
    @DisplayName("a twelve digit government id is returned with only the last four visible")
    void masksGovId() {
        StudentProfileResponse response = StudentProfileResponse.from(profileWithGovId("123456789012"));

        assertThat(response.govIdMasked()).isEqualTo("********9012");
        assertThat(response.govIdPresent()).isTrue();
    }

    @Test
    @DisplayName("the full government id never appears anywhere in the response")
    void neverLeaksTheFullValue() {
        StudentProfileResponse response = StudentProfileResponse.from(profileWithGovId("123456789012"));

        // toString() is what ends up in a log line, an error report or a
        // debugger. If the record ever gains a field holding the raw value,
        // this is what catches it.
        assertThat(response.toString()).doesNotContain("123456789012");
    }

    @Test
    @DisplayName("no government id gives null, not a row of stars")
    void handlesMissingGovId() {
        StudentProfileResponse response = StudentProfileResponse.from(profileWithGovId(null));

        assertThat(response.govIdMasked()).isNull();
        assertThat(response.govIdPresent()).isFalse();
    }

    @Test
    @DisplayName("a blank government id counts as absent")
    void handlesBlankGovId() {
        StudentProfileResponse response = StudentProfileResponse.from(profileWithGovId("   "));

        assertThat(response.govIdMasked()).isNull();
        assertThat(response.govIdPresent()).isFalse();
    }

    @Test
    @DisplayName("a value of four characters or fewer is masked completely")
    void masksShortValuesEntirely() {
        // The entity's pattern only allows twelve digits, so this cannot arrive
        // through the API today. It is tested because mask() would otherwise
        // reveal the whole value if the rule ever loosened, and the substring
        // arithmetic is exactly where an off-by-one hides.
        assertThat(StudentProfileResponse.from(profileWithGovId("1234")).govIdMasked())
                .isEqualTo("****");
    }

    private StudentProfile profileWithGovId(String govId) {
        return StudentProfile.builder()
                .id(1L)
                .userId(108L)
                .campusId(1L)
                .rollNo("2026/CS/0141")
                .govId(govId)
                .build();
    }
}
