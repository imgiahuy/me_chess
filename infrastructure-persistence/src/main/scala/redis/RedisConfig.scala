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

  /** Default Redis configuration for use without config file. */
  def default: RedisConfig = RedisConfig(
    host = "localhost",
    port = 6379,
    database = 0,
    password = None,
    timeout = 5000,
    maxTotal = 10,
    maxIdle = 5,
    minIdle = 1
  )

  /** Loads Redis configuration from the default application.conf. */
  def fromConfig(config: Config = ConfigFactory.load()): RedisConfig = {
    if (config.hasPath("redis")) {
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
    } else {
      default
    }
  }
}
