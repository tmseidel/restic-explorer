package org.remus.resticexplorer.scanning.data;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "scan_results")
@Getter
@Setter
@NoArgsConstructor
public class ScanResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repositoryId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ScanStatus status;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Integer snapshotCount;

    private Long totalSize;

    private Boolean retentionPolicyFulfilled;

    @Column(columnDefinition = "TEXT")
    private String retentionPolicyViolations;

    private LocalDateTime scannedAt;

    @PrePersist
    protected void onCreate() {
        scannedAt = LocalDateTime.now();
    }

    public enum ScanStatus {
        SUCCESS, FAILED, IN_PROGRESS
    }
}
