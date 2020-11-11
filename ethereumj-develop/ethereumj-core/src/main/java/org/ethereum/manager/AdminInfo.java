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


//计算最近的10000个块的执行时间。
package org.ethereum.manager;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 11.12.2014
 */


//1、@Service用于标注业务层组件
//2、@Controller用于标注控制层组件(如struts中的action)
//3、@Repository用于标注数据访问组件，即DAO组件.
// 4、@Component泛指组件，当组件不好归类的时候，我们可以使用这个注解进行标注。
@Component
public class AdminInfo {
    private static final int ExecTimeListLimit = 10000;

    private long startupTimeStamp;
    //共识
    private boolean consensus = true;
    private List<Long> blockExecTime = new LinkedList<>();



//    @PostConstruct该注解被用来修饰一个非静态的void（）方法。被@PostConstruct修饰的方法会在服务器加载Servlet的时候运行，并且只会被服务器执行一次。PostConstruct在构造函数之后执行，init（）方法之前执行。
//
//    通常我们会是在Spring框架中使用到@PostConstruct注解 该注解的方法在整个Bean初始化中的执行顺序：
//
//    Constructor(构造方法) -> @Autowired(依赖注入) -> @PostConstruct(注释的方法)
//
//    应用：在静态方法中调用依赖注入的Bean中的方法。
    @PostConstruct
    public void init() {
        startupTimeStamp = System.currentTimeMillis();
    }

    public long getStartupTimeStamp() {
        return startupTimeStamp;
    }

    public boolean isConsensus() {
        return consensus;
    }

    public void lostConsensus() {
        consensus = false;
    }

    public void addBlockExecTime(long time){
        while (blockExecTime.size() > ExecTimeListLimit) {
            blockExecTime.remove(0);
        }
        blockExecTime.add(time);
    }

    public Long getExecAvg(){

        if (blockExecTime.isEmpty()) return 0L;

        long sum = 0;
        for (int i = 0; i < blockExecTime.size(); ++i){
            sum += blockExecTime.get(i);
        }

        return sum / blockExecTime.size();
    }

    public List<Long> getBlockExecTime(){
        return blockExecTime;
    }
}
