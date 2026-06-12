package com.codelens.common.profile;

/**
 * 架构模式枚举。
 * 纯规则推断，不依赖 LLM。
 */
public enum ArchitecturePattern {
    /** 标准分层（Controller→Service→Repository） */
    LAYERED,
    /** 精简分层（Controller→Repository，无 Service 层） */
    LAYERED_NO_SERVICE,
    /** 命令查询分离 */
    CQRS,
    /** 六边形/端口适配器 */
    HEXAGONAL,
    /** 微服务（@FeignClient + @EnableFeignClients） */
    MICROSERVICE,
    /** 传统 Spring MVC */
    MVC,
    /** 事件驱动 */
    EVENT_DRIVEN,
    /** 非标准，无法归类 */
    CUSTOM,
    /** 数据不足，无法推断 */
    UNKNOWN;

    /**
     * 推断置信度。
     */
    public enum Confidence {
        HIGH, MEDIUM, LOW
    }
}
