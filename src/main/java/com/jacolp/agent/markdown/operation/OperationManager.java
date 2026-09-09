package com.jacolp.agent.markdown.operation;

import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.ReplaceResult;
import com.jacolp.agent.markdown.model.SectionNodeRef;

/**
 * 在内存中管理等待确认的 Markdown 替换提案。
 */
public final class OperationManager {

    private final MarkdownManager markdownManager;

    private final ConcurrentMap<UUID, OperationState> operations = new ConcurrentHashMap<>();

    private final ThreadLocal<CaptureState> currentCapture = new ThreadLocal<>();

    /**
     * 创建操作管理器并绑定 Markdown 管理器。
     *
     * @param markdownManager 执行实际替换的 Markdown 管理器
     */
    public OperationManager(MarkdownManager markdownManager) {
        this.markdownManager = Objects.requireNonNull(markdownManager, "markdownManager cannot be null");
    }

    /**
     * 创建一个等待确认的替换提案。
     *
     * @param section 目标上下文和节点
     * @param originalText 提案中的精确匹配文本
     * @param newText 替换文本；允许为空
     * @return 状态为 {@link OperationStatus#PENDING} 的操作快照
     */
    public Operation create(SectionNodeRef section, String originalText, String newText) {
        Objects.requireNonNull(section, "section cannot be null");
        Objects.requireNonNull(originalText, "originalText cannot be null");
        Objects.requireNonNull(newText, "newText cannot be null");
        if (originalText.isEmpty()) {
            // 与 MarkdownManager 一致，不允许空字符串作为无限匹配目标。
            throw new MarkdownReplacementException("originalText must not be empty");
        }

        // 创建提案前确认节点仍存在，尽早拒绝过期的章节引用。
        if (!this.markdownManager.getEntity(section.getDocumentId()).getNodes().containsKey(section.getNodeNumber())) {
            throw new SectionNotFoundException(section.getDocumentId(), section.getNodeNumber());
        }

        UUID opId = UUID.randomUUID();
        OperationState state = new OperationState(opId, section, originalText, newText);
        this.operations.put(opId, state);
        CaptureState capture = this.currentCapture.get();
        if (capture != null) {
            capture.operationIds.add(opId);
        }
        return state.snapshot();
    }

    /**
     * 开启一次请求级操作捕获范围。
     *
     * <p>捕获范围只收集当前线程在范围内创建的提案，避免并发聊天请求相互污染响应。</p>
     *
     * @return 请求操作捕获范围
     */
    public RequestScope capture() {
        CaptureState previous = this.currentCapture.get();
        CaptureState current = new CaptureState();
        this.currentCapture.set(current);
        return new RequestScope(current, previous);
    }

    /**
     * 将内部操作转换为聊天响应需要的标题路径。
     *
     * @param operation 操作快照
     * @return 操作对应的 sectionText
     */
    public String sectionText(Operation operation) {
        Objects.requireNonNull(operation, "operation cannot be null");
        return this.markdownManager.getSectionText(
                operation.getSection().getDocumentId(), operation.getSection().getNodeNumber());
    }

    /**
     * 获取操作的最新不可变快照。
     *
     * @param opId 操作 UUID
     * @return 当前操作快照
     * @throws OperationNotFoundException 操作不存在或已失效时抛出
     */
    public Operation get(UUID opId) {
        Objects.requireNonNull(opId, "opId cannot be null");
        OperationState state = this.operations.get(opId);
        if (state == null) {
            // 内存操作不存在时明确报告错误，不返回空对象掩盖问题。
            throw new OperationNotFoundException(opId);
        }
        return state.snapshot();
    }

    /**
     * 原子确认并执行一个等待中的替换提案。
     *
     * @param opId 操作 UUID
     * @return 包含新 Markdown 快照的替换结果
     * @throws OperationNotFoundException 操作不存在或已失效时抛出
     * @throws OperationStateException 操作已经离开 {@code PENDING} 时抛出
     */
    public ReplaceResult confirm(UUID opId) {
        Objects.requireNonNull(opId, "opId cannot be null");
        OperationState state = this.operations.get(opId);
        if (state == null) {
            // 未知 OpId 没有可确认的提案。
            throw new OperationNotFoundException(opId);
        }
        if (!state.transition(OperationStatus.PENDING, OperationStatus.EXECUTING)) {
            // CAS 失败说明其他请求已经确认或取消，当前请求不得重复执行。
            throw new OperationStateException(opId, state.status(), "confirmed");
        }

        try {
            // EXECUTING 状态只允许这一条线程调用 replace。
            ReplaceResult result = this.markdownManager.replace(
                    state.section(), state.originalText(), state.newText());
            // replace 返回表示持久化和上下文刷新均成功，随后进入 COMPLETED。
            state.transition(OperationStatus.EXECUTING, OperationStatus.COMPLETED);
            return result;
        }
        catch (RuntimeException exception) {
            // 任一替换或持久化异常都会记录 FAILED，再把原异常交给调用方处理。
            state.transition(OperationStatus.EXECUTING, OperationStatus.FAILED);
            throw exception;
        }
    }

    /**
     * 取消等待中的操作，不修改 Markdown 内容。
     *
     * @param opId 操作 UUID
     * @return 状态为 {@link OperationStatus#CANCELLED} 的操作快照
     * @throws OperationNotFoundException 操作不存在或已失效时抛出
     * @throws OperationStateException 操作已经离开 {@code PENDING} 时抛出
     */
    public Operation cancel(UUID opId) {
        Objects.requireNonNull(opId, "opId cannot be null");
        OperationState state = this.operations.get(opId);
        if (state == null) {
            // 未知 OpId 没有可取消的提案。
            throw new OperationNotFoundException(opId);
        }
        if (!state.transition(OperationStatus.PENDING, OperationStatus.CANCELLED)) {
            // CAS 失败表示确认或取消已经先一步完成，禁止重复离开 PENDING。
            throw new OperationStateException(opId, state.status(), "cancelled");
        }
        return state.snapshot();
    }

    MarkdownManager markdownManager() {
        return this.markdownManager;
    }

    ConcurrentMap<UUID, OperationState> operations() {
        return this.operations;
    }

    private void discard(List<UUID> operationIds) {
        operationIds.forEach(this.operations::remove);
    }

    private static final class CaptureState {

        private final List<UUID> operationIds = new ArrayList<>();
    }

    /**
     * 管理一次聊天请求中新建的操作提案。
     */
    public final class RequestScope implements AutoCloseable {

        private final CaptureState state;

        private final CaptureState previous;

        private boolean finished;

        private RequestScope(CaptureState state, CaptureState previous) {
            this.state = state;
            this.previous = previous;
        }

        /**
         * 获取本范围内按创建顺序排列的最新操作快照。
         *
         * @return 当前请求创建的操作
         */
        public List<Operation> operations() {
            return this.state.operationIds.stream()
                    .map(OperationManager.this.operations::get)
                    .filter(Objects::nonNull)
                    .map(OperationState::snapshot)
                    .toList();
        }

        /**
         * 提交本范围创建的操作，使其可供当前响应转换。
         */
        public void commit() {
            ensureOpen();
            this.finished = true;
        }

        /**
         * 丢弃本范围创建的操作，通常用于聊天失败或没有最终回答的情况。
         */
        public void discard() {
            ensureOpen();
            OperationManager.this.discard(this.state.operationIds);
            this.finished = true;
        }

        @Override
        public void close() {
            if (!this.finished) {
                OperationManager.this.discard(this.state.operationIds);
            }
            if (OperationManager.this.currentCapture.get() == this.state) {
                if (this.previous == null) {
                    OperationManager.this.currentCapture.remove();
                }
                else {
                    OperationManager.this.currentCapture.set(this.previous);
                }
            }
            this.finished = true;
        }

        private void ensureOpen() {
            if (this.finished) {
                throw new IllegalStateException("operation capture scope is already closed");
            }
        }
    }
}
