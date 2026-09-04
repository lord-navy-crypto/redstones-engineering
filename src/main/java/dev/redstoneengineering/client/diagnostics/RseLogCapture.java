package dev.redstoneengineering.client.diagnostics;

import dev.redstoneengineering.diagnostics.RseDiagnosticSeverity;
import dev.redstoneengineering.diagnostics.RseDiagnostics;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.Locale;

/** Client-process log tap that retains only events related to RSE. */
public final class RseLogCapture {
    private static final String APPENDER_NAME = "RSE-Diagnostics";
    private static boolean installed;

    private RseLogCapture() {
    }

    public static synchronized void install() {
        if (installed) return;

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        if (configuration.getAppender(APPENDER_NAME) != null) {
            installed = true;
            return;
        }

        RseAppender appender = new RseAppender();
        appender.start();
        configuration.addAppender(appender);
        LoggerConfig rootLogger = configuration.getRootLogger();
        rootLogger.addAppender(appender, Level.INFO, null);
        context.updateLoggers();
        installed = true;

        RseDiagnostics.record(
                RseDiagnosticSeverity.INFO,
                "diagnostics",
                "RSE diagnostic log capture enabled for this client session.",
                null
        );
    }

    private static final class RseAppender extends AbstractAppender {
        private RseAppender() {
            super(APPENDER_NAME, null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            if (event == null || event.getLevel().intLevel() > Level.INFO.intLevel()) return;

            String loggerName = event.getLoggerName() == null ? "unknown" : event.getLoggerName();
            String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
            Throwable thrown = event.getThrown();
            if (!isRseRelated(loggerName, message, thrown)) return;

            RseDiagnostics.record(mapSeverity(event.getLevel()), loggerName, message, thrown);
        }
    }

    private static RseDiagnosticSeverity mapSeverity(Level level) {
        if (level.intLevel() <= Level.ERROR.intLevel()) return RseDiagnosticSeverity.ERROR;
        if (level.intLevel() <= Level.WARN.intLevel()) return RseDiagnosticSeverity.WARN;
        return RseDiagnosticSeverity.INFO;
    }

    private static boolean isRseRelated(String loggerName, String message, Throwable throwable) {
        String normalizedLogger = loggerName.toLowerCase(Locale.ROOT);
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        if (normalizedLogger.startsWith("dev.redstoneengineering")
                || normalizedLogger.contains("redstoneengineering")
                || normalizedMessage.contains("redstone systems engineering")
                || normalizedMessage.contains("redstoneengineering")
                || normalizedMessage.contains("[rse]")) {
            return true;
        }

        Throwable cursor = throwable;
        int causeDepth = 0;
        while (cursor != null && causeDepth++ < 6) {
            for (StackTraceElement element : cursor.getStackTrace()) {
                if (element.getClassName().startsWith("dev.redstoneengineering")) return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
