package io.airbyte.integrations.io.airbyte.integration_tests.sources;

public class PostgresSourceAcceptanceLegacyCtidTest extends PostgresSourceAcceptanceTest{

  @Override
  protected String getServerImageName() {
    return "postgres:13-alpine";
  }
}
