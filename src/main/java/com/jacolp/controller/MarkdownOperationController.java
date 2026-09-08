package com.jacolp.controller;

import java.util.UUID;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import com.jacolp.agent.markdown.exception.MarkdownCursorException;
import com.jacolp.agent.markdown.exception.MarkdownException;
import com.jacolp.agent.markdown.exception.MarkdownPersistenceException;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.ReplaceResult;
import com.jacolp.agent.markdown.model.SectionReplaceProposal;
import com.jacolp.agent.markdown.model.SectionReplaceResult;
import com.jacolp.agent.markdown.operation.Operation;
import com.jacolp.agent.markdown.operation.OperationManager;
import com.jacolp.agent.markdown.operation.OperationNotFoundException;
import com.jacolp.agent.markdown.operation.OperationStateException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提供替换提案的查询、确认和取消接口。
 */
@RestController
@RequestMapping("/api/markdown-operations")
public class MarkdownOperationController {

    private final OperationManager operationManager;

    /**
     * 创建替换操作控制器。
     *
     * @param operationManager 替换提案管理器
     */
    public MarkdownOperationController(OperationManager operationManager) {
        this.operationManager = operationManager;
    }

    /**
     * 查询替换提案当前状态。
     *
     * @param opId 操作 UUID
     * @return 替换提案摘要
     */
    @GetMapping("/{opId}")
    public SectionReplaceProposal get(@PathVariable("opId") UUID opId) {
        try {
            return SectionReplaceProposal.from(this.operationManager.get(opId));
        }
        catch (MarkdownException exception) {
            throw toHttpException(exception);
        }
    }

    /**
     * 确认并执行替换提案。
     *
     * @param opId 操作 UUID
     * @return 替换完成后的结果摘要
     */
    @PostMapping("/{opId}/confirm")
    public SectionReplaceResult confirm(@PathVariable("opId") UUID opId) {
        try {
            ReplaceResult result = this.operationManager.confirm(opId);
            Operation operation = this.operationManager.get(opId);
            return SectionReplaceResult.from(operation, result);
        }
        catch (MarkdownException exception) {
            throw toHttpException(exception);
        }
    }

    /**
     * 取消尚未确认的替换提案。
     *
     * @param opId 操作 UUID
     * @return 已取消的替换提案摘要
     */
    @PostMapping("/{opId}/cancel")
    public SectionReplaceProposal cancel(@PathVariable("opId") UUID opId) {
        try {
            return SectionReplaceProposal.from(this.operationManager.cancel(opId));
        }
        catch (MarkdownException exception) {
            throw toHttpException(exception);
        }
    }

    private static ResponseStatusException toHttpException(MarkdownException exception) {
        HttpStatus status;
        if (exception instanceof OperationNotFoundException
                || exception instanceof MarkdownContextNotFoundException
                || exception instanceof SectionNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        }
        else if (exception instanceof OperationStateException) {
            status = HttpStatus.CONFLICT;
        }
        else if (exception instanceof MarkdownReplacementException
                || exception instanceof MarkdownCursorException) {
            status = HttpStatus.BAD_REQUEST;
        }
        else if (exception instanceof MarkdownPersistenceException) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return new ResponseStatusException(status, exception.getMessage(), exception);
    }
}
