package com.college.gatepass.listener;

import com.college.gatepass.event.PassDecidedEvent;
import com.college.gatepass.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for {@code PassDecidedEvent} and sends the appropriate email to
 * the student whose pass was just approved or rejected.
 *
 * <p>The listener is decoupled from {@code GatePassService} via the Spring event
 * system: the service just publishes an event and does not know or care about
 * emails. This keeps the service focused on business logic and makes notifications
 * easy to swap out or extend later (e.g. adding SMS or push notifications).
 *
 * <p>{@code @Async} ensures the email is sent on the {@code appTaskExecutor}
 * thread pool, not on the HTTP request thread. The warden's approve/reject
 * API call returns immediately without waiting for the email to go out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final EmailService emailService;

    /**
     * Receives a {@code PassDecidedEvent} and sends the correct email based on
     * whether the pass was approved or rejected.
     *
     * @param event the event containing the decided pass and the action string
     *              ("APPROVE" or "REJECT")
     */
    @Async("appTaskExecutor")
    @EventListener
    public void onPassDecided(PassDecidedEvent event) {
        log.info("[NOTIFY] Pass {} — action: {} — student: {}",
                event.pass().getId(),
                event.action(),
                event.pass().getStudent() != null ? event.pass().getStudent().getEmail() : "unknown");

        switch (event.action()) {
            case "APPROVE" -> emailService.sendApprovalEmail(event.pass());
            case "REJECT"  -> emailService.sendRejectionEmail(event.pass());
            default        -> log.warn("Unknown action in PassDecidedEvent: {}", event.action());
        }
    }
}