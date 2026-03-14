package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.bettingPlatform.BettingWebsite.dto.ApiResponse;
import com.bettingPlatform.BettingWebsite.service.ScheduledFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADMIN ONLY — Manual trigger & status endpoint for all scheduled fetch jobs.
 *
 * Base path : /api/v1/admin/scheduler
 * Security  : ROLE_ADMIN only (all endpoints)
 *
 * Endpoints:
 *   POST /run/fixtures           → trigger fetchTodayFixtures
 *   POST /run/odds               → trigger fetchOdds
 *   POST /run/live-scores        → trigger fetchLiveScores
 *   POST /run/settle-predictions → trigger autoSettlePredictions
 *   POST /run/expire-vip         → trigger expireVipSubscriptions
 *   POST /run/all                → trigger all jobs sequentially
 *   GET  /status                 → last-run timestamps + running flags
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/scheduler")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ScheduledFetchController {

    private final ScheduledFetchService scheduledFetchService;

    // ── Per-job "currently running" guards ───────────────────────
    private final AtomicBoolean fixturesRunning          = new AtomicBoolean(false);
    private final AtomicBoolean oddsRunning              = new AtomicBoolean(false);
    private final AtomicBoolean liveScoresRunning        = new AtomicBoolean(false);
    private final AtomicBoolean settlePredictionsRunning = new AtomicBoolean(false);
    private final AtomicBoolean expireVipRunning         = new AtomicBoolean(false);

    // ── Last-run timestamps ───────────────────────────────────────
    private final Map<String, LocalDateTime> lastRun = new ConcurrentHashMap<>();


    // ─────────────────────────────────────────────────────────────
    // POST /run/fixtures
    // ─────────────────────────────────────────────────────────────

    /**
     * Manually triggers fetchTodayFixtures (next 7 days).
     * Returns 409 if the job is already running.
     */
    @PostMapping("/run/fixtures")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runFixtures(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[SCHEDULER][ADMIN] Manual trigger: fetchTodayFixtures → adminId={}",
                principal.getUserId());

        if (!fixturesRunning.compareAndSet(false, true)) {
            log.warn("[SCHEDULER][ADMIN] fetchTodayFixtures already running → adminId={}",
                    principal.getUserId());
            return conflict("fetchTodayFixtures is already running");
        }

        CompletableFuture.runAsync(() -> {
            try {
                scheduledFetchService.fetchTodayFixtures();
                lastRun.put("fixtures", LocalDateTime.now());
                log.info("[SCHEDULER][ADMIN] fetchTodayFixtures completed");
            } catch (Exception e) {
                log.error("[SCHEDULER][ADMIN] fetchTodayFixtures failed: {}", e.getMessage(), e);
            } finally {
                fixturesRunning.set(false);
            }
        });

        return accepted("fetchTodayFixtures", "Fixture fetch triggered asynchronously");
    }


    // ─────────────────────────────────────────────────────────────
    // POST /run/odds
    // ─────────────────────────────────────────────────────────────

    /**
     * Manually triggers fetchOdds for all supported leagues.
     * Returns 409 if the job is already running.
     */
    @PostMapping("/run/odds")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runOdds(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[SCHEDULER][ADMIN] Manual trigger: fetchOdds → adminId={}",
                principal.getUserId());

        if (!oddsRunning.compareAndSet(false, true)) {
            log.warn("[SCHEDULER][ADMIN] fetchOdds already running → adminId={}",
                    principal.getUserId());
            return conflict("fetchOdds is already running");
        }

        CompletableFuture.runAsync(() -> {
            try {
                scheduledFetchService.fetchOdds();
                lastRun.put("odds", LocalDateTime.now());
                log.info("[SCHEDULER][ADMIN] fetchOdds completed");
            } catch (Exception e) {
                log.error("[SCHEDULER][ADMIN] fetchOdds failed: {}", e.getMessage(), e);
            } finally {
                oddsRunning.set(false);
            }
        });

        return accepted("fetchOdds", "Odds fetch triggered asynchronously");
    }


    // ─────────────────────────────────────────────────────────────
    // POST /run/live-scores
    // ─────────────────────────────────────────────────────────────

    /**
     * Manually triggers fetchLiveScores and pushes to WebSocket topic.
     * Returns 409 if the job is already running.
     */
    @PostMapping("/run/live-scores")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runLiveScores(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[SCHEDULER][ADMIN] Manual trigger: fetchLiveScores → adminId={}",
                principal.getUserId());

        if (!liveScoresRunning.compareAndSet(false, true)) {
            log.warn("[SCHEDULER][ADMIN] fetchLiveScores already running → adminId={}",
                    principal.getUserId());
            return conflict("fetchLiveScores is already running");
        }

        CompletableFuture.runAsync(() -> {
            try {
                scheduledFetchService.fetchLiveScores();
                lastRun.put("liveScores", LocalDateTime.now());
                log.info("[SCHEDULER][ADMIN] fetchLiveScores completed");
            } catch (Exception e) {
                log.error("[SCHEDULER][ADMIN] fetchLiveScores failed: {}", e.getMessage(), e);
            } finally {
                liveScoresRunning.set(false);
            }
        });

        return accepted("fetchLiveScores", "Live score fetch triggered asynchronously");
    }


    // ─────────────────────────────────────────────────────────────
    // POST /run/settle-predictions
    // ─────────────────────────────────────────────────────────────

    /**
     * Manually triggers autoSettlePredictions for all pending predictions.
     * Returns 409 if the job is already running.
     */
    @PostMapping("/run/settle-predictions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runSettlePredictions(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[SCHEDULER][ADMIN] Manual trigger: autoSettlePredictions → adminId={}",
                principal.getUserId());

        if (!settlePredictionsRunning.compareAndSet(false, true)) {
            log.warn("[SCHEDULER][ADMIN] autoSettlePredictions already running → adminId={}",
                    principal.getUserId());
            return conflict("autoSettlePredictions is already running");
        }

        CompletableFuture.runAsync(() -> {
            try {
                scheduledFetchService.autoSettlePredictions();
                lastRun.put("settlePredictions", LocalDateTime.now());
                log.info("[SCHEDULER][ADMIN] autoSettlePredictions completed");
            } catch (Exception e) {
                log.error("[SCHEDULER][ADMIN] autoSettlePredictions failed: {}", e.getMessage(), e);
            } finally {
                settlePredictionsRunning.set(false);
            }
        });

        return accepted("autoSettlePredictions", "Prediction settlement triggered asynchronously");
    }


    // ─────────────────────────────────────────────────────────────
    // POST /run/expire-vip
    // ─────────────────────────────────────────────────────────────

    /**
     * Manually triggers expireVipSubscriptions.
     * Returns 409 if the job is already running.
     */
    @PostMapping("/run/expire-vip")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runExpireVip(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[SCHEDULER][ADMIN] Manual trigger: expireVipSubscriptions → adminId={}",
                principal.getUserId());

        if (!expireVipRunning.compareAndSet(false, true)) {
            log.warn("[SCHEDULER][ADMIN] expireVipSubscriptions already running → adminId={}",
                    principal.getUserId());
            return conflict("expireVipSubscriptions is already running");
        }

        CompletableFuture.runAsync(() -> {
            try {
                scheduledFetchService.expireVipSubscriptions();
                lastRun.put("expireVip", LocalDateTime.now());
                log.info("[SCHEDULER][ADMIN] expireVipSubscriptions completed");
            } catch (Exception e) {
                log.error("[SCHEDULER][ADMIN] expireVipSubscriptions failed: {}", e.getMessage(), e);
            } finally {
                expireVipRunning.set(false);
            }
        });

        return accepted("expireVipSubscriptions", "VIP expiry job triggered asynchronously");
    }


    // ─────────────────────────────────────────────────────────────
    // POST /run/all
    // ─────────────────────────────────────────────────────────────

    /**
     * Triggers all five jobs sequentially on a single async thread.
     * Returns 409 if ANY job is currently running.
     */
    @PostMapping("/run/all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runAll(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[SCHEDULER][ADMIN] Manual trigger: ALL jobs → adminId={}",
                principal.getUserId());

        boolean anyRunning = fixturesRunning.get()
                || oddsRunning.get()
                || liveScoresRunning.get()
                || settlePredictionsRunning.get()
                || expireVipRunning.get();

        if (anyRunning) {
            log.warn("[SCHEDULER][ADMIN] Cannot run all — one or more jobs already running → adminId={}",
                    principal.getUserId());
            return conflict("One or more scheduler jobs are already running. Check /status.");
        }

        // Optimistically set all flags
        fixturesRunning.set(true);
        oddsRunning.set(true);
        liveScoresRunning.set(true);
        settlePredictionsRunning.set(true);
        expireVipRunning.set(true);

        CompletableFuture.runAsync(() -> {
            runSafe("fetchTodayFixtures",      scheduledFetchService::fetchTodayFixtures,      "fixtures",          fixturesRunning);
            runSafe("fetchOdds",               scheduledFetchService::fetchOdds,               "odds",              oddsRunning);
            runSafe("fetchLiveScores",         scheduledFetchService::fetchLiveScores,         "liveScores",        liveScoresRunning);
            runSafe("autoSettlePredictions",   scheduledFetchService::autoSettlePredictions,   "settlePredictions", settlePredictionsRunning);
            runSafe("expireVipSubscriptions",  scheduledFetchService::expireVipSubscriptions,  "expireVip",         expireVipRunning);
            log.info("[SCHEDULER][ADMIN] ALL jobs completed");
        });

        return accepted("ALL", "All 5 scheduler jobs triggered sequentially and asynchronously");
    }


    // ─────────────────────────────────────────────────────────────
    // GET /status
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the running status and last-run timestamp of each job.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[SCHEDULER][ADMIN] Status requested → adminId={}", principal.getUserId());

        Map<String, Object> status = new LinkedHashMap<>();

        status.put("fetchTodayFixtures",     jobStatus("fixtures",          fixturesRunning));
        status.put("fetchOdds",              jobStatus("odds",              oddsRunning));
        status.put("fetchLiveScores",        jobStatus("liveScores",        liveScoresRunning));
        status.put("autoSettlePredictions",  jobStatus("settlePredictions", settlePredictionsRunning));
        status.put("expireVipSubscriptions", jobStatus("expireVip",         expireVipRunning));
        status.put("checkedAt",              LocalDateTime.now().toString());

        return ResponseEntity.ok(ApiResponse.success("Scheduler status", status));
    }


    // ─────────────────────────────────────────────────────────────
    // PRIVATE helpers
    // ─────────────────────────────────────────────────────────────

    /** Builds a per-job status map. */
    private Map<String, Object> jobStatus(String key, AtomicBoolean running) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", running.get());
        m.put("lastRun", lastRun.getOrDefault(key, null));
        return m;
    }

    /** Wraps a job in try/catch, updates lastRun, clears flag. */
    private void runSafe(String name, Runnable job, String key, AtomicBoolean flag) {
        try {
            log.info("[SCHEDULER][ADMIN] Starting job: {}", name);
            job.run();
            lastRun.put(key, LocalDateTime.now());
            log.info("[SCHEDULER][ADMIN] Completed job: {}", name);
        } catch (Exception e) {
            log.error("[SCHEDULER][ADMIN] Job '{}' failed: {}", name, e.getMessage(), e);
        } finally {
            flag.set(false);
        }
    }

    /** 202 Accepted response for a triggered job. */
    private ResponseEntity<ApiResponse<Map<String, Object>>> accepted(
            String job, String message) {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("job",         job);
        data.put("triggeredAt", LocalDateTime.now().toString());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(message, data));
    }

    /** 409 Conflict response when a job is already running. */
    private ResponseEntity<ApiResponse<Map<String, Object>>> conflict(String reason) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(reason));
    }
}