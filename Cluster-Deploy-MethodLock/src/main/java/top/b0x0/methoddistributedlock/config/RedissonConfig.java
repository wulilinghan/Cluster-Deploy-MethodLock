package top.b0x0.methoddistributedlock.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

/**
 * redisson config
 *
 * @author TANG
 * @since 2021-07-27
 * @since JDK1.8
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379");

        //集群模式,集群节点的地址须使用“redis://”前缀，否则将会报错。
        //此例集群为3主3从
/*
        config.useClusterServers()
                .setScanInterval(2000)
                .addNodeAddress(
                        "redis://192.168.1.106:9001"
                        , "redis://192.168.1.106:9002"
                        , "redis://192.168.1.106:9003"
                        , "redis://192.168.1.106:9004"
                        , "redis://192.168.1.106:9005"
                        , "redis://192.168.1.106:9006");
*/

/*
        try {
            config = Config.fromYAML(new ClassPathResource("redisson-cluster.yml").getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
*/

        return Redisson.create(config);
    }
}
