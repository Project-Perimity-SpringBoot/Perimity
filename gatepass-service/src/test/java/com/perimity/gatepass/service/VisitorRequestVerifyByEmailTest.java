package com.perimity.gatepass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.perimity.gatepass.client.InternalServiceClient;
import com.perimity.gatepass.dto.request.VisitorEmailVerifiedDto;
import com.perimity.gatepass.entity.VisitorRequest;
import com.perimity.gatepass.entity.enums.RequestStatus;
import com.perimity.gatepass.exception.ResourceNotFoundException;
import com.perimity.gatepass.repository.EventRepository;
import com.perimity.gatepass.repository.GatePassRepository;
import com.perimity.gatepass.repository.VisitorRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PROPOSAL. The email-keyed verification path.
 *
 * These tests are about picking the RIGHT request. What happens once one is
 * picked is markEmailVerified's job, and duplicating those assertions here
 * would only make them harder to change.
 *
 * approvalRequired is stubbed true throughout, so nothing auto-approves and no
 * pass is issued. That keeps every case below about selection alone - the
 * auto-approve branch has its own coverage and its own reasons to change.
 */
@ExtendWith(MockitoExtension.class)
class VisitorRequestVerifyByEmailTest {

    private static final String EMAIL = "visitor@example.com";

    @Mock private VisitorRequestRepository requestRepository;
    @Mock private GatePassRepository gatePassRepository;
    @Mock private EventRepository eventRepository;
    @Mock private GatePassService gatePassService;
    @Mock private InternalServiceClient internal;

    private VisitorRequestService service;

    @BeforeEach
    void setUp() {
        service = new VisitorRequestService(requestRepository, gatePassRepository,
                eventRepository, gatePassService, internal);

        // Fails closed, as the production fallback does. Nothing auto-approves.
        lenient().when(internal.configBoolean(anyLong(), anyString(), anyBoolean()))
                .thenReturn(true);
        lenient().when(requestRepository.save(any(VisitorRequest.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private VisitorRequest request(Long id, RequestStatus status, boolean verified,
                                   LocalDateTime createdAt) {
        VisitorRequest r = new VisitorRequest();
        r.setId(id);
        r.setVisitorEmail(EMAIL);
        r.setCampusId(2L);
        r.setStatus(status);
        r.setOtpVerified(verified);
        r.setCreatedAt(createdAt);
        return r;
    }

    private VisitorEmailVerifiedDto dto() {
        VisitorEmailVerifiedDto d = new VisitorEmailVerifiedDto();
        d.setVisitorUserId(7L);
        return d;
    }

    /**
     * The repository returns newest first. A visitor who applied twice must not
     * have the stale request marked - that would leave the one they are
     * actually waiting on untouched, and fail silently.
     */
    @Test
    void picksTheNewestRequestStillAwaitingVerification() {
        VisitorRequest newer = request(41L, RequestStatus.PENDING, false, LocalDateTime.now());
        VisitorRequest older = request(12L, RequestStatus.PENDING, false,
                LocalDateTime.now().minusDays(3));

        given(requestRepository.findByVisitorEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(List.of(newer, older));
        given(requestRepository.findById(41L)).willReturn(Optional.of(newer));

        service.markEmailVerifiedByEmail(EMAIL, dto());

        verify(requestRepository).findById(41L);
        verify(requestRepository, never()).findById(12L);
    }

    /**
     * An already-verified request is not a candidate. Without this, a second
     * OTP for the same address re-marks a request that is already through - and
     * on a campus with approval.required=false, that is a second pass.
     */
    @Test
    void skipsRequestsThatAreAlreadyVerified() {
        VisitorRequest done = request(41L, RequestStatus.PENDING, true, LocalDateTime.now());
        VisitorRequest waiting = request(12L, RequestStatus.PENDING, false,
                LocalDateTime.now().minusDays(1));

        given(requestRepository.findByVisitorEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(List.of(done, waiting));
        given(requestRepository.findById(12L)).willReturn(Optional.of(waiting));

        service.markEmailVerifiedByEmail(EMAIL, dto());

        verify(requestRepository).findById(12L);
        verify(requestRepository, never()).findById(41L);
    }

    /** A decided request is finished. Verifying it later must not reopen it. */
    @Test
    void ignoresRequestsThatAreNoLongerPending() {
        given(requestRepository.findByVisitorEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(List.of(
                        request(41L, RequestStatus.REJECTED, false, LocalDateTime.now())));

        assertThatThrownBy(() -> service.markEmailVerifiedByEmail(EMAIL, dto()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * A visitor can verify a LOGIN code with nothing pending. That is a 404 the
     * caller tolerates rather than a fault - see GatepassVisitorClient in
     * auth-service, which logs it and lets the sign-in succeed.
     */
    @Test
    void nothingPendingIsANotFound() {
        given(requestRepository.findByVisitorEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.markEmailVerifiedByEmail(EMAIL, dto()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(EMAIL);
    }

    @Test
    void marksTheChosenRequestVerifiedAndRecordsTheHolder() {
        VisitorRequest waiting = request(41L, RequestStatus.PENDING, false, LocalDateTime.now());

        given(requestRepository.findByVisitorEmailOrderByCreatedAtDesc(EMAIL))
                .willReturn(List.of(waiting));
        given(requestRepository.findById(41L)).willReturn(Optional.of(waiting));

        service.markEmailVerifiedByEmail(EMAIL, dto());

        assertThat(waiting.isOtpVerified()).isTrue();
        assertThat(waiting.getVisitorUserId()).isEqualTo(7L);
    }
}
