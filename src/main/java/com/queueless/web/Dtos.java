package com.queueless.web;

import java.util.List;

import com.queueless.domain.TicketStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class Dtos {

    private Dtos() {
    }

    // ---- requests ----
    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 60) String name) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record CreateShopRequest(@NotBlank @Size(max = 60) String name,
                                    @Min(1) @Max(120) int avgServiceMinutes) {
    }

    public record JoinRequest(@NotBlank @Size(max = 40) String name) {
    }

    // ---- responses ----
    public record AuthResponse(String token, String name, String email) {
    }

    public record ShopSummary(Long id, String name, String slug, boolean open, int waiting) {
    }

    /** Safe-to-publish view of a queue; also the payload pushed over WebSocket. */
    public record PublicStatus(String shopName, boolean open, Integer nowServing, int waiting,
                               long avgServiceSeconds, long estimatedWaitSeconds) {
    }

    public record TicketView(String token, String shopName, String shopSlug, int number, String customerName,
                             TicketStatus status, Integer nowServing, Integer position, Long etaSeconds) {
    }

    public record ServingEntry(int number, String name, long elapsedSeconds) {
    }

    public record WaitingEntry(int number, String name, long waitedSeconds) {
    }

    public record OwnerSnapshot(ShopSummary shop, ServingEntry serving, List<WaitingEntry> waiting,
                                long servedCount, long avgServiceSeconds) {
    }
}
