package com.aircargo.authservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de correo desde auth-service. Si {@code app.mail.enabled=false}
 * (default), no hay SMTP configurado y el contenido se registra en el log
 * (útil en desarrollo local). Cuando está habilitado usa el JavaMailSender
 * autoconfigurado (lazy: no abre conexión al arrancar).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean mailEnabled;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${app.mail.enabled:false}") boolean mailEnabled,
                       @Value("${spring.mail.username:no-reply@aircargo.local}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailEnabled = mailEnabled;
        this.from = (from == null || from.isBlank()) ? "no-reply@aircargo.local" : from;
    }

    /**
     * Indica si el correo puede entregarse de verdad: requiere
     * {@code app.mail.enabled=true} Y un JavaMailSender disponible. Cuando es
     * false los códigos de recuperación solo quedan en el log, así que la UI
     * debe guiar al restablecimiento por administrador.
     */
    public boolean isDeliveryAvailable() {
        return mailEnabled && mailSenderProvider.getIfAvailable() != null;
    }

    public void sendMfaRecoveryCode(String to, String code, int expiryMinutes) {
        String subject = "AirCargo — Código de recuperación de acceso";
        String body = """
                Hola,

                Recibimos una solicitud para recuperar el acceso a tu autenticador (MFA).

                Tu código de verificación es: %s

                Este código vence en %d minutos y solo puede usarse una vez.
                Si no solicitaste este cambio, ignora este mensaje: tu cuenta sigue protegida.

                — AirCargo
                """.formatted(code, expiryMinutes);

        if (!mailEnabled) {
            log.info("[MAIL DISABLED] Código de recuperación MFA para {}: {} (vence en {} min)",
                    to, code, expiryMinutes);
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("[MAIL] app.mail.enabled=true pero no hay JavaMailSender configurado; código para {} no enviado", to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("[MAIL] Código de recuperación MFA enviado a {}", to);
        } catch (Exception e) {
            log.error("[MAIL] Falló el envío del código de recuperación a {}: {}", to, e.getMessage());
        }
    }
}
