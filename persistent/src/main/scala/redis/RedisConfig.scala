package redis

import com.typesafe.config.{Config, ConfigFactory}

/** Configuration for the Redis caching layer. */
case class RedisConfig(
  host: String,
  port: Int,
  database: Int,
  password: Option[String],
  timeout: Int,
  maxTotal: Int,
  maxIdle: Int,
  minIdle: Int
)

object RedisConfig {

  /** Loads Redis configuration from the default application.conf. */
  def fromConfig(config: Config = ConfigFactory.load()): RedisConfig = {
    val redisConfig = config.getConfig("redis")
    RedisConfig(
      host = redisConfig.getString("host"),
      port = redisConfig.getInt("port"),
      database = redisConfig.getInt("database"),
      password = if (redisConfig.hasPath("password") && redisConfig.getString("password") != null) {
        Some(redisConfig.getString("password"))
      } else None,
      timeout = redisConfig.getInt("timeout"),
      maxTotal = redisConfig.getInt("pool.maxTotal"),
      maxIdle = redisConfig.getInt("pool.maxIdle"),
      minIdle = redisConfig.getInt("pool.minIdle")
    )
  }
}
