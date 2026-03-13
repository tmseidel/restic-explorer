package org.remus.resticexplorer.repository.data;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.remus.resticexplorer.config.crypto.EncryptedStringConverter;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

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
    @Convert(converter = EncryptedStringConverter.class)
    private String repositoryPassword;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "repository_properties", joinColumns = @JoinColumn(name = "repository_id"))
    @MapKeyColumn(name = "property_key")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "property_value", length = 1024)
    private Map<RepositoryPropertyKey, String> properties = new EnumMap<>(RepositoryPropertyKey.class);

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

    /**
     * Get a property value by key.
     */
    public String getProperty(RepositoryPropertyKey key) {
        return properties.get(key);
    }

    /**
     * Set a property value. If value is null or blank, the property is removed.
     */
    public void setProperty(RepositoryPropertyKey key, String value) {
        if (value == null || value.isBlank()) {
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
    }

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
