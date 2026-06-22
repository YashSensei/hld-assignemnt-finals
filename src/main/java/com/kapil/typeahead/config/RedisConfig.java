package com.kapil.typeahead.config;

import com.kapil.typeahead.cache.RedisNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${app.redis.node2.host:localhost}")
    private String redisNode2Host;

    @Value("${app.redis.node2.port:6380}")
    private int redisNode2Port;

    @Value("${app.redis.node3.host:localhost}")
    private String redisNode3Host;

    @Value("${app.redis.node3.port:6381}")
    private int redisNode3Port;

    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory1() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort));
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory2() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisNode2Host, redisNode2Port));
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory3() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisNode3Host, redisNode3Port));
    }

    @Bean
    @Primary
    public StringRedisTemplate redisTemplate1(LettuceConnectionFactory redisConnectionFactory1) {
        return new StringRedisTemplate(redisConnectionFactory1);
    }

    @Bean
    public StringRedisTemplate redisTemplate2(LettuceConnectionFactory redisConnectionFactory2) {
        return new StringRedisTemplate(redisConnectionFactory2);
    }

    @Bean
    public StringRedisTemplate redisTemplate3(LettuceConnectionFactory redisConnectionFactory3) {
        return new StringRedisTemplate(redisConnectionFactory3);
    }

    @Bean
    public Map<RedisNode, StringRedisTemplate> redisTemplatesMap(
            StringRedisTemplate redisTemplate1,
            StringRedisTemplate redisTemplate2,
            StringRedisTemplate redisTemplate3
    ) {
        Map<RedisNode, StringRedisTemplate> map = new HashMap<>();
        map.put(new RedisNode("redis1", redisHost, redisPort), redisTemplate1);
        map.put(new RedisNode("redis2", redisNode2Host, redisNode2Port), redisTemplate2);
        map.put(new RedisNode("redis3", redisNode3Host, redisNode3Port), redisTemplate3);
        return map;
    }
}
