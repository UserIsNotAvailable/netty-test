# Netty 专家级修炼课程交接文档

## 课程状态概览 (Status Overview)

### ✅ 已通关模块 (Completed)

#### 1. 核心架构篇 (Architecture)
*   **Reactor 模型**: Boss/Worker 线程职责，EventLoop 死循环机制。
*   **IO 模型**: NIO vs Epoll (Linux) vs KQueue (Mac) vs IO_Uring (未来)。
*   **Bootstrap**: `group`, `channel`, `option`, `childHandler` 启动全流程。
*   **并发控制**: `DefaultEventExecutorGroup` (业务线程池) vs `EventLoopGroup` (IO线程池)。

#### 2. 数据处理篇 (Data Handling)
*   **ByteBuf 体系**: Heap vs Direct，Pooled vs Unpooled，Safe vs Unsafe。
*   **零拷贝技术**: `slice()` (切片), `CompositeByteBuf` (拼包)。
*   **引用计数**: `retain/release` 规则，Inbound/Outbound 释放责任人。
*   **底层原理**: `Jemalloc` 内存分配概念 (Chunk/Page)。

#### 3. Pipeline 与 Handler (Pipeline Art)
*   **传播机制**: Inbound (Head->Tail), Outbound (Tail->Head), Exception (Inbound)。
*   **上下文**: `ctx.write` (当前节点) vs `ctx.channel().write` (Tail节点)。
*   **掩码优化**: Execution Mask 如何跳过空方法 (O(1) 遍历)。
*   **异常陷阱**: `write` 异常吞噬问题 (`FIRE_EXCEPTION_ON_FAILURE`)。

#### 4. 异步编程篇 (Async Programming)
*   **Future/Promise**: Netty 回调模型 (`addListener`) vs JDK 阻塞模型。
*   **高级用法**: `PromiseCombiner` (聚合), `ProgressivePromise` (进度条)。
*   **死锁原理**: EventLoop 线程内调用 `sync()` 导致死锁的检测机制 (`BlockingOperationException`)。

#### 5. 参数调优篇 (Tuning)
*   **TCP 参数**: `SO_BACKLOG`, `TCP_NODELAY` (必开), `SO_KEEPALIVE`, `SO_REUSEADDR`。
*   **Netty 参数**: `WRITE_BUFFER_WATER_MARK` (写水位线)。

---

### 🔜 待修练模块 (Pending Modules - The Road Ahead)

#### 1. 生存模式：心跳与重连 (Survival Mode) **[High Priority]**
*   **服务端**: `IdleStateHandler` (读/写/读写空闲检测)。
    *   如何区分“真死机”和“假死机”？
    *   超时后如何主动断开连接？
*   **客户端**: 断线自动重连机制。
    *   **指数退避算法 (Exponential Backoff)**: 防止重连风暴打挂服务端。
    *   **Bootstrap 复用**: 重连时不需要重新 new Bootstrap。

#### 2. 源码深水区 (Deep Dive) **[Hardcore]**
*   **内存池 (Recycler)**:
    *   Netty 的轻量级对象池实现。
    *   **Stack**: 线程本地栈。
    *   **WeakOrderQueue**: 异线程回收优化 (无锁)。
*   **无锁队列 (MpscQueue)**:
    *   `JCTools` 库。
    *   **False Sharing (伪共享)**: 缓存行填充 (Padding) 原理。
*   **时间轮 (HashedWheelTimer)**:
    *   O(1) 复杂度处理百万级定时任务。
    *   Tick, Bucket, Wheel 的设计。
*   **ChannelFactory**:
    *   `ReflectiveChannelFactory` 源码分析 (反射创建 Channel)。

#### 3. 高级应用篇 (Advanced Application)
*   **流量整形 (Traffic Shaping)**:
    *   `GlobalTrafficShapingHandler`。
    *   如何限制带宽？(Token Bucket 算法 + `Thread.sleep` 延迟发送)。
*   **HTTP/WebSocket 开发**:
    *   Netty 实现高性能 Web 服务器。
    *   HTTP/2 支持。
*   **传输安全 (SSL/TLS)**:
    *   `SslHandler` 工作原理 (SSLEngine)。
    *   `OpenSSL` (netty-tcnative) vs JDK SSL 性能对比。

#### 4. 测试与调试 (Testing)
*   **EmbeddedChannel**:
    *   不启动网络，直接测试 Handler 逻辑。
    *   `writeInbound` / `readOutbound` 技巧。

---

### 📂 关键代码资产 (Code Assets)

*   **`NettyServer.java`**: 全功能服务端 (支持 Epoll, 业务线程池, 零拷贝编码, 异常捕获)。
*   **`Netty_Study_Notes.md`**: 已生成的 v1.2 完整学习笔记。
*   **`PromiseAsyncTest.java`**: 标准异步 Promise 用法。
*   **`PromiseSyncDeadLockTest.java`**: 死锁演示 (反面教材)。
*   **`PromiseCombineTest.java`**: 多任务聚合演示。
*   **`ProgressivePromiseTest.java`**: 进度条演示。

---

### 💡 下一步指令 (Next Step Instruction)

请直接复制以下指令给新的 AI 助手：

> "我们要继续 Netty 专家级课程。目前已完成 基础/数据/Pipeline/异步/参数 模块。
>
> **当前任务**：进入 **'生存模式：心跳与重连'** 章节。
> 
> 请基于 `NettyServer.java`，教我如何：
> 1. 在服务端添加 `IdleStateHandler` 检测假死连接。
> 2. 编写一个健壮的 `NettyClient`，实现断线后的 **指数退避重连**。"

