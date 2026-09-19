package com.chm.myfinances.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Attaches a Logback {@link ListAppender} to one logger for the duration of a test, so unit tests
 * can assert on level + formatted message without a Spring context and independent of whatever
 * level the app is configured to log at (the logger is forced to {@code TRACE} while captured and
 * restored on {@link #close()}). Use in try-with-resources:
 *
 * <pre>{@code
 * try (LogCapture logs = LogCapture.of(AccountService.class)) {
 *   service.close(id);
 *   assertThat(logs.messagesAt(Level.INFO)).containsExactly("Account " + id + " closed");
 * }
 * }</pre>
 *
 * <p>Only sees events logged through the given logger itself (additivity is untouched, so events
 * still reach the console too).
 */
public final class LogCapture implements AutoCloseable {

  private final Logger logger;
  private final Level originalLevel;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private LogCapture(Logger logger) {
    this.logger = logger;
    this.originalLevel = logger.getLevel();
    logger.setLevel(Level.TRACE);
    appender.setContext(logger.getLoggerContext());
    appender.start();
    logger.addAppender(appender);
  }

  public static LogCapture of(Class<?> loggerClass) {
    return new LogCapture((Logger) LoggerFactory.getLogger(loggerClass));
  }

  /** Every captured event, in order. */
  public List<ILoggingEvent> events() {
    return List.copyOf(appender.list);
  }

  /** Captured events at exactly {@code level}. */
  public List<ILoggingEvent> eventsAt(Level level) {
    return appender.list.stream().filter(event -> event.getLevel().equals(level)).toList();
  }

  /** Formatted messages (arguments substituted, throwable excluded) at exactly {@code level}. */
  public List<String> messagesAt(Level level) {
    return eventsAt(level).stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
    logger.setLevel(originalLevel);
  }
}
