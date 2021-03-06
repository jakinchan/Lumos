package club.javafan.blog.web.aop;

import club.javafan.blog.common.util.RedisUtil;
import com.alibaba.fastjson.JSONObject;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static club.javafan.blog.common.constant.RedisKeyConstant.CS_PAGE_VIEW;
import static club.javafan.blog.common.constant.RedisKeyConstant.EXCEPTION_AMOUNT;
import static club.javafan.blog.common.util.DateUtils.getToday;
import static java.util.Objects.isNull;

/**
 * @author 敲代码的长腿毛欧巴(博客)
 * @date 2019/12/10 22:57
 * @desc 日志处理和异常处理切面。主要作用打印入参和返回结果。
 * 也可以捕获异常。有条件的话可以吧捕获的异常做异常上报
 */
@Aspect
@Component
public class LoggerExceptionAop {
    /**
     * redis 工具类
     */
    @Autowired
    private RedisUtil redisUtil;
    /**
     * 日志
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final Long EXIST_TIME = 31 * 24 * 60 * 60L;
    @Pointcut(value = "execution(public * club.javafan.blog.service.impl.*.*(..))")
    private void servicePointCut() {
    }

    @Pointcut(value = "execution(public * club.javafan.blog.common.mail.impl.*.*(..))")
    private void commonPointCut() {
    }

    @Pointcut(value = "execution(public * club.javafan.blog.worker..*.*(..))")
    private void workerPointCut() {
    }

    @Pointcut(value = "execution(public * club.javafan.blog.web.controller..*.*(..))")
    private void controllerPoint() {
    }

    @Before(value = "controllerPoint()||servicePointCut()||commonPointCut()||workerPointCut()")
    public void before(JoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        StringBuilder log = new StringBuilder();
        log.append(className).append("@")
                .append(methodName)
                .append(" , params: ");
        Object[] args = joinPoint.getArgs();
        //过滤掉request请求和response 避免异步请求出错,同时过滤MultipartFile
        List<Object> logArgs = Stream.of(args)
                .filter(arg -> (!(arg instanceof HttpServletRequest) && !(arg instanceof HttpServletResponse))
                        && !(arg instanceof MultipartFile))
                .collect(Collectors.toList());
        log.append(JSONObject.toJSONString(logArgs)).toString();
        logger.info(log.toString());
    }

    @AfterReturning(value = "controllerPoint()||servicePointCut()||workerPointCut()", returning = "returnObj")
    public void afterReturn(Object returnObj) {
        if (isNull(returnObj)) {
            return;
        }
        logger.info("club.javafan.blog return result: {}", JSONObject.toJSONString(returnObj));
    }

    @AfterThrowing(value = "controllerPoint()||servicePointCut()||commonPointCut()||workerPointCut()", throwing = "e")
    public void afterThrowing(Throwable e) {
        //统计今日异常数
        recordTodayCount(EXCEPTION_AMOUNT);
        logger.error("club.javafan.blog error : {}", e);
    }

    /**
     * 记录今天的数量并且设置过期时间
     *
     * @param redisKey
     */
    private void recordTodayCount(String redisKey) {
        String key = redisKey + getToday();
        if (!redisUtil.hasKey(key)) {
            redisUtil.set(key, 0, EXIST_TIME);
        }
        redisUtil.incr(key);
    }

    @Around(value = "controllerPoint()||servicePointCut()||commonPointCut()||workerPointCut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        //统计今日执行的总次数
        recordTodayCount(CS_PAGE_VIEW);
        String className = proceedingJoinPoint.getTarget().getClass().getName();
        String methodName = proceedingJoinPoint.getSignature().getName();
        Long begin = System.currentTimeMillis();
        StringBuilder log = new StringBuilder(className + "." + methodName + " :");
        Object result;
        try {
            result = proceedingJoinPoint.proceed();
        } catch (Throwable e) {
            throw e;
        }
        Long end = System.currentTimeMillis();
        log.append("执行时间: ")
                .append(end - begin)
                .append(" ms");
        logger.info(log.toString());
        return result;
    }


}