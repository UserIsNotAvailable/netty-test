package com.wtb;

import io.netty.util.Recycler;

public class RecyclerTest {

    // 1. 定义一个可回收的对象类
    // 必须实现 Handle 接口（或者保存 Handle 引用），用于把自己还给池子
    static class User {
        private final Recycler.Handle<User> handle;
        private String name;

        // 构造函数是私有的，强制通过池子获取
        public User(Recycler.Handle<User> handle) {
            this.handle = handle;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        // 回收方法：调用 handle.recycle(this)
        public void recycle() {
            // 在回收前，最好清理一下状态，防止数据污染
            this.name = null; 
            handle.recycle(this);
        }
    }

    // 2. 创建对象池实例
    // 泛型是我们要池化的对象类型
    private static final Recycler<User> RECYCLER = new Recycler<User>() {
        @Override
        protected User newObject(Handle<User> handle) {
            // 当池子空了，需要新创建对象时，会调用这个方法
            return new User(handle);
        }
    };

    public static void main(String[] args) {
        System.out.println("--- 第一次获取对象 ---");
        // 3. 从池中获取对象
        User user1 = RECYCLER.get();
        user1.setName("Alice");
        System.out.println("User1: " + user1 + ", Name: " + user1.getName());

        System.out.println("\n--- 回收 User1 ---");
        // 4. 用完还回去
        user1.recycle();

        System.out.println("\n--- 第二次获取对象 ---");
        // 5. 再次获取
        User user2 = RECYCLER.get();
        System.out.println("User2: " + user2 + ", Name: " + user2.getName());

        System.out.println("\n--- 验证 ---");
        // 6. 验证是否是同一个对象
        if (user1 == user2) {
            System.out.println("成功复用！User1 和 User2 是同一个实例。");
        } else {
            System.out.println("复用失败！");
        }
        
        // 注意：user2 虽然是复用的，但字段应该被重置了（如果你在 recycle 里清空了的话）
        // 如果没清空，这里可能会读到脏数据 "Alice"
        System.out.println("User2 Name (期望是null): " + user2.getName());
    }
}


