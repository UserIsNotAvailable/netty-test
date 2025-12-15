# 📘 Netty 核心修炼手册 (v1.2 - 完整版)

这份笔记涵盖了 Netty 从架构原理到实战调优的核心知识点，补充了底层实现细节、线程模型层级和遗漏的高级用法。

## 第一章：世界观与架构 (Architecture)

### 1.1 线程模型 (Reactor)
*   **BossGroup (MainReactor)**:
    *   职责：只负责处理连接的 Accept 事件。
    *   线程数：通常 **1** 个就足够处理极高并发的连接请求。
    *   **注意**: 一个 `ServerSocketChannel` 只能绑定到一个 Boss 线程。
*   **WorkerGroup (SubReactor)**:
    *   职责：负责处理已连接 Socket 的 IO 读写、编解码。
    *   线程数：默认 **CPU 核数 * 2**。
*   **EventLoop**:
    *   本质：一个绑定了 Selector 的死循环线程 (`run()`)。
    *   **Thread Confinement (线程限制)**: 一个 Channel 一旦被分配给某个 EventLoop，它所有的 IO 操作和 Handler 逻辑都会在**同一个线程**内执行，从而实现了无锁化设计。

### 1.2 IO 模型全家福
| 模型 | 类名 | 平台 | 特点 |
| :--- | :--- | :--- | :--- |
| **NIO** | `NioEventLoopGroup` | 通用 (Windows/Linux) | 基于 JDK Selector，Windows 下模拟实现。 |
| **Epoll** | `EpollEventLoopGroup` | Linux | 基于 Linux epoll，支持边缘触发 (ET)，性能最强。 |
| **KQueue** | `KQueueEventLoopGroup` | MacOS / BSD | 基于 BSD kqueue，性能优异。 |
| **Local** | `LocalEventLoopGroup` | JVM 内部 | 内存管道，用于进程内通信（不走 TCP 栈）。 |

### 1.3 启动引导 (Bootstrap 进阶)
*   **`group()` 的变体**:
    *   `group(boss, worker)`: 标准主从。
    *   `group(singleGroup)`: 单线程模式 (Redis 模式)。
*   **`channel(Class)`**: 
    *   原理：`ReflectiveChannelFactory`。底层调用 `Class.newInstance()` 反射创建 Channel 实例。
*   **`handler()` vs `childHandler()`**:
    *   `handler()`: Boss 线程的 Handler (处理握手)。只能拿到 IP/端口，拿不到业务数据。
    *   `childHandler()`: Worker 线程的 Handler (处理读写)。
*   **`attr()` vs `childAttr()`**:
    *   `attr()`: 绑给 ServerSocketChannel。
    *   `childAttr()`: 绑给每一个连上来的 SocketChannel (如 SessionID)。

---

## 第二章：数据之道 (Data Handling)

### 2.1 ByteBuf 体系
*   **内存位置**:
    *   **Heap (堆内)**: `byte[]`。分配快，GC 管理，但 IO 时需要拷贝。
    *   **Direct (堆外)**: 物理内存。分配慢，IO 零拷贝。**Netty IO 默认使用 Direct**。
*   **内存管理**:
    *   **Pooled (池化)**: 使用 Jemalloc 算法复用内存 (Arena -> Chunk -> Page -> SubPage)，减少申请开销和 GC。**Netty 默认开启**。
    *   **Unpooled (非池化)**: 每次都新建。
*   **操作方式**:
    *   **Unsafe**: 通过 `sun.misc.Unsafe.getInt(address)` 直接读取内存地址（绕过 JDK 边界检查，极速）。
    *   **Safe**: 依赖 JDK API。

### 2.2 引用计数 (Reference Counting)
Netty 采用手动内存管理来优化堆外内存。
*   **规则**:
    *   `retain()`: 计数 +1。
    *   `release()`: 计数 -1。当计数归零时，内存被回收。
*   **Slice 的陷阱**:
    *   `buf.slice()` 出来的对象和原 buf **共享引用计数**。如果你要留存 slice，必须手动 `retain()`，否则原 buf 释放后，slice 也会失效。
*   **谁负责释放?**
    *   **Inbound**: 最后一个使用的 Handler（通常是业务 Handler）负责释放。`SimpleChannelInboundHandler` 会自动帮你释放。
    *   **Outbound**: Netty 底层发送完数据后，会自动释放。

### 2.3 零拷贝技术
*   **Slice (切片)**: 逻辑切分，不拷贝内存。
*   **Composite (组合)**: `CompositeByteBuf`。逻辑上合并多个 Buffer (Header + Body)，底层是 Buffer 数组，IO 时使用 `writev` 发送。
*   **FileRegion**: 封装 `sendfile` 系统调用，实现文件零拷贝传输 (内核态直接拷贝到网卡)。

### 2.4 遗珠细节
*   **`discardReadBytes()`**: 将未读数据移到 Buffer 开头，整理内存（发生内存拷贝，慎用）。
*   **`noPreferDirect`**: JVM 参数，控制是否偏向 Direct 内存。

---

## 第三章：流水线艺术 (Pipeline & Handler)

### 3.1 结构与传播
*   **双向链表**: `Head <-> Context A <-> Context B <-> Tail`。
*   **Inbound (读)**: 从 Head 往 Tail 传。
    *   事件: `channelRead`, `channelActive`, `exceptionCaught`。
*   **Outbound (写)**: 从 Tail 往 Head 传。
    *   事件: `write`, `flush`, `close`。
    *   **坑**: `ctx.write()` (从当前节点往前找) vs `ctx.channel().write()` (从 Tail 开始往前找)。

### 3.2 关键 Handler 与 优化
*   **`ByteToMessageDecoder`**: 处理 TCP 粘包/拆包。内部维护 `cumulation` 缓冲区。
*   **`@Sharable`**: 
    *   如果是无状态 Handler，加上此注解并做成单例，所有 Channel 共用一个实例，极大减少 GC 压力。
*   **Execution Mask (掩码优化)**:
    *   Netty 在 Handler 加入时会计算掩码 (`mask`)。如果 Handler 没有重写 `channelRead`，运行时直接**跳过**该 Handler，O(1) 遍历。
*   **插队**: `pipeline.addBefore()`, `pipeline.addAfter()`。

### 3.3 异常传播 (Ghost Story)
*   **异常也是 Inbound 事件**。无论是在读还是写过程中抛出的异常，都会顺着 Inbound 方向传播到 Tail。
*   **Write 异常吞噬**: `ctx.write()` 操作是异步的，其异常通常存放在返回的 `Future` 中，不会自动触发 `exceptionCaught`。
    *   **解法**: `future.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)`。

---

## 第四章：异步之魂 (Future & Promise)

### 4.1 死锁陷阱 (必考)
*   **现象**: 在 EventLoop 线程里调用 `future.sync()` 或 `future.await()`。
*   **原理**: 线程在等待任务结束，但任务需要这个线程去执行回调才能结束。互相等待。
*   **Netty 保护**: `DefaultPromise.checkDeadLock()` 会检测 `executor.inEventLoop()`，如果是，直接抛出 `BlockingOperationException`。

### 4.2 Promise (可写的 Future)
*   `Promise` 是 `Future` 的子类。
*   生产者使用 `promise.setSuccess(val)` 或 `promise.setFailure(ex)` 来通知结果。
*   **notify 机制**: 如果设置结果时当前线程是 Loop 线程，直接执行 Listener；否则提交任务到 Loop 线程执行（保证线程安全）。

### 4.3 进阶用法
*   **`PromiseCombiner`**: 聚合多个异步任务（煮饭+炒菜 -> 开饭）。
*   **`ProgressivePromise`**: 支持进度条反馈 (`setProgress`)。
*   **`awaitUninterruptibly()`**: 不响应中断的等待，Netty 内部常用（防止用户随便中断 IO 线程）。

---

## 第五章：生产级参数 (TCP Tuning)

### 5.1 Boss 线程参数
*   **`SO_BACKLOG`**: 全连接队列大小。在高并发下必须设置足够大（如 1024），否则客户端会收到 "Connection Refused"。
*   **`SO_REUSEADDR`**: 端口复用，允许服务器重启后立即绑定该端口。

### 5.2 Worker 线程参数
*   **`TCP_NODELAY`**: **必须开启 (true)**。禁用 Nagle 算法，避免小包延迟，实现由发即发。
*   **`SO_KEEPALIVE`**: TCP 层面的心跳保活（通常 2 小时检测一次）。虽然有用，但通常需要配合应用层心跳。
*   **`WRITE_BUFFER_WATER_MARK`**: 写缓冲区水位线。用于流控，防止发送速度过快导致 OOM。
*   **`SO_RCVBUF`/`SO_SNDBUF`**: 缓冲区大小。一般不改，OS 自动调整。高吞吐场景可调大。
*   **`ALLOCATOR`**: 指定内存分配器。

---

## 附录：并发控制与类层级 (Advanced Threading)

### 附 1: 线程模型层级图谱
```text
EventExecutorGroup (接口: 线程池)
  ├── MultithreadEventExecutorGroup (抽象类: 管理一群单线程)
  │    ├── DefaultEventExecutorGroup (业务线程池，无Selector)
  │    └── MultithreadEventLoopGroup (IO线程池，有Selector)
  │         ├── NioEventLoopGroup
  │         ├── EpollEventLoopGroup
  │         └── LocalEventLoopGroup
  │
  └── SingleThreadEventExecutor (抽象类: 一个线程)
       ├── DefaultEventExecutor (一个业务线程)
       └── SingleThreadEventLoop (一个IO线程)
            ├── NioEventLoop (真·干活的)
            └── EpollEventLoop (真·干活的)
```

### 附 2: 选型指南
*   **EventLoopGroup**: 带 Selector，负责 IO。
    *   **Nio**: 通用。
    *   **Epoll**: Linux 高性能。
*   **EventExecutorGroup**: 不带 Selector，纯线程池，负责业务。
    *   **DefaultEventExecutorGroup**: 标准选择，保序，支持 Future。
    *   **UnorderedThreadPoolEventExecutor**: 不保序，吞吐量更高。

### 附 3: 生产架构策略
*   **纯 IO 场景**: 业务逻辑简单，直接在 Worker 线程处理。
*   **耗时业务场景**:
    *   使用 `DefaultEventExecutorGroup` (业务线程池)。
    *   `pipeline.addLast(businessGroup, new MyHandler())`。
    *   这样 IO 线程只负责解码，然后扔给业务线程池，立刻返回去处理其他连接。
