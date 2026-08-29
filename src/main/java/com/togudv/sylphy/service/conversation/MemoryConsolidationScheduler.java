package com.togudv.sylphy.service.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparos temporales de la consolidacion de memoria. Cada job solo consume
 * el nivel inferior; si falla, no se pierde material y el siguiente disparo
 * reintenta. Los crons son configurables.
 */
@Slf4j
@Component
public class MemoryConsolidationScheduler {

    private final MemoryConsolidationService consolidationService;

    public MemoryConsolidationScheduler(MemoryConsolidationService consolidationService) {
        this.consolidationService = consolidationService;
    }

    @Scheduled(cron = "${sylphy.chat.memory.cron.daily:0 0 2 * * *}")
    public void daily() {
        run("diaria", consolidationService::consolidateDaily);
    }

    @Scheduled(cron = "${sylphy.chat.memory.cron.weekly:0 0 3 * * MON}")
    public void weekly() {
        run("semanal", consolidationService::consolidateWeekly);
    }

    @Scheduled(cron = "${sylphy.chat.memory.cron.monthly:0 0 4 1 * *}")
    public void monthly() {
        run("mensual", consolidationService::consolidateMonthly);
    }

    @Scheduled(cron = "${sylphy.chat.memory.cron.annual:0 0 5 1 1 *}")
    public void annual() {
        run("anual", consolidationService::consolidateAnnual);
    }

    private void run(String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("consolidacion {} fallo; el material se conserva y se reintentara", label, e);
        }
    }
}
