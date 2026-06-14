FROM gradle:9.2.1-jdk17 AS build
WORKDIR /workspace
COPY tts-backend/ ./
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/install/tts-backend/ ./
ENV PORT=8080
EXPOSE 8080
CMD ["./bin/tts-backend"]
