package redis

import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

/** Factory for creating Redis clients and connection pools. */
object RedisClientFactory {

  /** Creates a JedisPool from the given Redis configuration. */
  def createPool(config: RedisConfig): JedisPool = {
    val poolConfig = new JedisPoolConfig()
    poolConfig.setMaxTotal(config.maxTotal)
    poolConfig.setMaxIdle(config.maxIdle)
    poolConfig.setMinIdle(config.minIdle)
    poolConfig.setTestOnBorrow(true)
    poolConfig.setTestOnReturn(true)

    config.password match {
      case Some(pwd) => new JedisPool(poolConfig, config.host, config.port, config.timeout, pwd, config.database)
      case None => new JedisPool(poolConfig, config.host, config.port, config.timeout, null, config.database)
    }
  }

  /** Creates a standalone Jedis connection from the given configuration. */
  def createClient(config: RedisConfig): Jedis = {
    val jedis = new Jedis(config.host, config.port, config.timeout)
    config.password.foreach(jedis.auth)
    if (config.database != 0) jedis.select(config.database)
    jedis
  }
}
