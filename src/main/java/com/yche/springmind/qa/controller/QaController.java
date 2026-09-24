package com.yche.springmind.qa.controller;

import com.yche.springmind.qa.model.dto.AskQuestionRequest;
import com.yche.springmind.qa.model.vo.AskQuestionResponse;
import com.yche.springmind.qa.service.QaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供基于指定知识组和会话上下文进行知识问答的 HTTP 接口。
 *
 * <p>位于接口层：负责接收 HTTP 请求、触发参数校验并调用业务服务；业务规则由 Service 层统一维护。</p>
 */
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;

    /**
     * 创建并初始化 {@link QaController}，保存该组件运行所需的依赖与配置。
     *
     * @param qaService 方法参数 {@code qaService}
     */
    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    /**
     * 处理 {@code askQuestion} 对应的 HTTP 请求，并将业务结果封装为统一响应。
     *
     * @param askQuestionRequest ask问题请求参数
     * @param request 已经通过控制器基础校验的请求对象
     * @return 方法执行结果，具体结构由返回类型 {@code AskQuestionResponse} 表示
     */
    @PostMapping("/ask")
    public AskQuestionResponse askQuestion(
            @Valid @RequestBody AskQuestionRequest askQuestionRequest,
            HttpServletRequest request
    ) {
        return qaService.ask(request, askQuestionRequest);
    }
}
