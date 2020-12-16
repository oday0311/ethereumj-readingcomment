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
package org.ethereum.net.swarm;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Hierarchical structure of path items
 * The item can be one of two kinds:
 * - the leaf item which contains reference to the actual data with its content type
 * - the non-leaf item which contains reference to the child manifest with the dedicated content type
 */
public class Manifest {
    public enum Status {
        OK(200),
        NOT_FOUND(404);

        private int code;
        Status(int code) {
            this.code = code;
        }
    }

    // used for Json [de]serialization only
    private static class ManifestRoot {
        public List<ManifestEntry> entries = new ArrayList<>();

        public ManifestRoot() {
        }

        public ManifestRoot(List<ManifestEntry> entries) {
            this.entries = entries;
        }

        public ManifestRoot(ManifestEntry parent) {
            entries.addAll(parent.getChildren());
        }
    }

    /**
     *  Manifest item
     */
    @JsonAutoDetect(getterVisibility= JsonAutoDetect.Visibility.NONE,
                    fieldVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
                    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class ManifestEntry extends StringTrie.TrieNode<ManifestEntry> {
        public String hash;
        public String contentType;
        public Status status;

        private Manifest thisMF;

        public ManifestEntry() {
            super(null, "");
        }

        public ManifestEntry(String path, String hash, String contentType, Status status) {
            super(null, "");
            this.path = path;
            this.hash = hash;
            this.contentType = contentType;
            this.status = status;
        }

        ManifestEntry(ManifestEntry parent, String path) {
            super(parent, path);
            this.path = path;
        }

        /**
         *  Indicates if this entry contains reference to a child manifest
         */
        public boolean isManifestType() { return MANIFEST_MIME_TYPE.equals(contentType);}
        boolean isValid() {return hash != null;}
        void invalidate() {hash = null;}

        @Override
        public boolean isLeaf() {
            return !(isManifestType() || !children.isEmpty());
        }

        /**
         *  loads the child manifest
         */
        @Override
        protected Map<String, ManifestEntry> loadChildren() {
            if (isManifestType() && children.isEmpty() && isValid()) {
                ManifestRoot manifestRoot = load(thisMF.dpa, hash);
                children = new HashMap<>();
                for (Manifest.ManifestEntry entry : manifestRoot.entries) {
                    children.put(getKey(entry.path), entry);
                }
            }
            return children;
        }

        @JsonProperty
        public String getPath() {
            return path;
        }

        @JsonProperty
        public void setPath(String path) {
            this.path = path;
        }

        @Override
        protected ManifestEntry createNode(ManifestEntry parent, String path) {
            return new ManifestEntry(parent, path).setThisMF(parent.thisMF);
        }

        @Override
        protected void nodeChanged() {
            if (!isLeaf()) {
                contentType = MANIFEST_MIME_TYPE;
                invalidate();
            }
        }

        ManifestEntry setThisMF(Manifest thisMF) {
            this.thisMF = thisMF;
            return this;
        }

        @Override
        public String toString() {
            return "ManifestEntry{" +
                    "path='" + path + '\'' +
                    ", hash='" + hash + '\'' +
                    ", contentType='" + contentType + '\'' +
                    ", status=" + status +
                    '}';
        }
    }

    public final static String MANIFEST_MIME_TYPE = "application/bzz-manifest+json";

    private DPA dpa;
    private final StringTrie<ManifestEntry> trie;

    /**
     * Constructs the Manifest instance with backing DPA storage
     * @param dpa DPA
     */
    public Manifest(DPA dpa) {
        this(dpa, new ManifestEntry(null, ""));
    }

    private Manifest(DPA dpa, ManifestEntry root) {
        this.dpa = dpa;
        trie = new StringTrie<ManifestEntry>(root.setThisMF(this)) {};
    }

    /**
     * Retrieves the entry with the specified path loading necessary nested manifests on demand
     */
    public ManifestEntry get(String path) {
        return trie.get(path);
    }

    /**
     * Adds a new entry to the manifest hierarchy with loading necessary nested manifests on demand.
     * The entry path should contain the absolute path relative to this manifest root
     */
    public void add(ManifestEntry entry) {
        add(null, entry);
    }

    void add(ManifestEntry parent, ManifestEntry entry) {
        ManifestEntry added = parent == null ? trie.add(entry.path) : trie.add(parent, entry.path);
        added.hash = entry.hash;
        added.contentType = entry.contentType;
        added.status = entry.status;
    }

    /**
     * Deletes the leaf manifest entry with the specified path
     */
    public void delete(String path) {
        trie.delete(path);
    }

    /**
     * Loads the manifest with the specified hashKey from the DPA storage
     */
    public static Manifest loadManifest(DPA dpa, String hashKey) {
        ManifestRoot manifestRoot = load(dpa, hashKey);

        Manifest ret = new Manifest(dpa);
        for (Manifest.ManifestEntry entry : manifestRoot.entries) {
            ret.add(entry);
        }
        return ret;
    }

    private static Manifest.ManifestRoot load(DPA dpa, String hashKey) {
        try {
            SectionReader sr = dpa.retrieve(new Key(hashKey));
            ObjectMapper om = new ObjectMapper();
            String s = Util.readerToString(sr);
            ManifestRoot manifestRoot = om.readValue(s, ManifestRoot.class);
            return manifestRoot;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves this manifest (all its modified nodes) to this manifest DPA storage
     * @return hashKey of the saved Manifest
     */
    public String save() {
        return save(trie.rootNode);
    }

    private String save(ManifestEntry e) {
        if (e.isValid()) return e.hash;
        for (ManifestEntry c : e.getChildren()) {
            save(c);
        }
        e.hash = serialize(dpa, e);
        return e.hash;
    }


    private String serialize(DPA dpa, ManifestEntry manifest) {
        try {
            ObjectMapper om = new ObjectMapper();

            ManifestRoot mr = new ManifestRoot(manifest);
            String s = om.writeValueAsString(mr);

            String hash = dpa.store(Util.stringToReader(s)).getHexString();
            return hash;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return  manifest dump for debug purposes
     */
    public String dump() {
        return Util.dumpTree(trie.rootNode);
    }
}



/*
以太坊Swarm体系结构


区块链，例如以太坊，允许实现去中心化的应用程序（DApps）。DApps的主要思想包括在不可变区块链上以智能合约的形式部署应用程序，从而消除可信应用程序服务器和单点故障。以太坊Swarm旨在通过作为去中心化数据存储解决方案来增强DApps的出现，并通过扩展Web 3.0范例。
然而，令人惊讶地难以使Web 3.0模型以纯粹去中心化的方式工作。这有两个主要原因。首先，与智能合约的交互很复杂，并且提供了非常差的用户体验。出于这个原因，大多数DApps提供了一个Web界面，由一个托管在传统Web服务器上并通过HTTP协议提供服务的脱链前端组成。当然，这会在设置中引入可信的中心化组件。其次，在区块链上存储大量数据非常昂贵，这就是为什么DApps通常需要一种将一些数据存储在离线链中的方法。同样，使用数据库管理系统或传统文件系统违背了去中心化模型。
去中心化存储

可以在去中心化存储解决方案中找到对中心化组件的这种依赖性的解决方案。这个想法很简单：协作节点的对等（P2P）网络用于汇集资源。P2P网络充当具有内置冗余的分布式云存储解决方案。理论上，任何类型的数据都可以从这种去中心化的网络托管和提供，包括离线DApp数据和构成DApps前端的文件。
推荐阅读
1
枢纽创业？ 4种正确做事的方法
2020年12月14日 星期一 08:23:09
2
RenBTC Defi硬币评测–关于RenBTC的详细评测
2020年12月14日 星期一 04:12:26
可能最知名的分布式存储解决方案是行星际文件系统（IPFS），它使用分布式哈希表数据结构在节点网络上存储内容。但是，除非原始数据所有者继续从其自己的主机提供IPFS内容，否则不保证IPFS内容可用。这是因为通过网络的内容传播根据流行度被优先化，并且不受欢迎的内容可能被垃圾收集。缺乏激励或节点来托管内容是去中心化存储解决方案中的一般问题。
以太坊Swarm体系结构

Swarm是以太坊实施的去中心化文件存储网络。它由以太坊Geth  客户端支持，并且与存储网络的交互与以太坊区块链紧密相关，并且需要以太坊帐户。
Swarm分布式存储模型
上图说明了以太坊Swarm如何在P2P网络上分发数据。数据被分成称为块的块，块的最大大小限制为4K字节。网络层与这些块所代表的内容无关，例如，它们是文件的一部分还是任何其他数据。块通过网络分布，并通过其内容的32字节哈希来寻址。这确保了可以验证数据完整性，但是，这引入了存储可能被修改的内容的问题。散列寻址也不是非常用户友好。出于这个原因，另一层，以太坊名称服务（ENS）允许用户为其内容注册人类可读的名称。ENS在以太坊网络上实施为智能合约，可以被视为相当于促进传统互联网服务中内容命名的域名服务（DNS）。
激励层

以太坊Swarm与IPFS的区别在于它不仅仅引用和（可能缓存）内容所有者自己的存储上可用的内容。相反，它实际上构成了可以上载内容的云服务。
目前，无法保证上传的内容仍然可用，因为节点可以随意加入和离开网络，甚至降低其存储容量。计划未来的激励层，以补偿节点所有者提供存储空间。通过与以太坊的紧密结合，这成为可能。
使用Swarm

要连接到以太坊Swarm，需要运行Geth的实例。Swarm客户端本身可以从不同平台的Swarm下载页面获得。
安装Swarm可执行文件后，你可以使用正在运行的Geth实例管理的现有帐户连接到网络：
* swarm -bzzaccount
然后，Swarm在端口8500上提供端点。在浏览器中导航到http:// localhost：8500将打开Swarm网络的搜索框。可以在官方文档中找到更高级的使用选项。
当然，Web浏览器当前不支持Swarm的协议。因此，以太坊基金会提供网关服务，允许在没有本地Swarm客户端的情况下访问Swarm托管内容。可以通过在以下URL中放置内容地址来访问网关：


 */