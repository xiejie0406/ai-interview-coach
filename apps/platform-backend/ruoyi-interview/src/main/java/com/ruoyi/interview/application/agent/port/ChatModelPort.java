package com.ruoyi.interview.application.agent.port;

/** 厂商 SDK、Spring AI/LangChain4j 类型只能在 adapter 实现内出现。 */
@FunctionalInterface
public interface ChatModelPort {

    ChatModelResult execute(ChatModelRequest request, InvocationContext context);
}
