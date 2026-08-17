package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.AsyncTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsyncTaskRepository extends JpaRepository<AsyncTask, Long> {
}
