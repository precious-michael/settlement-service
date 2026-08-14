package org.settlementservice.settlementservice.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.services.SelfResolutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SelfResolutionJob {

    private final SelfResolutionService selfResolutionService;

    @Scheduled(cron = "${self-resolution.schedule.cron:0 0 * * * *}")
    public void run() {
        log.debug("Self-resolution job started");
        selfResolutionService.resolve(null, null);
    }
}
