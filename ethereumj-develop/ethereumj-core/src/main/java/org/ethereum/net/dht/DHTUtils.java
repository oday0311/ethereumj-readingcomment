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
package org.ethereum.net.dht;

import java.util.List;

import static org.ethereum.net.dht.Bucket.*;

public class DHTUtils {

    public static void printAllLeafs(Bucket root){
        SaveLeaf saveLeaf = new SaveLeaf();
        root.traverseTree(saveLeaf);

        for (Bucket bucket : saveLeaf.getLeafs())
            System.out.println(bucket);
    }

    public static List<Bucket> getAllLeafs(Bucket root){
        SaveLeaf saveLeaf = new SaveLeaf();
        root.traverseTree(saveLeaf);

        return saveLeaf.getLeafs();
    }
}


/*

    Kademlia 算法
引言
        在p2p网络中，资源应当被分配到合适的节点上，最理想的情况就是当想要获取资源编号为i的资源时候就去节点编号为i的节点上获取。在现实情况下，为了达成这个效果，必须做到以下两点：

        节点编号和资源编号拥有同样的取值空间
        一个资源应当被保存在其就近的节点上
        Chrod算法通过把节点编号和资源编号按序排列在Chrod环中，对于没有节点的资源顺时针旋转到第一个就近节点的位置上。这样就完成了尽最大可能的对应关系。由于存在节点编号和资源编号错位的情况，因此就需要一些发现算法来寻找某个资源的确切节点位置。kademlia算法就是一个常用且简单的算法。

        编号
        Kademlia算法的节点编号和资源编号和Chrod算法相同。编号的长度为160-bit。

        距离
        Kademlia定义两个编号 x,y 的距离 d(x,y)=x⊕y 其中 ⊕ 为异或操作，例如 110⊕010=100=8 那么就称两个节点的距离为8。

        k-bucket
        如果节点编号一共有n位，那么一个节点需要维护n个k-bucket，每个k-bucket相当于一个路由表，k-bucket-i为第i个路由表，下面讲解k-bucket-2的构成：

        假设当前节点编号二进制为0101010，k-bucket-2表示与其第二位和不同，但是第二位左边的都相同的节点，例如0101001和0101000，那么k-bucket-2里面就记录了这两个节点的信息。依次类推，节点就维护了一个很大的路由表。

        我们可以看到，当i值越大，k-bucket-i中的可选的节点就会越多，因此规定k-bucket-i中节点最多为k个。k-bucket中仅存储距离最近的k个节点。

        协议
        Kademlia算法有以下基本操作：

        FIND_NODE
        FIND_VALUE
        PING
        STORE
        任何一种操作都会更新k-bucket，k-bucket的更新机制如下：

        如果节点已经在k-bucket中，将其移动到最末端
        如果节点不在k-bucket中：

        k-bucket未满

        插入到k-bucket末尾
        k-bucket满了

        Ping第一个节点：

        Ping通：第一个节点移动到尾部，丢弃新节点
        Ping不通：移除第一个节点，新节点插入到尾部
        lookup
        lookup是Kadmelia中最主要的过程，在算法中定义了 α 表示每次通信节点的数量，以下是lookup的基本流程：

        节点选取其最近k-bucket中的 α 个节点
        向这 α 个节点发送 FIND_NODE RPC 请求
        FIND_NODE操作被发送到一个节点后，该节点会返回k个离目标节点最近的节点
        节点从k个返回的节点中选择 α 个节点继续发送 FIND_NODE操作
        对于那些没有响应的节点将会被从k-bucket中删除
        如果一轮FIND_NODE请求没有发现比之前记录的节点中距离更近的节点，那么节点将会给未查询的k个最近的节点发送FIND_NODE请求
        当获得到更近的节点后lookup就会结束
        存储
        为了去存储一个{key-value}资源，参与者需要定位该资源的k个最近的节点然后发送STORE RPC请求，另外每个节点，每个小时还需要重复发送该请求。

        节点加入
        当一个新的节点u加入网络



        ，必须和一个已知的节点w通信，u将w加入到最近的k-bucket中，接着u用自己的节点编号启动lookup操作，不断刷新其 k-bucket
 */