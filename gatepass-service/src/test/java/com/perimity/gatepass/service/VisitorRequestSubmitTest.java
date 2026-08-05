package com.perimity.gatepass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.perimity.gatepass.client.InternalServiceClient;
import com.perimity.gatepass.dto.request.VisitorRequestCreateDto;
import com.perimity.gatepass.entity.VisitorRequest;
import com.perimity.gatepass.entity.enums.RequestStatus;
import com.perimity.gatepass.exception.ResourceNotFoundException;
import com.perimity.gatepass.repository.EventRepository;
import com.perimity.gatepass.repository.GatePassRepository;
import com.perimity.gatepass.repository.VisitorRequestRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * submit(), which had no test at all until a shipped change broke it.
 *
 * hostUserId was made optional on the DTO while the entity and the column were
 * still NOT NULL. Every test passed, because nothing here exercised submit -
 * the first visitor to use the new form would have been the test. These cover
 * the shape of a request rather than its plumbing, so the same class of gap
 * cannot reopen silently.
 */
@ExtendWith(MockitoExtension.class)
class VisitorRequestSubmitTest {

    private static final Long CAMPUS = 1L;

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

        lenient().when(internal.campusOf(anyLong()))
                .thenReturn(Optional.of(new InternalServiceClient.CampusView(
                        CAMPUS, "MAIN", "Main Campus")));
        // Fails closed, as production does. Nothing auto-approves here.
        lenient().when(internal.configBoolean(anyLong(), anyString(), anyBoolean()))
                .thenReturn(true);
        lenient().when(requestRepository.save(any(VisitorRequest.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private VisitorRequestCreateDto dto() {
        VisitorRequestCreateDto d = new VisitorRequestCreateDto();
        d.setCampusId(CAMPUS);
        d.setVisitorName("Anita Deshmukh");
        d.setVisitorEmail("anita@example.com");
        d.setPurpose("Meeting the project guide for a thesis review");
        d.setVisitFrom(LocalDate.now());
        d.setVisitTo(LocalDate.now().plusDays(1));
        return d;
    }

    /**
     * THE REGRESSION. A visitor picks a campus, not a person, so this is the
     * ordinary case now - not an edge case.
     */
    @Test
    void acceptsARequestWithNoHost() {
        service.submit(dto());

        ArgumentCaptor<VisitorRequest> saved = ArgumentCaptor.forClass(VisitorRequest.class);
        verify(requestRepository).save(saved.capture());

        assertThat(saved.getValue().getHostUserId()).isNull();
        assertThat(saved.getValue().getCampusId()).isEqualTo(CAMPUS);
        assertThat(saved.getValue().getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(saved.getValue().isOtpVerified()).isFalse();
    }

    /** Still carried when the visitor knows who invited them. */
    @Test
    void keepsTheHostWhenOneIsNamed() {
        VisitorRequestCreateDto d = dto();
        d.setHostUserId(42L);

        service.submit(d);

        ArgumentCaptor<VisitorRequest> saved = ArgumentCaptor.forClass(VisitorRequest.class);
        verify(requestRepository).save(saved.capture());
        assertThat(saved.getValue().getHostUserId()).isEqualTo(42L);
    }

    /**
     * An unknown campus would file the request into a queue no faculty can see.
     * The visitor would wait for an approval nobody is ever shown, which is the
     * worst kind of failure: silent and patient.
     */
    @Test
    void rejectsACampusThatDoesNotExist() {
        given(internal.campusOf(999L)).willReturn(Optional.empty());

        VisitorRequestCreateDto d = dto();
        d.setCampusId(999L);

        assertThatThrownBy(() -> service.submit(d))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(requestRepository, never()).save(any());
    }

    /**
     * Checked before the duplicate rule, so an unknown campus reports itself
     * instead of hiding behind "you already have a request".
     */
    @Test
    void reportsTheUnknownCampusRatherThanTheDuplicate() {
        given(internal.campusOf(999L)).willReturn(Optional.empty());

        VisitorRequestCreateDto d = dto();
        d.setCampusId(999L);

        assertThatThrownBy(() -> service.submit(d))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(requestRepository, never())
                .existsByVisitorEmailAndCampusIdAndStatus(anyString(), anyLong(), any());
    }

    /** One open request per person per campus, or a host gets five identical rows. */
    @Test
    void refusesASecondOpenRequestAtTheSameCampus() {
        given(requestRepository.existsByVisitorEmailAndCampusIdAndStatus(
                eq("anita@example.com"), eq(CAMPUS), eq(RequestStatus.PENDING)))
                .willReturn(true);

        assertThatThrownBy(() -> service.submit(dto()))
                .isInstanceOf(IllegalStateException.class);

        verify(requestRepository, never()).save(any());
    }

    /** Stored lowercase so identity matching by email cannot miss on case. */
    @Test
    void normalisesTheEmailAndTrimsTheName() {
        VisitorRequestCreateDto d = dto();
        d.setVisitorEmail("Anita@Example.COM");
        d.setVisitorName("  Anita Deshmukh  ");

        service.submit(d);

        ArgumentCaptor<VisitorRequest> saved = ArgumentCaptor.forClass(VisitorRequest.class);
        verify(requestRepository).save(saved.capture());
        assertThat(saved.getValue().getVisitorEmail()).isEqualTo("anita@example.com");
        assertThat(saved.getValue().getVisitorName()).isEqualTo("Anita Deshmukh");
    }
}
