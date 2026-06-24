FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew installDist --no-daemon

FROM eclipse-temurin:25-jre

LABEL org.opencontainers.image.source="https://github.com/UPT-FAING-EPIS/proyecto-si784-2026-i-u1-analizador-de-dependencias"
LABEL org.opencontainers.image.description="DepAnalyzer dependency and vulnerability analyzer"

RUN useradd --create-home --uid 10001 depanalyzer
WORKDIR /opt/depanalyzer
COPY --from=build /workspace/build/install/depanalyzer/ ./

USER depanalyzer
WORKDIR /project
ENTRYPOINT ["/opt/depanalyzer/bin/depanalyzer"]
CMD ["--help"]
