package com.perimity.qr.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.perimity.qr.dto.BatchProgressResponse;
import com.perimity.qr.dto.JobStatusResponse;
import com.perimity.qr.dto.QrRecordResponse;
import com.perimity.qr.entity.enums.JobStatus;
import com.perimity.qr.security.JwtService;
import com.perimity.qr.service.GenerationJobService;
import com.perimity.qr.service.QrRecordService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for the public GET endpoints.
 *
 * @WebMvcTest rather than @SpringBootTest: this is the layer where the things
 * that actually break live - a path variable whose @Positive never runs, a
 * binary response that content-negotiates itself into a 406, an error that
 * escapes as a 500. None of those are visible in a service-layer unit test,
 * and all of them reach the frontend.
 *
 * addFilters = false strips the security chain. These tests are about routing,
 * content negotiation and validation; who may call each endpoint is
 * QrSecurityTest's job. Without this, every assertion below would fail with a
 * 401 and say nothing about the thing it was written to check.
 *
 * The two @MockBean filters' collaborators and the property are all context
 * plumbing: @WebMvcTest includes Filter beans, so InternalApiKeyFilter and
 * JwtAuthenticationFilter are both constructed here even though neither runs.
 * A blank api-key or a missing JwtService fails the whole context before a
 * single test executes.
 */
@WebMvcTest(QrController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "qr.internal.api-key=test-key-not-used-by-these-tests")
class QrControllerTest {

    @MockBean
    private JwtService jwtService;

    private static final byte[] PDF_BYTES = "%PDF-1.4 fake pass".getBytes();
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QrRecordService qrRecordService;

    @MockBean
    private GenerationJobService generationJobService;

    @Test
    void downloadPdf_returnsPdfBytesAsAnAttachment() throws Exception {
        given(qrRecordService.download(41L, true)).willReturn(PDF_BYTES);

        mockMvc.perform(get("/api/qr/41/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(PDF_BYTES))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"pass-41.pdf\""));
    }

    /**
     * The QR PNG and the pass PDF are two different objects. Passing the wrong
     * boolean here would serve a PNG with a .pdf filename and an
     * application/pdf content type - which opens as a corrupt file, not as an
     * error, so nothing would report it.
     */
    @Test
    void downloadPdf_asksForThePdfObjectNotTheQrPng() throws Exception {
        given(qrRecordService.download(anyLong(), eq(true))).willReturn(PDF_BYTES);

        mockMvc.perform(get("/api/qr/41/pdf")).andExpect(status().isOk());

        verify(qrRecordService).download(41L, true);
    }

    /**
     * A pass PDF carries the entry credential. If this ever starts coming back
     * cacheable, a copy sits in every proxy between the gate wifi and here.
     */
    @Test
    void downloadPdf_isNotCacheable() throws Exception {
        given(qrRecordService.download(41L, true)).willReturn(PDF_BYTES);

        mockMvc.perform(get("/api/qr/41/pdf"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    /**
     * The reason this test exists: the mapping declares
     * produces = application/pdf, and the 404 body is JSON.
     *
     * Spring clears the producible-media-type attribute before running the
     * @ExceptionHandler, so the JSON writes correctly - but that is framework
     * behaviour nobody on this team should have to remember. If a future Spring
     * upgrade or a stray @RequestMapping(produces=...) changes it, the visitor's
     * "pass not ready yet" screen turns into an unexplained 406 and this test is
     * what says why.
     */
    @Test
    void downloadPdf_missingRecordReturnsJsonNotFoundNotA406() throws Exception {
        given(qrRecordService.download(41L, true))
                .willThrow(new EntityNotFoundException("No active QR record for passId 41"));

        mockMvc.perform(get("/api/qr/41/pdf"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("No active QR record for passId 41"));
    }

    /**
     * Proves @Validated at class level is actually wired. Without it the
     * @Positive is silently ignored and a negative id reaches the service and
     * the database.
     */
    @Test
    void downloadPdf_rejectsANonPositivePassId() throws Exception {
        mockMvc.perform(get("/api/qr/-1/pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(qrRecordService);
    }

    // ---------------------------------------------------------------
    // GET /{passId}/image - the QR PNG the pass view renders
    // ---------------------------------------------------------------

    @Test
    void qrImage_returnsPngBytes() throws Exception {
        given(qrRecordService.download(41L, false)).willReturn(PNG_BYTES);

        mockMvc.perform(get("/api/qr/41/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG_BYTES));
    }

    /**
     * inline, not attachment. If this ever flips, the visitor's pass view stops
     * showing a QR and starts triggering a file download instead - a broken
     * screen that throws no error anywhere.
     */
    @Test
    void qrImage_isServedInlineNotAsADownload() throws Exception {
        given(qrRecordService.download(41L, false)).willReturn(PNG_BYTES);

        mockMvc.perform(get("/api/qr/41/image"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"pass-41.png\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    /**
     * The PNG and the PDF are two different stored objects behind one boolean.
     * Passing the wrong one here would serve a PDF with an image/png content
     * type, which renders as a broken image rather than as an error.
     */
    @Test
    void qrImage_asksForTheQrObjectNotThePdf() throws Exception {
        given(qrRecordService.download(anyLong(), eq(false))).willReturn(PNG_BYTES);

        mockMvc.perform(get("/api/qr/41/image")).andExpect(status().isOk());

        verify(qrRecordService).download(41L, false);
        verify(qrRecordService, never()).download(41L, true);
    }

    @Test
    void qrImage_missingRecordReturnsJsonNotFound() throws Exception {
        given(qrRecordService.download(41L, false))
                .willThrow(new EntityNotFoundException("No active QR record for passId 41"));

        mockMvc.perform(get("/api/qr/41/image"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ---------------------------------------------------------------
    // Regression cover for the three endpoints that existed before
    // /{passId}/pdf was added.
    //
    // /{passId} is a single-segment catch-all template. Adding a
    // two-segment template beside it is exactly the shape of change that
    // silently swallows an existing route - and the symptom would not be an
    // error, it would be the wrong handler returning a 200. These pin the
    // routing so that failure is loud.
    // ---------------------------------------------------------------

    @Test
    void getByPassId_stillReturnsMetadataAndNotThePdf() throws Exception {
        given(qrRecordService.getActiveByPassId(41L)).willReturn(
                QrRecordResponse.builder()
                        .passId(41L)
                        .campusId(1L)
                        .qrKey("1/qr/41/7.png")
                        .pdfKey("1/pdf/41/7.pdf")
                        .active(true)
                        .build());

        mockMvc.perform(get("/api/qr/41"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.passId").value(41))
                .andExpect(jsonPath("$.data.pdfKey").value("1/pdf/41/7.pdf"));

        verify(qrRecordService).getActiveByPassId(41L);
        verify(qrRecordService, never()).download(anyLong(), anyBoolean());
    }

    @Test
    void getJobStatus_stillResolves() throws Exception {
        given(generationJobService.getStatus(9L)).willReturn(
                JobStatusResponse.builder().jobId(9L).passId(41L).status(JobStatus.DONE).build());

        mockMvc.perform(get("/api/qr/jobs/9/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(9));

        verifyNoInteractions(qrRecordService);
    }

    @Test
    void getBatchProgress_stillResolves() throws Exception {
        given(generationJobService.getBatchProgress(3L)).willReturn(
                BatchProgressResponse.builder()
                        .batchId(3L).total(580).done(312).percentComplete(53).build());

        mockMvc.perform(get("/api/qr/jobs/batch/3/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(580))
                .andExpect(jsonPath("$.data.done").value(312));

        verifyNoInteractions(qrRecordService);
    }

    /**
     * The inverse of the test above: the PDF route must not be served by the
     * single-segment handler. If specificity ever stopped working, this comes
     * back as JSON metadata with a 200 - a "working" endpoint serving the
     * wrong thing, which the frontend would surface as a corrupt download.
     */
    @Test
    void pdfRoute_isNotSwallowedByTheSingleSegmentTemplate() throws Exception {
        given(qrRecordService.download(41L, true)).willReturn(PDF_BYTES);

        mockMvc.perform(get("/api/qr/41/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        verify(qrRecordService, never()).getActiveByPassId(anyLong());
    }
}
