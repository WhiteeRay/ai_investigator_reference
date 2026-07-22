package org.di.ai_investigator_report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.ai_investigator_report.entity.LogEntry;
import org.di.ai_investigator_report.repository.LogEntryRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final LogEntryRepository logEntryRepository;

    public LogEntry saveFileContent(byte[] bytes, String fileName) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        LogEntry entry = new LogEntry();
        entry.setContent(content);
        entry.setTimestamp(LocalDateTime.now());
        LogEntry saved = logEntryRepository.save(entry);
        log.info("Saved log file content to DB: id={}, file={}", saved.getId(), fileName);
        return saved;
    }
}
