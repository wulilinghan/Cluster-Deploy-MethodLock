package top.b0x0.methoddistributedlock.aspect;

import cn.hutool.core.thread.NamedThreadFactory;
import cn.hutool.system.SystemUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.b0x0.methoddistributedlock.annotion.MethodDistributedLock;

import java.lang.reflect.Method;
import java.util.concurrent.*;

/**
 * 防止定时任务重复执行切面
 *
 * @author TANG
 * @date 2021-04-27
 */
@Component
@Aspect
public class MethodLockAspect {
    private static final Logger log = LoggerFactory.getLogger(MethodLockAspect.class);

    @Pointcut("@annotation(top.b0x0.methoddistributedlock.annotion.MethodDistributedLock)")
    public void methodLockPointCut() {
    }

    private static final ExecutorService EXECUTOR_SERVICE;

    static {
        NamedThreadFactory namedThreadFactory = new NamedThreadFactory("lock-pool-", false);
        EXECUTOR_SERVICE = new ThreadPoolExecutor(
                3,
                20,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(500),
                namedThreadFactory);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    ConfigurableApplicationContext configurableApplicationContext;

    @Around("methodLockPointCut()")
    public Object doAroundMethod(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
//        System.out.println(" 1. 切面开始执行.... ");
        Method method = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod();
        MethodDistributedLock taskLock = method.getDeclaredAnnotation(MethodDistributedLock.class);
        if (taskLock == null) {
            return proceedingJoinPoint.proceed();
        }
        String resource = taskLock.resource();
        long expirationTime = taskLock.expirationTime();
        TimeUnit expirationTimeUnit = taskLock.timeUnit();

        String lockKey = "";
        if (StringUtils.hasText(resource)) {
            lockKey = "method_distributedLock:" + resource;
        } else {
            lockKey = "method_distributedLock:" + method.getDeclaringClass().getName() + ":" + method.getName();
        }
        ConfigurableEnvironment env = configurableApplicationContext.getEnvironment();
        String appName = env.getProperty("spring.application.name");
        String port = env.getProperty("server.port");
        String lockedVal = appName + "-" + port;

        // 第二种方式
        //使用redisson
        RLock redissonLock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
//            System.out.println(" 2. 开始获取锁... ");
/*
            // 第一种方式
            // setIfAbsent 在redis cluster模式下master宕机 数据还没同步到slave 会重复获取锁
            Boolean setIfAbsent = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockedVal, expirationTime, expirationTimeUnit);
            if (setIfAbsent == null) {
                throw new RuntimeException("setIfAbsent Acquiring lock exception");
            }
*/

            // 设置获取锁等待时间以及锁的有效时间
            redissonLock.tryLock(0, expirationTime, expirationTimeUnit);
            // 检查当前线程是否获得此锁
            locked = redissonLock.isHeldByCurrentThread();

            System.out.println("locked = " + locked);
        } catch (Exception e) {
            log.error("获取锁异常: {}", e.getMessage());
            return null;
        }

//        System.out.println(" 3. 开始校验是否执行任务... ");

        // 获得锁
        if (Boolean.FALSE.equals(locked)) {
            log.info("任务已经被其它实例执行，本实例跳过执行：{}", lockKey);
            return null;
        }

        try {
            log.info("-------------------------------------------------------------------------------");
            log.info("当前线程:[{}]，实例IP: [{}]，获取锁结果: [{}]，lockKey: [{}]，设置超时时间为：{} {}",
                    getServerIp(), Thread.currentThread().getName(), locked, lockKey, expirationTime, expirationTimeUnit);
            Future<Object> future = EXECUTOR_SERVICE.submit(() -> {
                // 执行原方法
                Object obj = null;
                try {
                    // 执行原方法
                    obj = proceedingJoinPoint.proceed();
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
                return obj;
            });
            // 超时处理
            return future.get(expirationTime, expirationTimeUnit);
        } catch (TimeoutException ex) {
            log.error("{} 运行超时", lockKey, ex);
            return null;
        } finally {
            // 释放锁
//            stringRedisTemplate.delete(lockKey);

            redissonLock.unlock();

            log.info("释放锁: {}", lockKey);
        }
    }


    /**
     * 获取服务器IP地址
     *
     * @return /
     */
    private static String getServerIp() {
        return SystemUtil.getHostInfo().getAddress();
    }
}
