package database

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

/** Runs Flyway database migrations against a JDBC data source.
  *
  * This keeps schema evolution explicit and versioned instead of relying on
  * Slick's runtime schema.create, which is only suitable for quick tests.
  */
object FlywayMigrationRunner {

  /** Runs pending migrations for the given JDBC connection.
    *
    * @param url      JDBC URL
    * @param user     database user
    * @param password database password
    * @param schemas  optional schemas to manage
    * @return the number of migrations applied
    */
  def migrate(
    url: String,
    user: String,
    password: String,
    schemas: Seq[String] = Seq.empty
  ): Int = {
    val config: FluentConfiguration = Flyway.configure()
      .dataSource(url, user, password)
      .locations("db/migration")
      .baselineOnMigrate(true)

    val configured = if (schemas.nonEmpty) {
      config.schemas(schemas: _*)
    } else {
      config
    }

    val flyway = configured.load()
    val result = flyway.migrate()
    result.migrationsExecuted
  }

  /** Validates the current schema against the available migrations. */
  def validate(url: String, user: String, password: String): Unit = {
    Flyway.configure()
      .dataSource(url, user, password)
      .locations("db/migration")
      .load()
      .validate()
  }

  /** Drops all managed objects then re-runs all migrations from scratch.
    *
    * Intended for use in integration tests against a real database so the
    * schema is always in a clean, known state between test runs.
    */
  def cleanAndMigrate(url: String, user: String, password: String): Int = {
    val flyway = Flyway.configure()
      .dataSource(url, user, password)
      .locations("db/migration")
      .cleanDisabled(false)
      .load()
    flyway.clean()
    flyway.migrate().migrationsExecuted
  }
}
