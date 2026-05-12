# File Deduplication Service

INFO0027 project — pluggable deduplication backend for an online file-sharing
platform.

## Build

```sh
mvn clean package
```

This produces `Deduplicator.jar` at the project root. The JAR contains
the `META-INF/services/be.uliege.info0027.deduplication.FileDeduplicationBootstrap`
provider configuration, so the test harness can discover the implementation
via `ServiceLoader`.

## Requirements

- JDK 25 (Eclipse Temurin)
- Maven 3.9+

## Authentication for the GitLab interfaces dependency

The interfaces dependency lives on the institutional GitLab Package Registry.
Maven needs credentials configured in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>gitlab-maven</id>
      <configuration>
        <httpHeaders>
          <property>
            <name>Private-Token</name>
            <value>YOUR_PERSONAL_ACCESS_TOKEN</value>
          </property>
        </httpHeaders>
      </configuration>
    </server>
  </servers>
</settings>
```

## Run tests

```sh
mvn test
```

## Project layout

```
src/main/java/be/uliege/info0027/dedup/
├── DeduplicationBootstrapImpl.java   composition root (ServiceLoader entry)
├── core/DeduplicationService.java    orchestrates walk + engine
├── engine/
│   ├── DeduplicationEngine.java      Strategy interface
│   ├── DeduplicationEngineFactory.java
│   ├── ExactDeduplicationEngine.java SHA-256 over file contents
│   └── SimilarDeduplicationEngine.java   pure-Java aHash for images
├── frontend/FrontendGateImpl.java    JSON adapter for the Frontend team
├── storage/StorageCheckerImpl.java   on-the-fly upload check
└── util/VfsWalker.java               recursive VFS traversal
```
