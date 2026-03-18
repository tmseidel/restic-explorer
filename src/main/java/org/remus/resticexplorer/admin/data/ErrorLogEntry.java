package org.remus.resticexplorer.admin.data;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "error_log_entries", indexes = {
        @Index(name = "idx_error_log_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
public class ErrorLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private Long repositoryId;

    private String repositoryName;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
