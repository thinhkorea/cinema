package com.example.cinema.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class MovieDiscoveryAiServiceLauncher {

    private static final Logger log = LoggerFactory.getLogger(MovieDiscoveryAiServiceLauncher.class);

    private final boolean enabled;
    private final String pythonCommand;
    private final String serviceDirectory;
    private final String host;
    private final int port;
    private final String healthUrl;
    private final boolean warmupEnabled;
    private final String warmupUrl;
    private final String cacheRoot;
    private Process process;

    public MovieDiscoveryAiServiceLauncher(
            @Value("${cinema.movie-discovery.ai-autostart-enabled:true}") boolean enabled,
            @Value("${cinema.movie-discovery.ai-python-command:python}") String pythonCommand,
            @Value("${cinema.movie-discovery.ai-service-dir:../ai-services/movie-discovery}") String serviceDirectory,
            @Value("${cinema.movie-discovery.ai-host:127.0.0.1}") String host,
            @Value("${cinema.movie-discovery.ai-port:8002}") int port,
            @Value("${cinema.movie-discovery.ai-health-url:http://localhost:8002/health}") String healthUrl,
            @Value("${cinema.movie-discovery.ai-warmup-enabled:true}") boolean warmupEnabled,
            @Value("${cinema.movie-discovery.ai-warmup-url:http://localhost:8002/warmup}") String warmupUrl,
            @Value("${cinema.movie-discovery.ai-cache-root:D:/AI_CACHE}") String cacheRoot
    ) {
        this.enabled = enabled;
        this.pythonCommand = pythonCommand;
        this.serviceDirectory = serviceDirectory;
        this.host = host;
        this.port = port;
        this.healthUrl = healthUrl;
        this.warmupEnabled = warmupEnabled;
        this.warmupUrl = warmupUrl;
        this.cacheRoot = cacheRoot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAiService() {
        if (!enabled) {
            log.info("[MovieDiscoveryAI] Auto-start disabled.");
            return;
        }
        if (isHealthy(Duration.ofMillis(800))) {
            log.info("[MovieDiscoveryAI] Service already running at {}", healthUrl);
            warmupInBackground();
            return;
        }

        Path workingDirectory = resolveServiceDirectory();
        if (workingDirectory == null) {
            log.warn("[MovieDiscoveryAI] Service directory not found: {}", serviceDirectory);
            return;
        }

        try {
            String resolvedPythonCommand = resolvePythonCommand(workingDirectory);
            List<String> command = List.of(
                    resolvedPythonCommand,
                    "-m",
                    "uvicorn",
                    "app:app",
                    "--host",
                    host,
                    "--port",
                    String.valueOf(port)
            );
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            configureAiCacheEnvironment(builder);
            process = builder.start();
            streamLogs(process);

            if (process.waitFor(2, TimeUnit.SECONDS)) {
                log.warn("[MovieDiscoveryAI] Service stopped immediately with exit code {}. Run pip install -r ai-services/movie-discovery/requirements.txt first.", process.exitValue());
                process = null;
                return;
            }
            log.info("[MovieDiscoveryAI] Started service at http://{}:{} using {}", host, port, resolvedPythonCommand);
            warmupInBackground();
        } catch (IOException ex) {
            log.warn("[MovieDiscoveryAI] Could not start AI service. Check Python and requirements. Cause: {}", ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[MovieDiscoveryAI] Startup interrupted.");
        }
    }

    @PreDestroy
    public void stopAiService() {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private Path resolveServiceDirectory() {
        Path configuredPath = Paths.get(serviceDirectory);
        Path currentDirectory = Paths.get("").toAbsolutePath().normalize();
        List<Path> candidates = configuredPath.isAbsolute()
                ? List.of(configuredPath)
                : List.of(
                currentDirectory.resolve(configuredPath),
                currentDirectory.resolve("ai-services").resolve("movie-discovery"),
                currentDirectory.resolve("..").resolve("ai-services").resolve("movie-discovery")
        );

        return candidates.stream()
                .map(Path::normalize)
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(null);
    }

    private String resolvePythonCommand(Path workingDirectory) {
        if (pythonCommand != null && !pythonCommand.isBlank() && !"python".equalsIgnoreCase(pythonCommand.trim())) {
            return pythonCommand;
        }

        List<Path> candidates = List.of(
                workingDirectory.resolve(".venv").resolve("Scripts").resolve("python.exe"),
                workingDirectory.resolve(".venv").resolve("bin").resolve("python"),
                workingDirectory.resolve("venv").resolve("Scripts").resolve("python.exe"),
                workingDirectory.resolve("venv").resolve("bin").resolve("python")
        );

        return candidates.stream()
                .map(Path::normalize)
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .findFirst()
                .orElse(pythonCommand);
    }

    private void configureAiCacheEnvironment(ProcessBuilder builder) {
        if (cacheRoot == null || cacheRoot.isBlank()) {
            return;
        }

        Path root = Paths.get(cacheRoot).toAbsolutePath().normalize();
        Path huggingFaceHome = root.resolve("huggingface");
        Path huggingFaceHub = huggingFaceHome.resolve("hub");
        Path torchHome = root.resolve("torch");
        Path pipCache = root.resolve("pip");

        try {
            Files.createDirectories(huggingFaceHub);
            Files.createDirectories(torchHome);
            Files.createDirectories(pipCache);
        } catch (IOException ex) {
            log.warn("[MovieDiscoveryAI] Could not create AI cache folders under {}: {}", root, ex.getMessage());
        }

        builder.environment().put("HF_HOME", huggingFaceHome.toString());
        builder.environment().put("HUGGINGFACE_HUB_CACHE", huggingFaceHub.toString());
        builder.environment().put("TORCH_HOME", torchHome.toString());
        builder.environment().put("PIP_CACHE_DIR", pipCache.toString());
    }

    private void warmupInBackground() {
        if (!warmupEnabled) {
            return;
        }

        Thread warmupThread = new Thread(() -> {
            if (!waitUntilHealthy(Duration.ofMinutes(3))) {
                log.warn("[MovieDiscoveryAI] Warmup skipped because service did not become healthy.");
                return;
            }
            warmup();
        }, "movie-discovery-ai-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    private boolean waitUntilHealthy(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isHealthy(Duration.ofSeconds(2))) {
                return true;
            }
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void warmup() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(warmupUrl).openConnection();
            connection.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            connection.setReadTimeout((int) Duration.ofMinutes(10).toMillis());
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));

            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                log.info("[MovieDiscoveryAI] Warmup completed.");
            } else {
                log.warn("[MovieDiscoveryAI] Warmup returned HTTP {}", status);
            }
        } catch (IOException ex) {
            log.warn("[MovieDiscoveryAI] Warmup failed: {}", ex.getMessage());
        }
    }

    private boolean isHealthy(Duration timeout) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(healthUrl).openConnection();
            connection.setConnectTimeout((int) timeout.toMillis());
            connection.setReadTimeout((int) timeout.toMillis());
            connection.setRequestMethod("GET");
            return connection.getResponseCode() >= 200 && connection.getResponseCode() < 300;
        } catch (IOException ex) {
            return false;
        }
    }

    private void streamLogs(Process startedProcess) {
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(startedProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[MovieDiscoveryAI] {}", line);
                }
            } catch (IOException ex) {
                log.debug("[MovieDiscoveryAI] Log stream closed: {}", ex.getMessage());
            }
        }, "movie-discovery-ai-logs");
        logThread.setDaemon(true);
        logThread.start();
    }
}
