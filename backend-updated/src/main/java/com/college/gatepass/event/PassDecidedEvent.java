package com.college.gatepass.event;

import com.college.gatepass.entity.GatePass;

/**
 * An in-process event published whenever a warden approves or rejects a gate pass.
 *
 * <p>{@code GatePassService} publishes this event using
 * {@code ApplicationEventPublisher.publishEvent()}.
 * {@code NotificationListener} picks it up asynchronously and sends the email.
 *
 * <p>Using an event here keeps the service layer clean — the service does not
 * need to know anything about email or notifications.
 *
 * @param pass   the gate pass that was decided (has the latest status already set)
 * @param action either "APPROVE" or "REJECT" so the listener knows which email to send
 */
public record PassDecidedEvent(GatePass pass, String action) {}