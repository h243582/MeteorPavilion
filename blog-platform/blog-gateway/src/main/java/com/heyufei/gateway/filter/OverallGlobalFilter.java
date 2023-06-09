package com.heyufei.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.heyufei.common.util.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定义一个全局过滤器，模拟统一认证，Spring Cloud Gateway规范中，要求所有全局过滤器必须实现GlobalFilter接口
 *
 * @author HeYuFei
 * @since 2023-04-07  17:16
 */
@Component
@Slf4j
@RefreshScope
public class OverallGlobalFilter implements GlobalFilter, Ordered {
    /**
     * 白名单
     */
    private static final List<String> white_List = Arrays.asList("/demo", "/logout", "/user/admin/login");
    /**
     * redis中的token键开头
     */
    private static final String Token_Redis_Key_Starts = "Authorization:";
    /**
     * redis中的token值开头
     */
    private static final String Token_Redis_Value_Starts = "Heyufei:";
    /**
     * 请求头中的token键
     */
    private static final String Token_Head_Key = "Authorization";


    /**
     * 返回到登录地址
     */
    private static final String PORTAL_URL = "https://localhost:8001/api/v1/user/admin/login";
    @Value(value = "${token.secretKey}")
    private String secretKey;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 对请求进行过滤
     *
     * @param exchange （网管层面的交换器，通过此对象获取请求和响应对象）
     * @param chain    过滤器链（这个过滤器链中包含0个或多个过滤器）
     * @return Mono是 WebFlux技术中的0个或1个响应序列
     */
    @SneakyThrows
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //拿到当前请求地址
        String path = exchange.getRequest().getURI().getPath();
        log.info("网关登录效验，地址：{}", path);

        //获取请求和响应对象
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // 判断是否登录
        AtomicBoolean isLogin = new AtomicBoolean(false);

        //获取请求头
        String authorization = request.getHeaders().getFirst(Token_Head_Key);

        if (authorization != null && authorization.startsWith(Token_Redis_Value_Starts)) {
            //解析出对象
            Claims claims = JwtUtils.getTokenBody(authorization, secretKey);
            //解析后转对象
            JSONObject sub = JSONObject.parseObject(claims.get("sub").toString());
            //根据对象的id拿到redis的值
            String token = (String) redisTemplate.opsForValue().get(Token_Redis_Key_Starts + sub.get("id"));

            if (authorization.equals(token)) {
                //如果请求头和redis的头对得上
                //登录成功
                isLogin.set(true);
            }
        }

        // 这边添加一个延时， 等待获取到session
        Thread.sleep(200);


        //判断白名单中是否包含当前地址
        boolean isWhiteUrl = white_List.stream().anyMatch(path::endsWith);
        //如果已登录 或者 url在白名单中 就直接放行
        if (isLogin.get() || isWhiteUrl) {
            return chain.filter(exchange);
        }
        //未放行的执行下面的
        response.setStatusCode(HttpStatus.SEE_OTHER);
        response.getHeaders().set(HttpHeaders.LOCATION, PORTAL_URL);
        response.getHeaders().add("Content-Type", "text/plain;charset=UTF-8");
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        //数值越小,优先级越高
        return Ordered.HIGHEST_PRECEDENCE;
    }
}