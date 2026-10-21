package com.queueless.service;

import com.queueless.web.Dtos.PublicStatus;

/** Published inside a transaction; broadcast to WebSocket clients only after it commits. */
public record QueueChanged(String slug, PublicStatus status) {
}
