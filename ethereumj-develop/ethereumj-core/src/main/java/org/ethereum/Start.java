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
package org.ethereum;

import org.ethereum.cli.CLIInterface;
import org.ethereum.config.SystemProperties;
import org.ethereum.mine.Ethash;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.math.NumberUtils.toInt;
import static org.ethereum.facade.EthereumFactory.createEthereum;

/**
 * @author Roman Mandeleil
 * @since 14.11.2014
 */
public class Start {

    public static void main(String args[]) {
        CLIInterface.call(args);

        final SystemProperties config = SystemProperties.getDefault();

        getEthashBlockNumber().ifPresent(blockNumber -> createDagFileAndExit(config, blockNumber));
        getBlocksDumpPath(config).ifPresent(dumpPath -> loadDumpAndExit(config, dumpPath));

        createEthereum();
    }

    private static void disableSync(SystemProperties config) {
        config.setSyncEnabled(false);
        config.setDiscoveryEnabled(false);
    }

    private static Optional<Long> getEthashBlockNumber() {
        String value = System.getProperty("ethash.blockNumber");
        return isEmpty(value) ? Optional.empty() : Optional.of(parseLong(value));
    }

    /**
     * Creates DAG file for specified block number and terminate program execution with 0 code.
     *
     * @param config      {@link SystemProperties} config instance;
     * @param blockNumber data set block number;
     */
    private static void createDagFileAndExit(SystemProperties config, Long blockNumber) {
        disableSync(config);

        new Ethash(config, blockNumber).getFullDataset();
        // DAG file has been created, lets exit
        System.exit(0);
    }

    private static Optional<Path> getBlocksDumpPath(SystemProperties config) {
        String blocksLoader = config.blocksLoader();

        if (isEmpty(blocksLoader)) {
            return Optional.empty();
        } else {
            Path path = Paths.get(blocksLoader);
            return Files.exists(path) ? Optional.of(path) : Optional.empty();
        }
    }

    /**
     * Loads single or multiple block dumps from specified path, and terminate program execution.<br>
     * Exit code is 0 in case of successfully dumps loading, 1 otherwise.
     *
     * @param config {@link SystemProperties} config instance;
     * @param path   file system path to dump file or directory that contains dumps;
     */
    private static void loadDumpAndExit(SystemProperties config, Path path) {
        disableSync(config);

        boolean loaded = false;
        try {
            Pattern pattern = Pattern.compile("(\\D+)?(\\d+)?(.*)?");

            Path[] paths = Files.isDirectory(path) ?
                    Files.list(path)
                            .sorted(Comparator.comparingInt(filePath -> {
                                String fileName = filePath.getFileName().toString();
                                Matcher matcher = pattern.matcher(fileName);
                                return matcher.matches() ? toInt(matcher.group(2)) : 0;
                            }))
                            .toArray(Path[]::new)
                    : new Path[]{path};

            loaded = createEthereum().getBlockLoader().loadBlocks(paths);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.exit(loaded ? 0 : 1);
    }
}


/* 一共大概10w行代码
//
//        cli : 负责启动参数的处理

//        config: 负责配置文件注入以及一些bean的注入

//        公共部分：包含系统配置变量(SystemProperties)、配置初始化(Initializer 完成配置的初始化)、仓库、数据源、验证器等的注入

//        blockchain：包含的是一些以太坊发布时各个版本的不同的特性，这些特性包括不限于难度值计算以及其他一些EIP所描述的bug或者features

//        net：包含的是以太坊支持的不同的网络配置，包含主网或者测试网络不同的配置信息，所谓的配置信息就是blockchain配置所描述的一些不同的EIP或者其他的features
//        core: 核心部分，它包含账户、区块、创世块、区块链、transaction、bloom的定义以及区块如何验证、如何加入链以及transaction如何使用vm执行也就是智能合约的执行都在这里完成
//        crypto: 加密工具包含不限于hash算法、ECC算法等
//        datasource: 提供了两种数据源实现内存以及leveldb，并使用者两种数据源扩展了不同实现，这包含缓存数据源、链数据源，依据于此又封装出读写缓存、异步读写缓存以及链存储相关的数据源实现。
//        db: 定义了如何使用datasource存储block、transaction，换句话说就是block、transaction的存储数据结构
//        facade: 包含了ethereum的实现，就是将块存储、验证、同步、合约执行等做的封装
//        mine是挖矿相关的
//        net 涉及的都是网络相关的，以太坊节点发现块同步都是建立在rlpx协议之上，这包含p2p、shh、eth等，另外server包就是节点发现服务启动入口
//        samples是一些测试例子
//        solidity是合约编译部分的实现
//        sync 是负责负责块的同步下载等
//        trie 是以太坊链存储的数据结构，该包主要是实现该数据结构也就是MPT
//        utils 工具类，包含rlp编码等
//        validator 这是一些验证器，在验证block的时候会用到
//        vm 以太坊vm实现
//        start是程序入口


 */