package com.codelens.common.models;

/**
 * 架构分层枚举
 * 标识类在经典分层架构中所属的层次。
 */
public enum ArchitectureLayer {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    HANDLER,
    CONFIG,
    CLIENT,
    MODEL,
    UTIL,
    UNKNOWN
}
