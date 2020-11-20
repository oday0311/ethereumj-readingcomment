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
package org.ethereum.trie;

import org.ethereum.util.Value;


/**
 * A Node in a Merkle Patricia Tree is one of the following:
 *
 * - NULL (represented as the empty string)
 * - A two-item array [ key, value ] (1 key for 2-item array)
 * - A 17-item array [ v0 ... v15, vt ] (16 keys for 17-item array)
 *
 * The idea is that in the event that there is a long path of nodes
 * each with only one element, we shortcut the descent by setting up
 * a [ key, value ] node, where the key gives the hexadecimal path
 * to descend, in the compact encoding described above, and the value
 * is just the hash of the node like in the standard radix tree.
 *
 *                               R
 *                              / \
 *                             /   \
 *                            N     N
 *                           / \   / \
 *                          L   L L   L
 *
 * Also, we add another conceptual change: internal nodes can no longer
 * have values, only leaves with no children of their own can; however,
 * since to be fully generic we want the key/value store to be able
 * store keys like 'dog' and 'doge' at the same time, we simply add
 * a terminator symbol (16) to the alphabet so there is never a value
 * "en-route" to another value.
 *
 * Where a node is referenced inside a node, what is included is:
 *
 *      H(rlp.encode(x)) where H(x) = keccak(x) if len(x) &gt;= 32 else x
 *
 * Note that when updating a trie, you will need to store the key/value pair (keccak(x), x)
 * in a persistent lookup table when you create a node with length &gt;= 32,
 * but if the node is shorter than that then you do not need to store anything
 * when length &lt; 32 for the obvious reason that the function f(x) = x is reversible.
 *
 * @author Nick Savers
 * @since 20.05.2014
 */
public class Node {

    /* RLP encoded value of the Trie-node */
    private final Value value;
    private boolean dirty;

    public Node(Value val) {
        this(val, false);
    }

    public Node(Value val, boolean dirty) {
        this.value = val;
        this.dirty = dirty;
    }

    public Node copy() {
        return new Node(this.value, this.dirty);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public Value getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "[" + dirty + ", " + value + "]";
    }
}


/*

https://image-static.segmentfault.com/309/469/3094698205-5c009d8768f3b_articlex


MTP树种的节点包含空节点、叶子节点、扩展节点和分支节点。

        Nibble：它是key的基本单元，是一个四元组（四个bit位的组合例如二进制表达的0010就是一个四元组）
        空节点**：简单的表示空，在代码中是一个空串。
        叶子节点（leaf）：只有两个元素，分别为key和value，表示为[key,value]的一个键值对，其中key是key的一种特殊十六进制编码，value是value的RLP编码。
        扩展节点（extension）：也是[key，value]的一个键值对，但是这里的value是其他节点的hash值，这个hash可以被用来查询数据库中的节点。也就是说通过hash链接到其他节点。
        分支节点（branch）：分支节点有17个元素，回到Nibble，四元组是key的基本单元，四元组最多有16个值。所以前16个必将落入到在其遍历中的键的十六个可能的半字节值中的每一个。第17个是存储那些在当前结点结束了的节点(例如， 有三个key,分别是 (abc ,abd, ab) 第17个字段储存了ab节点的值)
*/
