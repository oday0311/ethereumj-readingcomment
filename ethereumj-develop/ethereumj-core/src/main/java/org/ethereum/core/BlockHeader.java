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
package org.ethereum.core;

import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.*;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.crypto.HashUtil.EMPTY_LIST_HASH;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.toHexString;

/**
 * 先从区块开始。跟比特币一样，以太坊的区块结构也分为区块头和区块体，这一篇我们先讲区块头。以太坊的区块头包含15个字段：
 *
 * ParentHash
 * 这是上一个区块的哈希值，跟比特币一样，我们可以把它看成一个指针，指向上一个区块，正是有这个指针，区块和区块才串联起来，才有区块链。
 *
 * Coinbase
 * 在比特币里也有一个coinbase，但那个coinbase是指一笔特殊的交易，就是系统奖励比特币给区块创建者的那笔交易。但在以太坊这里，是区块创建者留下的以太坊地址，用于接收系统奖励和交易手续费。
 *
 * UncleHash
 * 以太坊有一个独特的东西，叫叔区块，咱们下一篇会仔细讲讲。现在只要知道这个字段就是所有叔区块用RLP编码后再哈希出来的值即可。
 *
 * Root
 * 这是一棵MPT树的根哈希，这棵树存储了所有以太坊账户。
 *
 * TxHash
 * 这也是一棵MPT树的根哈希，这棵树存储了所有的交易信息。
 *
 * ReceiptHash
 * 这还是一棵MPT树的根哈希。对于以太坊账户和交易我们都已讲过，MPT我们也讲过，用MPT树来存储它们好理解。那这棵树又是存什么的呢？其实，这棵树存储的是收据信息。什么是收据？就是交易完成后会提供一个清单给你，告诉你一些信息：比如这笔交易被哪个区块打包了，这笔交易最终花费了多少gas、执行交易时创建的一些日志等等。
 *
 * Bloom
 * 我们可以在合约中通过定义“事件”来生成日志。上面说了，在收据树里会存储一些日志，这个bloom其实是一个过滤器，通过这个过滤器可以快速搜索和判断某个日志是不是存在于收据中。
 *
 * Difficulty、 Nonce、mixHash
 * 这三个字段都和以太坊的挖矿有关，以太坊和比特币一样，也是POW模式，所以它也有一个挖矿难度系数，这个系数会根据出块速度来进行调整。以太坊第一个区块的难度是131,072，后面区块的难度会根据前面区块出块的速度调整，出得快难度就调高一点，出得慢就调低一点。
 *
 * Difficulty就是区块的难度系数，Nonce是目标值，Nonce值小于等于2^256/Difficulty。所以，难度值越高，目标值的范围越窄，要找到符合的就越难。以太坊具体的挖矿计算比比特币复杂得多，但大概的流程就是不断尝试不同的mixHash来获得符合条件的Nonce。所以，mixHash可以简单理解为比特币区块头里的随机值。
 *
 * Number
 * 区块的序号，每个区块的序号就是在父区块的序号上加1。
 *
 * Time
 * 区块生成的时间。这个时间不是那么精确地就是区块真正生成的时间，有可能就是父区块的生成时间加上10秒，有可能就是区块产生时的“大概”时间。
 *
 * GasLimit
 * 区块内所有Gas消耗的理论上限。这个理论值与父区块有关，它允许打包区块的矿工根据父区块的情况对这些值做些微调。每个区块在产生时就必须设定这么一个gas消耗的理论上限，这个上限值限定了一个区块打包交易的总量，比如一个区块的上限值设定为10000，现在有3笔交易的gas设定分别都是5000，那么这个区块就最多打包其中的两笔交易，如果硬要打包3笔，其他节点就不会认这个区块的。
 *
 * 可以这么说，这个字段限定了一个区块的存储规模，但仍保有一定弹性。这一点与比特币不同，比特币的一个区块是多大，直接写死在比特币软件里，要更改的话只能通过硬分叉。
 *
 * GasUsed
 * 区块内所有交易执行完后所实际消耗的gas总量。
 *
 * extraData
 * 这个字段是留给区块的创建者，让他可以记录一些与该区块有关的信息，长度小于等于32字节即可。
 * Block header is a value object containing
 * the basic information of a block
 */
public class BlockHeader {

    public static final int NONCE_LENGTH = 8;
    public static final int HASH_LENGTH = 32;
    public static final int ADDRESS_LENGTH = 20;
    public static final int MAX_HEADER_SIZE = 800;

    /* The SHA3 256-bit hash of the parent block, in its entirety */
    private byte[] parentHash;
    /* The SHA3 256-bit hash of the uncles list portion of this block */
    private byte[] unclesHash;
    /* The 160-bit address to which all fees collected from the
     * successful mining of this block be transferred; formally */
    private byte[] coinbase;
    /* The SHA3 256-bit hash of the root node of the state trie,
     * after all transactions are executed and finalisations applied */
    private byte[] stateRoot;
    /* The SHA3 256-bit hash of the root node of the trie structure
     * populated with each transaction in the transaction
     * list portion, the trie is populate by [key, val] --> [rlp(index), rlp(tx_recipe)]
     * of the block */
    private byte[] txTrieRoot;
    /* The SHA3 256-bit hash of the root node of the trie structure
     * populated with each transaction recipe in the transaction recipes
     * list portion, the trie is populate by [key, val] --> [rlp(index), rlp(tx_recipe)]
     * of the block */
    private byte[] receiptTrieRoot;
    /* The Bloom filter composed from indexable information 
     * (logger address and log topics) contained in each log entry 
     * from the receipt of each transaction in the transactions list */
    private byte[] logsBloom;
    /* A scalar value corresponding to the difficulty level of this block.
     * This can be calculated from the previous block’s difficulty level
     * and the timestamp */
    private byte[] difficulty;
    /* A scalar value equal to the reasonable output of Unix's time()
     * at this block's inception */
    private long timestamp;
    /* A scalar value equal to the number of ancestor blocks.
     * The genesis block has a number of zero */
    private long number;
    /* A scalar value equal to the current limit of gas expenditure per block */
    private byte[] gasLimit;
    /* A scalar value equal to the total gas used in transactions in this block */
    private long gasUsed;

    /* A 256-bit hash which proves that a sufficient amount
     * of computation has been carried out on this block */
    private byte[] mixHash;

    /* An arbitrary byte array containing data relevant to this block.
     * With the exception of the genesis block, this must be 32 bytes or fewer */
    private byte[] extraData;
    /* A 64-bit value which, combined with the mix-hash, 
     * proves that a sufficient amount of computation has been carried out on this block  */
    private byte[] nonce;

    private byte[] hashCache;

    public BlockHeader(byte[] encoded) {
        this((RLPList) RLP.decode2(encoded).get(0));
    }

    public BlockHeader(RLPList rlpHeader) {

        this.parentHash = rlpHeader.get(0).getRLPData();
        this.unclesHash = rlpHeader.get(1).getRLPData();
        this.coinbase = rlpHeader.get(2).getRLPData();
        this.stateRoot = rlpHeader.get(3).getRLPData();

        this.txTrieRoot = rlpHeader.get(4).getRLPData();
        if (this.txTrieRoot == null)
            this.txTrieRoot = EMPTY_TRIE_HASH;

        this.receiptTrieRoot = rlpHeader.get(5).getRLPData();
        if (this.receiptTrieRoot == null)
            this.receiptTrieRoot = EMPTY_TRIE_HASH;

        this.logsBloom = rlpHeader.get(6).getRLPData();
        this.difficulty = rlpHeader.get(7).getRLPData();

        byte[] nrBytes = rlpHeader.get(8).getRLPData();
        byte[] glBytes = rlpHeader.get(9).getRLPData();
        byte[] guBytes = rlpHeader.get(10).getRLPData();
        byte[] tsBytes = rlpHeader.get(11).getRLPData();

        this.number = ByteUtil.byteArrayToLong(nrBytes);

        this.gasLimit = glBytes;
        this.gasUsed = ByteUtil.byteArrayToLong(guBytes);
        this.timestamp = ByteUtil.byteArrayToLong(tsBytes);

        this.extraData = rlpHeader.get(12).getRLPData();
        this.mixHash = rlpHeader.get(13).getRLPData();
        this.nonce = rlpHeader.get(14).getRLPData();
    }

    public BlockHeader(byte[] parentHash, byte[] unclesHash, byte[] coinbase,
                       byte[] logsBloom, byte[] difficulty, long number,
                       byte[] gasLimit, long gasUsed, long timestamp,
                       byte[] extraData, byte[] mixHash, byte[] nonce) {
        this.parentHash = parentHash;
        this.unclesHash = unclesHash;
        this.coinbase = coinbase;
        this.logsBloom = logsBloom;
        this.difficulty = difficulty;
        this.number = number;
        this.gasLimit = gasLimit;
        this.gasUsed = gasUsed;
        this.timestamp = timestamp;
        this.extraData = extraData;
        this.mixHash = mixHash;
        this.nonce = nonce;
        this.stateRoot = EMPTY_TRIE_HASH;
    }

    public boolean isGenesis() {
        return this.getNumber() == Genesis.NUMBER;
    }

    public byte[] getParentHash() {
        return parentHash;
    }

    public byte[] getUnclesHash() {
        return unclesHash;
    }

    public void setUnclesHash(byte[] unclesHash) {
        this.unclesHash = unclesHash;
        hashCache = null;
    }

    public byte[] getCoinbase() {
        return coinbase;
    }

    public void setCoinbase(byte[] coinbase) {
        this.coinbase = coinbase;
        hashCache = null;
    }

    public byte[] getStateRoot() {
        return stateRoot;
    }

    public void setStateRoot(byte[] stateRoot) {
        this.stateRoot = stateRoot;
        hashCache = null;
    }

    public byte[] getTxTrieRoot() {
        return txTrieRoot;
    }

    public void setReceiptsRoot(byte[] receiptTrieRoot) {
        this.receiptTrieRoot = receiptTrieRoot;
        hashCache = null;
    }

    public byte[] getReceiptsRoot() {
        return receiptTrieRoot;
    }

    public void setTransactionsRoot(byte[] stateRoot) {
        this.txTrieRoot = stateRoot;
        hashCache = null;
    }


    public byte[] getLogsBloom() {
        return logsBloom;
    }

    public byte[] getDifficulty() {
        return difficulty;
    }

    public BigInteger getDifficultyBI() {
        return new BigInteger(1, difficulty);
    }


    public void setDifficulty(byte[] difficulty) {
        this.difficulty = difficulty;
        hashCache = null;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        hashCache = null;
    }

    public long getNumber() {
        return number;
    }

    public void setNumber(long number) {
        this.number = number;
        hashCache = null;
    }

    public byte[] getGasLimit() {
        return gasLimit;
    }

    public void setGasLimit(byte[] gasLimit) {
        this.gasLimit = gasLimit;
        hashCache = null;
    }

    public long getGasUsed() {
        return gasUsed;
    }

    public void setGasUsed(long gasUsed) {
        this.gasUsed = gasUsed;
        hashCache = null;
    }

    public byte[] getMixHash() {
        return mixHash;
    }

    public void setMixHash(byte[] mixHash) {
        this.mixHash = mixHash;
        hashCache = null;
    }

    public byte[] getExtraData() {
        return extraData;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
        hashCache = null;
    }

    public void setLogsBloom(byte[] logsBloom) {
        this.logsBloom = logsBloom;
        hashCache = null;
    }

    public void setExtraData(byte[] extraData) {
        this.extraData = extraData;
        hashCache = null;
    }

    public byte[] getHash() {
        if (hashCache == null) {
            hashCache = HashUtil.sha3(getEncoded());
        }
        return hashCache;
    }

    public byte[] getEncoded() {
        return this.getEncoded(true); // with nonce
    }

    public byte[] getEncodedWithoutNonce() {
        return this.getEncoded(false);
    }

    public byte[] getEncoded(boolean withNonce) {
        byte[] parentHash = RLP.encodeElement(this.parentHash);

        byte[] unclesHash = RLP.encodeElement(this.unclesHash);
        byte[] coinbase = RLP.encodeElement(this.coinbase);

        byte[] stateRoot = RLP.encodeElement(this.stateRoot);

        if (txTrieRoot == null) this.txTrieRoot = EMPTY_TRIE_HASH;
        byte[] txTrieRoot = RLP.encodeElement(this.txTrieRoot);

        if (receiptTrieRoot == null) this.receiptTrieRoot = EMPTY_TRIE_HASH;
        byte[] receiptTrieRoot = RLP.encodeElement(this.receiptTrieRoot);

        byte[] logsBloom = RLP.encodeElement(this.logsBloom);
        byte[] difficulty = RLP.encodeBigInteger(new BigInteger(1, this.difficulty));
        byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        byte[] gasLimit = RLP.encodeElement(this.gasLimit);
        byte[] gasUsed = RLP.encodeBigInteger(BigInteger.valueOf(this.gasUsed));
        byte[] timestamp = RLP.encodeBigInteger(BigInteger.valueOf(this.timestamp));

        byte[] extraData = RLP.encodeElement(this.extraData);
        if (withNonce) {
            byte[] mixHash = RLP.encodeElement(this.mixHash);
            byte[] nonce = RLP.encodeElement(this.nonce);
            return RLP.encodeList(parentHash, unclesHash, coinbase,
                    stateRoot, txTrieRoot, receiptTrieRoot, logsBloom, difficulty, number,
                    gasLimit, gasUsed, timestamp, extraData, mixHash, nonce);
        } else {
            return RLP.encodeList(parentHash, unclesHash, coinbase,
                    stateRoot, txTrieRoot, receiptTrieRoot, logsBloom, difficulty, number,
                    gasLimit, gasUsed, timestamp, extraData);
        }
    }

    public byte[] getUnclesEncoded(List<BlockHeader> uncleList) {

        byte[][] unclesEncoded = new byte[uncleList.size()][];
        int i = 0;
        for (BlockHeader uncle : uncleList) {
            unclesEncoded[i] = uncle.getEncoded();
            ++i;
        }
        return RLP.encodeList(unclesEncoded);
    }

    public byte[] getPowBoundary() {
        return BigIntegers.asUnsignedByteArray(32, BigInteger.ONE.shiftLeft(256).divide(getDifficultyBI()));
    }

    public byte[] calcPowValue() {

        // nonce bytes are expected in Little Endian order, reverting
        byte[] nonceReverted = Arrays.reverse(nonce);
        byte[] hashWithoutNonce = HashUtil.sha3(getEncodedWithoutNonce());

        byte[] seed = Arrays.concatenate(hashWithoutNonce, nonceReverted);
        byte[] seedHash = HashUtil.sha512(seed);

        byte[] concat = Arrays.concatenate(seedHash, mixHash);
        return HashUtil.sha3(concat);
    }

    public BigInteger calcDifficulty(BlockchainNetConfig config, BlockHeader parent) {
        return config.getConfigForBlock(getNumber()).
                calcDifficulty(this, parent);
    }

    public boolean hasUncles() {
        return !FastByteComparisons.equal(unclesHash, EMPTY_LIST_HASH);
    }

    public String toString() {
        return toStringWithSuffix("\n");
    }

    private String toStringWithSuffix(final String suffix) {
        StringBuilder toStringBuff = new StringBuilder();
        toStringBuff.append("  hash=").append(toHexString(getHash())).append(suffix);
        toStringBuff.append("  parentHash=").append(toHexString(parentHash)).append(suffix);
        toStringBuff.append("  unclesHash=").append(toHexString(unclesHash)).append(suffix);
        toStringBuff.append("  coinbase=").append(toHexString(coinbase)).append(suffix);
        toStringBuff.append("  stateRoot=").append(toHexString(stateRoot)).append(suffix);
        toStringBuff.append("  txTrieHash=").append(toHexString(txTrieRoot)).append(suffix);
        toStringBuff.append("  receiptsTrieHash=").append(toHexString(receiptTrieRoot)).append(suffix);
        toStringBuff.append("  difficulty=").append(toHexString(difficulty)).append(suffix);
        toStringBuff.append("  number=").append(number).append(suffix);
//        toStringBuff.append("  gasLimit=").append(gasLimit).append(suffix);
        toStringBuff.append("  gasLimit=").append(toHexString(gasLimit)).append(suffix);
        toStringBuff.append("  gasUsed=").append(gasUsed).append(suffix);
        toStringBuff.append("  timestamp=").append(timestamp).append(" (").append(Utils.longToDateTime(timestamp)).append(")").append(suffix);
        toStringBuff.append("  extraData=").append(toHexString(extraData)).append(suffix);
        toStringBuff.append("  mixHash=").append(toHexString(mixHash)).append(suffix);
        toStringBuff.append("  nonce=").append(toHexString(nonce)).append(suffix);
        return toStringBuff.toString();
    }

    public String toFlatString() {
        return toStringWithSuffix("");
    }

    public String getShortDescr() {
        return "#" + getNumber() + " (" + Hex.toHexString(getHash()).substring(0,6) + " <~ "
                + Hex.toHexString(getParentHash()).substring(0,6) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockHeader that = (BlockHeader) o;
        return FastByteComparisons.equal(getHash(), that.getHash());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getHash());
    }
}
