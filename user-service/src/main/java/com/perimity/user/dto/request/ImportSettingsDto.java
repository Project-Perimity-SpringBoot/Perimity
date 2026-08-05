package com.perimity.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PUT /api/user/students/import/settings.
 *
 * ==========================================================================
 * URLs, NOT IDS, AND NOT VALIDATED BY REGEX HERE
 * ==========================================================================
 * Both fields take whatever Google put in the address bar. Extracting the id
 * is the service's job, because a person copying a link should not have to
 * know that the useful part of
 *
 *   https://docs.google.com/spreadsheets/d/1AbC.../edit#gid=0
 *
 * is the bit between /d/ and the next slash.
 *
 * There is deliberately no @Pattern on either. Google has changed these URL
 * shapes more than once, and a regex here would reject a link that works - the
 * worst kind of validation, because the user can see it working in their
 * browser while the form insists it is wrong.
 *
 * The service refuses anything it genuinely cannot read an id out of, with a
 * message naming what to copy instead.
 *
 * campusId is absent: it comes from the token. A faculty member configuring
 * another campus's form is not a thing that should be expressible.
 */
@Schema(description = "The campus intake form and the sheet its responses go to")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportSettingsDto {

    /**
     * The link students open. Whatever Google gave when the form was shared -
     * a /viewform address or a forms.gle short link.
     */
    @Size(max = 500, message = "That link is longer than we can store")
    @Schema(example = "https://docs.google.com/forms/d/e/1FAIpQLS.../viewform")
    private String formUrl;

    /**
     * The RESPONSES SPREADSHEET, not the form.
     *
     * The most common mistake is pasting the form link twice - both are
     * docs.google.com addresses with an id in the same position, so nothing
     * about the shape tells them apart. The service checks the path and says so
     * by name rather than failing later at "could not export".
     */
    @Size(max = 500, message = "That link is longer than we can store")
    @Schema(example = "https://docs.google.com/spreadsheets/d/1AbCdEf.../edit")
    private String responsesSheetUrl;
}
