package org.di.ai_investigator_report.repository;

import org.di.ai_investigator_report.entity.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {
}
