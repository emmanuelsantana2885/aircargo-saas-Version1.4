package com.aircargo.common.audit;

import com.aircargo.common.event.AuditLogEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {

    private final UUID id = UUID.randomUUID();

    /** Fakes manuales: byte-buddy no puede mockear clases concretas en JDK 25. */
    static class FakeJdbc extends JdbcTemplate {
        int calls;
        Object[] args;

        @Override
        public int update(String sql, Object... args) {
            if (boom != null) throw boom;
            calls++;
            this.args = args;
            return 1;
        }

        Object arg(int i) { return args == null ? null : args[i]; }
        RuntimeException boom;
    }

    /** Fake de RabbitTemplate: captura el publish sin tocar broker. */
    static class FakeRabbit extends org.springframework.amqp.rabbit.core.RabbitTemplate {
        int calls;
        String exchange;
        String routingKey;
        Object payload;
        RuntimeException boom;

        @Override
        public void convertAndSend(String exchange, String routingKey, Object message) {
            calls++;
            if (boom != null) throw boom;
            this.exchange = exchange;
            this.routingKey = routingKey;
            this.payload = message;
        }
    }

    @Test
    void conBD_persisteUnSoloRegistro() {
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(jdbc);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "USER_UPDATED", "USER", id.toString(), "{}", "10.1.2.3"));
        assertEquals(1, jdbc.calls);
    }

    @Test
    void insertFallido_noLanzaYAdvierte() {
        FakeJdbc jdbc = new FakeJdbc();
        jdbc.boom = new RuntimeException("bd caida");

        AuditService svc = new AuditService(jdbc);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "MAWB_UPDATED", "MAWB", id.toString(), null, "10.1.2.5"));
        assertEquals(0, jdbc.calls);  // el fallo se registra en el log de la app, no revienta la request
    }

    @Test
    void sinBD_noLanza() {
        AuditService svc = new AuditService(null);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "X", null, null, null, null));
    }

    @Test
    void ipSePseudonimizaEnInsert() {
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(jdbc);
        svc.log(id, "a@x.com", "Ana", "X", null, null, null, "192.168.23.45");
        assertEquals("192.168.23.0", jdbc.arg(7));
    }

    @Test
    void detailsNulosSeSanitizan() {
        FakeJdbc jdbc = new FakeJdbc();

        AuditService svc = new AuditService(jdbc);
        svc.log(id, "a@x.com", "Ana", "X", null, null, null, "10.0.0.1");

        // details (índice 6) nunca debe llegar null al INSERT (TextUtil.safe)
        assertNotNull(jdbc.arg(6));
        assertNull(jdbc.arg(4)); // entityType sí puede ser null
    }

    @Test
    void conRabbit_publicaPorAmqpSinEscribirEnJdbc() {
        FakeJdbc jdbc = new FakeJdbc();
        FakeRabbit rabbit = new FakeRabbit();

        AuditService svc = new AuditService(jdbc);
        svc.setRabbitTemplate(rabbit);
        svc.log(id, "a@x.com", "Ana", "FLIGHT_UPDATED", "FLIGHT", id.toString(), "{}", "10.1.2.3");

        assertEquals(1, rabbit.calls);
        assertEquals(AuditService.AUDIT_EXCHANGE, rabbit.exchange);
        assertEquals(AuditService.AUDIT_ROUTING_KEY, rabbit.routingKey);
        assertTrue(rabbit.payload instanceof AuditLogEvent);
        assertEquals("FLIGHT_UPDATED", ((AuditLogEvent) rabbit.payload).action());
        assertEquals(0, jdbc.calls);  // una sola escritura: AMQP, nunca doble
    }

    @Test
    void siRabbitFalla_caeAlFallbackJdbcComoUnaSolaEscritura() {
        FakeJdbc jdbc = new FakeJdbc();
        FakeRabbit rabbit = new FakeRabbit();
        rabbit.boom = new RuntimeException("broker caido");

        AuditService svc = new AuditService(jdbc);
        svc.setRabbitTemplate(rabbit);
        assertDoesNotThrow(() ->
                svc.log(id, "a@x.com", "Ana", "MAWB_DELETED", "MAWB", id.toString(), null, "192.168.23.45"));

        assertEquals(1, rabbit.calls);   // el intento AMQP ocurrió
        assertEquals(1, jdbc.calls);     // fallback JDBC cubrió el evento
        assertEquals("192.168.23.0", jdbc.arg(7)); // IP pseudonimizada en el fallback
    }

    @Test
    void conRabbit_elEventoLlevaIpPseudonimizada() {
        AuditService svc = new AuditService(null);
        FakeRabbit rabbit = new FakeRabbit();
        svc.setRabbitTemplate(rabbit);
        svc.log(id, "a@x.com", "Ana", "X", null, null, null, "192.168.23.45");

        assertEquals("192.168.23.0", ((AuditLogEvent) rabbit.payload).ipAddress());
    }
}
