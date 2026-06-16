FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
COPY def ./def
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# --- Subprocess tools (no Java equivalent exists) ---------------------------
# 1. SCIP indexers — produce `index.scip` from a cloned repo (ScipService).
#    Bundling scip-java by default since initial sourcing scope is JVM repos.
#    Add scip-typescript / scip-go / scip-python / ... here as the supported
#    language set grows.
# 2. syft — generates CycloneDX SBOMs from a cloned repo (SbomService).
#    Repository cloning itself is in-process via JGit (Maven dep), so no
#    system `git` binary is required.
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends curl ca-certificates; \
    # scip-java via Coursier
    curl -fLo /usr/local/bin/cs https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz; \
    gunzip -f /usr/local/bin/cs || true; \
    chmod +x /usr/local/bin/cs; \
    cs install --install-dir /usr/local/bin --contrib scip-java; \
    # syft (Anchore) via official installer
    curl -fsSL https://raw.githubusercontent.com/anchore/syft/main/install.sh \
      | sh -s -- -b /usr/local/bin; \
    apt-get purge -y curl; \
    apt-get autoremove -y; \
    rm -rf /var/lib/apt/lists/*
# ---------------------------------------------------------------------------

COPY --from=build /workspace/target/itip-definition-blackboard-repository-sourcer-1.0.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
