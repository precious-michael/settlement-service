package org.settlementservice.settlementservice.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.enums.AsyncTaskStatus;
import org.settlementservice.settlementservice.enums.AsyncTaskType;
import org.settlementservice.settlementservice.models.AsyncTask;
import org.settlementservice.settlementservice.repositories.AsyncTaskRepository;
import org.settlementservice.settlementservice.services.SelfResolutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class SelfResolutionJob {

    private final SelfResolutionService selfResolutionService;
    private final AsyncTaskRepository asyncTaskRepository;

    @Scheduled(cron = "${self-resolution.schedule.cron:0 */5 * * * *}")
    public void run() {
        log.info("=============== SELF-RESOLUTION JOB TRIGGERED ===============");
        try {
            // Create AsyncTask to track this scheduled run
            AsyncTask task = asyncTaskRepository.save(AsyncTask.builder()
                    .type(AsyncTaskType.SELF_RESOLUTION)
                    .status(AsyncTaskStatus.PROCESSING)
                    .totalRecords(0L)
                    .processedRecords(0L)
                    .startedAt(Instant.now())
                    .build());

            log.info("Created async task {} for scheduled self-resolution", task.getId());

            // Call async method directly - does not block
            selfResolutionService.resolveAsync(null, null)
                    .whenComplete((count, ex) -> {
                        if (ex != null) {
                            log.error("Self-resolution task {} failed", task.getId(), ex);
                            task.setStatus(AsyncTaskStatus.FAILED);
                            task.setErrorMessage(ex.getMessage());
                        } else {
                            log.info("Self-resolution task {} completed - resolved {} transactions", task.getId(), count);
                            task.setStatus(AsyncTaskStatus.COMPLETED);
                            task.setProcessedRecords((long) count);
                            task.setTotalRecords((long) count);
                        }
                        task.setCompletedAt(Instant.now());
                        asyncTaskRepository.save(task);
                    });

            log.info("Self-resolution job scheduled async task {} and returned immediately", task.getId());
        } catch (Exception e) {
            log.error("Error in self-resolution job", e);
        }
    }
}
