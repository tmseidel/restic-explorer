package org.remus.resticexplorer.repository.data;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "restic_repositories")
@Getter
@Setter
@NoArgsConstructor
public class ResticRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RepositoryType type;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String repositoryPassword;

    // S3-specific fields
    private String s3AccessKey;
    private String s3SecretKey;
    private String s3Region;

    @Column(nullable = false)
    private Integer scanIntervalMinutes = 60;

    private LocalDateTime lastScanned;

    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private RepositoryGroup group;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
