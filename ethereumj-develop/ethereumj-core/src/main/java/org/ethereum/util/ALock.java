/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.util;

import java.util.concurrent.locks.Lock;

/**
 * AutoClosable Lock wrapper. Use case:
 *
 * try (ALock l = wLock.lock()) {
 *     // do smth under lock
 * }
 *
 * Created by Anton Nashatyrev on 27.01.2017.
 */
public final class ALock implements AutoCloseable {
    private final Lock lock;

    public ALock(Lock l) {
        this.lock = l;
    }

    public final ALock lock() {
        this.lock.lock();
        return this;
    }

    public final void close() {
        this.lock.unlock();
    }
}



/*

一、认识AutoCloseable

AutoCloseable接口位于java.lang包下，从JDK1.7开始引入。



1.在1.7之前，我们通过try{} finally{} 在finally中释放资源。

在finally中关闭资源存在以下问题：
1、自己要手动写代码做关闭的逻辑；
2、有时候还会忘记关闭一些资源；
3、关闭代码的逻辑比较冗长，不应该是正常的业务逻辑需要关注的；

2.对于实现AutoCloseable接口的类的实例，将其放到try后面（我们称之为：带资源的try语句），在try结束的时候，会自动将这些资源关闭（调用close方法）。

带资源的try语句的3个关键点：
1、由带资源的try语句管理的资源必须是实现了AutoCloseable接口的类的对象。
2、在try代码中声明的资源被隐式声明为fianl。
3、通过使用分号分隔每个声明可以管理多个资源。

public class AutoCloseableDemo {
    public static void main(String[] args) {
        try (AutoCloseableObjecct app = new AutoCloseableObjecct()) {
            System.out.println("--执行main方法--");
        } catch (Exception e) {
            System.out.println("--exception--");
        } finally {
            System.out.println("--finally--");
        }
    }

    //自己定义类 并实现AutoCloseable
    public static class AutoCloseableObjecct implements AutoCloseable {
        @Override
        public void close() throws Exception {
            System.out.println("--close--");
        }

    }


    @Test
    public void demo2() {

        //JDK1.7之前,释放资源方式
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream("");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                fileInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //1.7之后，只要实现了AutoCloseable接口
        try (FileInputStream fileInputStream2 = new FileInputStream("")) {

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}




 */