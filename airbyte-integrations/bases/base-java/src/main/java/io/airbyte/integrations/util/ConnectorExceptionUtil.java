/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.util;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import io.airbyte.commons.exceptions.ConfigErrorException;
import io.airbyte.commons.exceptions.ConnectionErrorException;
import io.airbyte.integrations.base.errors.messages.ErrorMessage;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class defining methods for handling configuration exceptions in connectors.
 */
public class ConnectorExceptionUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorExceptionUtil.class);

  public static final String COMMON_EXCEPTION_MESSAGE_TEMPLATE = "Could not connect with provided configuration. Error: %s";
  static final String RECOVERY_CONNECTION_ERROR_MESSAGE =
      "We're having issues syncing from a Postgres replica that is configured as a hot standby server. " +
          "Please see https://docs.airbyte.com/integrations/sources/postgres/#sync-data-from-postgres-hot-standby-server for options and workarounds";

  public static final List<Integer> HTTP_AUTHENTICATION_ERROR_CODES = ImmutableList.of(401, 403);
  private static final List<Predicate<Throwable>> configErrorPredicates =
      List.of(getConfigErrorPredicate(), getConnectionErrorPredicate(),
          isRecoveryConnectionExceptionPredicate(), isUnknownColumnInFieldListException());

  public static boolean isConfigError(final Throwable e) {
    return configErrorPredicates.stream().anyMatch(predicate -> predicate.test(e));
  }

  public static String getDisplayMessage(final Throwable e) {
    if (e instanceof ConfigErrorException) {
      return ((ConfigErrorException) e).getDisplayMessage();
    } else if (e instanceof final ConnectionErrorException connEx) {
      return ErrorMessage.getErrorMessage(connEx.getStateCode(), connEx.getErrorCode(), connEx.getExceptionMessage(), connEx);
    } else if (isRecoveryConnectionExceptionPredicate().test(e)) {
      return RECOVERY_CONNECTION_ERROR_MESSAGE;
    } else if (isUnknownColumnInFieldListException().test(e)) {
      return e.getMessage();
    } else {
      return String.format(COMMON_EXCEPTION_MESSAGE_TEMPLATE, e.getMessage() != null ? e.getMessage() : "");
    }
  }

  /**
   * Returns the first instance of an exception associated with a configuration error (if it exists).
   * Otherwise, the original exception is returned.
   */
  public static Throwable getRootConfigError(final Exception e) {
    Throwable current = e;
    while (current != null) {
      if (ConnectorExceptionUtil.isConfigError(current)) {
        return current;
      } else {
        current = current.getCause();
      }
    }
    return e;
  }

  /**
   * Log all the exceptions, and rethrow the first. This is useful for e.g. running multiple futures
   * and waiting for them to complete/fail. Rather than combining them into a single mega-exception
   * (which works poorly in the UI), we just log all of them, and throw the first exception.
   * <p>
   * In most cases, all the exceptions will look very similar, so the user only needs to see the first
   * exception anyway. This mimics e.g. a for-loop over multiple tasks, where the loop would break on
   * the first exception.
   */
  public static <T extends Throwable> void logAllAndThrowFirst(final String initialMessage, final Collection<? extends T> throwables) throws T {
    if (!throwables.isEmpty()) {
      final String stacktraces = throwables.stream().map(ExceptionUtils::getStackTrace).collect(joining("\n"));
      LOGGER.error(initialMessage + stacktraces + "\nRethrowing first exception.");
      throw throwables.iterator().next();
    }
  }

  private static Predicate<Throwable> getConfigErrorPredicate() {
    return e -> e instanceof ConfigErrorException;
  }

  private static Predicate<Throwable> getConnectionErrorPredicate() {
    return e -> e instanceof ConnectionErrorException;
  }

  private static Predicate<Throwable> isRecoveryConnectionExceptionPredicate() {
    return e -> e instanceof SQLException && e.getMessage()
        .toLowerCase(Locale.ROOT)
        .contains("due to conflict with recovery");
  }

  private static Predicate<Throwable> isUnknownColumnInFieldListException() {
    return e -> e instanceof SQLSyntaxErrorException
        && e.getMessage()
            .toLowerCase(Locale.ROOT)
            .contains("unknown column")
        && e.getMessage()
            .toLowerCase(Locale.ROOT)
            .contains("in 'field list'");
  }

}
