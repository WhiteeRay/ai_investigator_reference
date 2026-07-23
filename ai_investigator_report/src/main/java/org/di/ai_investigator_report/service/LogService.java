package org.di.ai_investigator_report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.di.ai_investigator_report.entity.LogEntry;
import org.di.ai_investigator_report.repository.LogEntryRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final LogEntryRepository logEntryRepository;

    public LogEntry saveFileContent(byte[] bytes, String fileName) {
        String content;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            content = extractor.getText();
        } catch (Exception e) {
            log.warn("Could not extract text from docx, storing as base64: {}", e.getMessage());
            content = java.util.Base64.getEncoder().encodeToString(bytes);
        }
        LogEntry entry = new LogEntry();
        entry.setContent(content);
        entry.setTimestamp(LocalDateTime.now());
        LogEntry saved = logEntryRepository.save(entry);
        log.info("Saved log file content to DB: id={}, file={}, length={}", saved.getId(), fileName, content.length());
        return saved;
    }
}
