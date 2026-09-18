package com.posgateway.aml.integration.fix;

import com.posgateway.aml.dto.market.FixSurveillanceDtos.FixSessionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import quickfix.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Component
public class FixEngineLifecycle implements SmartLifecycle {
    private final boolean enabled;
    private final Path settingsPath;
    private final FixMarketDataApplication application;
    private volatile boolean running;
    private volatile SessionSettings settings;
    private Acceptor acceptor;
    private Initiator initiator;

    public FixEngineLifecycle(
            @Value("${fix.enabled:false}") boolean enabled,
            @Value("${fix.settings-path:}") String settingsPath,
            FixMarketDataApplication application) {
        this.enabled = enabled;
        this.settingsPath = settingsPath == null || settingsPath.isBlank() ? null : Path.of(settingsPath);
        this.application = application;
    }

    @Override
    public synchronized void start() {
        if (running || !enabled) return;
        if (settingsPath == null || !Files.isRegularFile(settingsPath)) {
            throw new IllegalStateException("fix.settings-path must reference a readable QuickFIX/J configuration");
        }
        try (InputStream input = Files.newInputStream(settingsPath)) {
            settings = new SessionSettings(input);
            String connectionType = validate(settings);
            application.configure(settings);
            MessageStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new FileLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();
            if ("acceptor".equals(connectionType)) {
                acceptor = new ThreadedSocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);
                acceptor.start();
            } else {
                initiator = new SocketInitiator(application, storeFactory, settings, logFactory, messageFactory);
                initiator.start();
            }
            running = true;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not start FIX engine", exception);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        if (acceptor != null) acceptor.stop();
        if (initiator != null) initiator.stop();
        acceptor = null;
        initiator = null;
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    public List<FixSessionResponse> sessions(Long allowedPspId) {
        SessionSettings current = settings;
        if (current == null) return List.of();
        List<FixSessionResponse> result = new ArrayList<>();
        Iterator<SessionID> iterator = current.sectionIterator();
        while (iterator.hasNext()) {
            SessionID sessionId = iterator.next();
            try {
                Long pspId = application.pspId(sessionId);
                if (allowedPspId != null && allowedPspId > 0 && !allowedPspId.equals(pspId)) continue;
                Session session = Session.lookupSession(sessionId);
                result.add(new FixSessionResponse(sessionId.toString(), pspId,
                        current.getString(sessionId, "ConnectionType"), enabled,
                        session != null && session.isLoggedOn(),
                        session == null ? null : session.getExpectedSenderNum(),
                        session == null ? null : session.getExpectedTargetNum()));
            } catch (Exception exception) {
                throw new IllegalStateException("Could not read FIX session status for " + sessionId, exception);
            }
        }
        return List.copyOf(result);
    }

    private String validate(SessionSettings candidate) throws ConfigError {
        Iterator<SessionID> iterator = candidate.sectionIterator();
        String commonConnectionType = null;
        int count = 0;
        while (iterator.hasNext()) {
            count++;
            SessionID sessionId = iterator.next();
            String connectionType = required(candidate, sessionId, "ConnectionType").toLowerCase(Locale.ROOT);
            if (!connectionType.equals("acceptor") && !connectionType.equals("initiator")) {
                throw new ConfigError("ConnectionType must be acceptor or initiator for " + sessionId);
            }
            if (commonConnectionType != null && !commonConnectionType.equals(connectionType)) {
                throw new ConfigError("One FIX engine configuration cannot mix acceptor and initiator sessions");
            }
            commonConnectionType = connectionType;
            required(candidate, sessionId, "HokekaPspId");
            required(candidate, sessionId, "HokekaUsername");
            required(candidate, sessionId, "HokekaPassword");
            required(candidate, sessionId, "FileStorePath");
            required(candidate, sessionId, "FileLogPath");
            if (connectionType.equals("acceptor")) required(candidate, sessionId, "SocketAcceptPort");
            if (connectionType.equals("initiator")) {
                required(candidate, sessionId, "SocketConnectHost");
                required(candidate, sessionId, "SocketConnectPort");
            }
        }
        if (count == 0) throw new ConfigError("FIX settings file contains no sessions");
        return commonConnectionType;
    }

    private String required(SessionSettings candidate, SessionID sessionId, String key) throws ConfigError {
        if (!candidate.isSetting(sessionId, key)) throw new ConfigError(key + " is required for " + sessionId);
        String value = candidate.getString(sessionId, key);
        if (value == null || value.isBlank()) throw new ConfigError(key + " is blank for " + sessionId);
        return value;
    }
}
