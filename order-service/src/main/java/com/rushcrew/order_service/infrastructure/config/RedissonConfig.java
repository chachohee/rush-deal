package com.rushcrew.order_service.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class RedissonConfig {

	@Value("${spring.data.redis.host:localhost}")
	private String redisHost;

	@Value("${spring.data.redis.port:6381}")
	private int redisPort;

	@Value("${spring.data.redis.password:}")
	private String redisPassword;

	@Bean
	public RedissonClient redissonClient() {
		Config config = new Config();

		var singleServerConfig = config.useSingleServer()
			.setAddress("redis://" + redisHost + ":" + redisPort)
			.setConnectionPoolSize(30)
			.setConnectionMinimumIdleSize(10)
			.setRetryAttempts(3)
			.setRetryInterval(1500);

		if (redisPassword != null && !redisPassword.trim().isEmpty()) {
			singleServerConfig.setPassword(redisPassword);
		}

		return Redisson.create(config);
	}
}
