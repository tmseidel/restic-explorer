package org.remus.resticexplorer.scanning.data;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "check_results")
@Getter
@Setter
@NoArgsConstructor
public class CheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repositoryId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CheckStatus status;

    @Column(columnDefinition = "TEXT")
    private String message;

    private LocalDateTime checkedAt;

    @PrePersist
    protected void onCreate() {
        checkedAt = LocalDateTime.now();
    }

    public enum CheckStatus {
        SUCCESS, FAILED, IN_PROGRESS
    }
}
