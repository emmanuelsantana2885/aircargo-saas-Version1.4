package com.aircargo.notificationservice.config;

import com.aircargo.notificationservice.dto.NotificationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro de conexiones SSE por usuario (Server-Sent Events).
 *
 * <p>Cada usuario autenticado puede abrir una o varias pestañas; todas sus
 * conexiones viven en la misma lista bajo su userId. Cuando se persiste una
 * notificación para ese usuario ({@link com.aircargo.notificationservice.service.NotificationServiceImpl#createNotification}),
 * se empuja el DTO a todas sus conexiones. El heartbeat evita que proxies o
 * el navegador cierren la conexión inactiva.
 */
@Component
public class NotificationStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamRegistry.class);

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            cleanup.run();
        }
        return emitter;
    }

    public void send(UUID userId, NotificationDTO notification) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.getId() != null ? notification.getId().toString() : null)
                        .name("notification")
                        .data(notification, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.debug("Envío SSE a {} falló ({}), cerrando conexión", userId, e.getMessage());
                remove(userId, emitter);
            }
        }
    }

    /** Mantiene vivas las conexiones (los proxies suelen cortar a los ~30-60s sin tráfico). */
    @Scheduled(fixedRate = 25_000)
    public void keepAlive() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId, list);
            }
        }
    }
}
