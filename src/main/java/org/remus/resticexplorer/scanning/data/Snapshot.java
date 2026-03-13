package org.remus.resticexplorer.scanning.data;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "snapshots")
@Getter
@Setter
@NoArgsConstructor
public class Snapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repositoryId;

    @Column(nullable = false)
    private String snapshotId;

    private String hostname;

    private String username;

    @Column(columnDefinition = "TEXT")
    private String paths;

    @Column(columnDefinition = "TEXT")
    private String tags;

    private LocalDateTime snapshotTime;

    @Column(columnDefinition = "TEXT")
    private String treeHash;

    private Long totalSize;

    private Long totalFileCount;

    private LocalDateTime cachedAt;

    @PrePersist
    protected void onCreate() {
        cachedAt = LocalDateTime.now();
    }
}
