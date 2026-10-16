package com.aircargo.loadplanningservice.service;

import com.aircargo.feign.client.FlightClient;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.client.UldClient;
import com.aircargo.feign.dto.FlightDTO;
import com.aircargo.feign.dto.MawbDTO;
import com.aircargo.loadplanningservice.config.RabbitConfig;
import com.aircargo.loadplanningservice.dto.LoadPlanningDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadPlanningServiceImplTest {

    @Mock
    private FlightClient flightClient;
    @Mock
    private UldClient uldClient;
    @Mock
    private MawbClient mawbClient;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private WeightBalanceService weightBalanceService;

    private LoadPlanningServiceImpl service;

    private UUID flightId;

    @BeforeEach
    void setUp() {
        service = new LoadPlanningServiceImpl(flightClient, uldClient, mawbClient, rabbitTemplate, weightBalanceService);
        flightId = UUID.randomUUID();
    }

    private FlightDTO departedFlight() {
        FlightDTO f = new FlightDTO();
        f.setId(flightId);
        f.setFlightNumber("0403");
        f.setStatus("DEPARTED");
        f.setAircraftType("B767");
        return f;
    }

    private int statusOf(Executable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call);
        return ex.getStatusCode().value();
    }

    // ---------- recoverLoadPlan: validacion de la excepcion ----------

    @Test
    void recoverLoadPlan_rejectsMissingExceptionType() {
        when(flightClient.getFlightById(flightId)).thenReturn(departedFlight());

        assertEquals(HttpStatus.BAD_REQUEST.value(),
                statusOf(() -> service.recoverLoadPlan(flightId, null, "storm")));

        verify(flightClient, never()).updateFlightStatus(any(), any());
        verify(uldClient, never()).recoverFlight(any());
    }

    @Test
    void recoverLoadPlan_rejectsBlankExceptionType() {
        when(flightClient.getFlightById(flightId)).thenReturn(departedFlight());

        assertEquals(HttpStatus.BAD_REQUEST.value(),
                statusOf(() -> service.recoverLoadPlan(flightId, "   ", "storm")));

        verify(flightClient, never()).updateFlightStatus(any(), any());
    }

    @Test
    void recoverLoadPlan_rejectsUnknownExceptionType() {
        when(flightClient.getFlightById(flightId)).thenReturn(departedFlight());

        assertEquals(HttpStatus.BAD_REQUEST.value(),
                statusOf(() -> service.recoverLoadPlan(flightId, "COFFEE_BREAK", "storm")));

        verify(flightClient, never()).updateFlightStatus(any(), any());
        verify(uldClient, never()).recoverFlight(any());
    }

    @Test
    void recoverLoadPlan_rejectsMissingReason() {
        when(flightClient.getFlightById(flightId)).thenReturn(departedFlight());

        assertEquals(HttpStatus.BAD_REQUEST.value(),
                statusOf(() -> service.recoverLoadPlan(flightId, "FLIGHT_PROBLEM", "  ")));

        verify(flightClient, never()).updateFlightStatus(any(), any());
        verify(uldClient, never()).recoverFlight(any());
    }

    @Test
    void recoverLoadPlan_rejectsNonDepartedFlight() {
        FlightDTO scheduled = departedFlight();
        scheduled.setStatus("SCHEDULED");
        when(flightClient.getFlightById(flightId)).thenReturn(scheduled);

        assertEquals(HttpStatus.BAD_REQUEST.value(),
                statusOf(() -> service.recoverLoadPlan(flightId, "FLIGHT_PROBLEM", "fallo de motor")));

        verify(flightClient, never()).updateFlightStatus(any(), any());
        verify(uldClient, never()).recoverFlight(any());
    }

    @Test
    void recoverLoadPlan_returns404_whenFlightMissing() {
        when(flightClient.getFlightById(flightId)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND.value(),
                statusOf(() -> service.recoverLoadPlan(flightId, "FLIGHT_PROBLEM", "fallo de motor")));
    }

    // ---------- recoverLoadPlan: camino feliz ----------

    @Test
    void recoverLoadPlan_revertsFlightMawbsAndUlds() {
        when(flightClient.getFlightById(flightId)).thenReturn(departedFlight());

        UUID mawbId = UUID.randomUUID();
        MawbDTO departedMawb = new MawbDTO();
        departedMawb.setId(mawbId);
        departedMawb.setStatus("DEPARTED");
        when(mawbClient.getMawbsByFlight(flightId)).thenReturn(List.of(departedMawb));
        when(uldClient.recoverFlight(flightId)).thenReturn(Map.of("recovered", 3));
        when(uldClient.getUlds(null, flightId)).thenReturn(List.of());

        LoadPlanningDTO plan = service.recoverLoadPlan(flightId, "OPERATIONAL_DEVIATION",
                "desviacion de carga en rampa");

        assertNotNull(plan);
        assertEquals(flightId, plan.getFlightId());
        verify(flightClient).updateFlightStatus(flightId, "SCHEDULED");
        verify(mawbClient).updateMawbStatus(mawbId, "MANIFESTED");
        verify(uldClient).recoverFlight(flightId);
    }

    @Test
    void recoverLoadPlan_normalizesLowercaseExceptionType() {
        when(flightClient.getFlightById(flightId)).thenReturn(departedFlight());
        when(mawbClient.getMawbsByFlight(flightId)).thenReturn(List.of());
        when(uldClient.recoverFlight(flightId)).thenReturn(Map.of("recovered", 0));
        when(uldClient.getUlds(null, flightId)).thenReturn(List.of());

        LoadPlanningDTO plan = service.recoverLoadPlan(flightId, "flight_problem", "ruido en tren de aterrizaje");

        assertNotNull(plan);
        verify(flightClient).updateFlightStatus(flightId, "SCHEDULED");
        verify(uldClient).recoverFlight(flightId);
    }

    @Test
    void recoverLoadPlan_succeedsEvenWhenUldServiceFails() {
        when(flightClient.getFlightById(flightId)).thenReturn(departedFlight());
        when(mawbClient.getMawbsByFlight(flightId)).thenReturn(List.of());
        when(uldClient.recoverFlight(flightId)).thenThrow(new RuntimeException("uld service down"));
        when(uldClient.getUlds(null, flightId)).thenReturn(List.of());

        LoadPlanningDTO plan = service.recoverLoadPlan(flightId, "OTHER", "cierre parcial del aeropuerto");

        assertNotNull(plan);
        verify(flightClient).updateFlightStatus(flightId, "SCHEDULED");
    }

    // ---------- closeLoadPlan: despacha los ULDs ----------

    @Test
    void closeLoadPlan_setsDepartedAndDispatchesUlds() {
        when(flightClient.getFlightById(flightId)).thenReturn(departedFlight());
        when(uldClient.getUlds(null, flightId)).thenReturn(List.of());
        when(uldClient.dispatchFlight(flightId)).thenReturn(Map.of("dispatched", 2));

        LoadPlanningDTO plan = service.closeLoadPlan(flightId);

        assertNotNull(plan);
        verify(flightClient).updateFlightStatus(flightId, "DEPARTED");
        verify(uldClient).dispatchFlight(flightId);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("flight.departed"), (Object) any());
    }

    @Test
    void closeLoadPlan_survivesDispatchFailure() {
        when(flightClient.getFlightById(flightId)).thenReturn(departedFlight());
        when(uldClient.getUlds(null, flightId)).thenReturn(List.of());
        when(uldClient.dispatchFlight(flightId)).thenThrow(new RuntimeException("network"));

        LoadPlanningDTO plan = service.closeLoadPlan(flightId);

        assertNotNull(plan);
        verify(flightClient).updateFlightStatus(flightId, "DEPARTED");
    }

    @Test
    void closeLoadPlan_returns404_whenFlightMissing() {
        when(flightClient.getFlightById(flightId)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND.value(),
                statusOf(() -> service.closeLoadPlan(flightId)));

        verify(uldClient, never()).dispatchFlight(any());
    }
}
