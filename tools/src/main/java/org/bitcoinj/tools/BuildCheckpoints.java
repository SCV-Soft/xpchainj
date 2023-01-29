/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.tools;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Network;
import org.bitcoinj.utils.Threading;
import picocli.CommandLine;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Downloads and verifies a full chain from your local peer, emitting checkpoints at each difficulty transition period
 * to a file which is then signed with your key.
 */
@CommandLine.Command(name = "build-checkpoints", usageHelpAutoWidth = true, sortOptions = false, description = "Create checkpoint files to use with CheckpointManager.")
public class BuildCheckpoints implements Callable<Integer> {
    @CommandLine.Option(names = "--net", description = "Which network to connect to. Valid values: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}")
    private Network net = Network.MAIN;
    @CommandLine.Option(names = "--peer", description = "IP address/domain name for connection instead of localhost.")
    private String peer = null;
    @CommandLine.Option(names = "--days", description = "How many days to keep as a safety margin. Checkpointing will be done up to this many days ago.")
    private int days = 7;
    @CommandLine.Option(names = "--help", usageHelp = true, description = "Displays program options.")
    private boolean help;

    private static NetworkParameters params;

    public static void main(String[] args) throws Exception {
        BriefLogFormatter.initWithSilentBitcoinJ();
        int exitCode = new CommandLine(new BuildCheckpoints()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        final String suffix;
        params = net.networkParameters();
        Context.propagate(new Context(params));

        switch (net) {
            case MAIN:
                suffix = "";
                break;
            case TEST:
                suffix = "-testnet";
                break;
            case SIGNET:
                suffix = "-signet";
                break;
            case REGTEST:
                suffix = "-regtest";
                break;
            default:
                throw new RuntimeException("Unreachable.");
        }

        // Configure bitcoinj to fetch only headers, not save them to disk, connect to a local fully synced/validated
        // node and to save block headers that are on interval boundaries, as long as they are <1 month old.
        final BlockStore store = new MemoryBlockStore(params);
        final BlockChain chain = new BlockChain(params, store);
        final PeerGroup peerGroup = new PeerGroup(params, chain);

        final InetAddress ipAddress;

        // DNS discovery can be used for some networks
        boolean networkHasDnsSeeds = params.getDnsSeeds() != null;
        if (peer != null) {
            // use peer provided in argument
            try {
                ipAddress = InetAddress.getByName(peer);
                startPeerGroup(peerGroup, ipAddress);
            } catch (UnknownHostException e) {
                System.err.println("Could not understand peer domain name/IP address: " + peer + ": " + e.getMessage());
                return 1;
            }
        } else if (networkHasDnsSeeds) {
            // use a peer group discovered with dns
            peerGroup.setUserAgent("PeerMonitor", "1.0");
            peerGroup.setMaxConnections(20);
            peerGroup.addPeerDiscovery(new DnsDiscovery(params));
            //String array[] = {};

            {
                InetAddress ipAddr = InetAddress.getLocalHost();
                final PeerAddress peerAddress = new PeerAddress(params, ipAddr);
                System.out.println("Connecting to " + peerAddress + "...");
                peerGroup.addAddress(peerAddress);
            }

            String array[] = {"113.38.187.34", "45.77.37.235", "159.28.238.184", "158.247.207.5", "119.171.52.54", "202.182.125.164", "110.131.217.146", "101.140.231.41"};
            for(String addr : array) {
                InetAddress ipAddr = InetAddress.getByName(addr);
                final PeerAddress peerAddress = new PeerAddress(params, ipAddr);
                System.out.println("Connecting to " + peerAddress + "...");
                peerGroup.addAddress(peerAddress);
            }
            peerGroup.start();

            // Connect to at least 4 peers because some may not support download
            Future<List<Peer>> future = peerGroup.waitForPeers(1);
            System.out.println("Connecting to " + params.getId() + ", timeout 20 seconds...");
            // throw timeout exception if we can't get peers
            future.get(20, SECONDS);
        } else {
            // try localhost
            System.out.println("Use Localhost");
            ipAddress = InetAddress.getLocalHost();
            startPeerGroup(peerGroup, ipAddress);
        }

        // Sorted map of block height to StoredBlock object.
        final TreeMap<Integer, StoredBlock> checkpoints = new TreeMap<>();

        long now = new Date().getTime() / 1000;
        peerGroup.setFastCatchupTimeSecs(now);

        final long timeAgo = now - (86400 * days);
        System.out.println("Checkpointing up to " + Utils.dateTimeFormat(timeAgo * 1000));

        chain.addNewBestBlockListener(Threading.SAME_THREAD, block -> {
            int height = block.getHeight();
            System.out.println(String.format("Got %d", height));
            if (height % params.getInterval() == 0 && block.getHeader().getTimeSeconds() <= timeAgo) {
                System.out.println(String.format("Checkpointing block %s at height %d, time %s",
                        block.getHeader().getHash(), block.getHeight(), Utils.dateTimeFormat(block.getHeader().getTime())));
                checkpoints.put(height, block);
            }
        });

        System.out.println("222");
        /*
        System.out.println(peerGroup.getConnectedPeers().size());
        System.out.println(peerGroup.getConnectedPeers());
        System.out.println(peerGroup.getDownloadPeer());
        System.out.println(peerGroup.getMostCommonChainHeight());
        System.out.println(peerGroup.getUseLocalhostPeerWhenPossible());
        System.out.println(peerGroup.getMaxPeersToDiscoverCount());
        */


        System.out.println(peerGroup.getDownloadPeer());
        // peerGroup.downloadBlockChain();
        String[][] Blocks = {
                {"61", "000000000b0ee5a16f76f6be928209d7da8f6562050097a2bfca7d73df671706"},
                {"122", "0000000017bf38e29cfd15e82c11eb7decad65291293c258f1a2b0d332fc64c2"},
                {"244", "000000008f99ac18f4624dd10e3ec46cc048cbd8cef2b5a61535109c4736fbcd"},
                {"488", "000000000c36fbaa58d3b7be796bb405aa2cc7c03827ae01f2d822b116b5cab8"},
                {"976", "0000000063e39df42295a29308119136e24d038aad39e250b00812278cefd547"},
                {"1953", "000000007df64249ddab9870409019d1517e61ab05348905d6e9c0bc56ce178c"},
                {"3906", "00000000506de4591511ffab2da9d00368e4e44ee4fcdc40dd132f433ecaaeb7"},
                {"7812", "00000000057515ad06707849c536de1c01955a7b1db4a08a159b5af0fd5877a3"},
                {"15625", "215cbb50779eba4164b3acba18c752c3e91c76c0fe5061b6a11882e31a9ad3dd"},
                {"31250", "b42c024cfd666f94289e27641d085beea37b896ada0ddf3f18065978b76e5838"},
                {"62500", "6d34ed31e60fec151676bbcc8034ff744d8cda538d9b16506c8e3ddf5aca8678"},
                {"125000", "542c03e87d37b383a6f6b67dc4df57e606e6f75b4ce2f1a818008891f9b3673a"},
                {"250000", "aac445d7b9565a3a13e4d4e5dbc98ca62dfa1d192e4fe7ef945955dda9897e8b"},
                {"500000", "8205c3d59681b38035a04b21c8bd991500341ca255b619704cbbbbef692efb22"},
                {"1000000", "564ca33540d8f4b1ca7e0c6a41b981d3f461f7cd059e99204721bb9777eebc99"},
                {"2000000", "1ad334e6854a76cd36ec2b026486f786464d37d4ede80d752ccf8d2069b14b93"},
                {"2005000", "8228c32d084f683ff81989f0ac7e482b9c447040bf86c95853cbcd3c0e522f77"},
                {"2010000", "80d14eac04fba7be3f011bf774cb3f2b1cbf22d40caff8f5cfa5d545a7c8b31d"},
                {"2015000", "aed9dd372dc5cc0826aa4c7fd0d1a8a3ea9ec005e42605452e11498dab8d05e0"},
                {"2020000", "ee047613cf2bca1819fe327115dc11a1fcba177b59ae38c9a5aa3a23b09147ec"},
                {"2025000", "2e1da93ce1b07f64099abe0b7ec932bfa296e65da4c447f5e4b68a2db54634cb"},
                {"2030000", "af32ce35a6d10d527c98ea0bf4213af1d6c5cb209cd3529cfff22c9e0851979e"},
                //{"2037000", "ad2992d0cfefc6071f5ab6e80d856f55b522b4794931eb37617ec5f5015eb52e"},
        };

        Peer downloadPeer = peerGroup.getDownloadPeer();
        for(String[] blk : Blocks) {
            Sha256Hash blockHash = Sha256Hash.wrap(blk[1]);
            Future<Block> future = downloadPeer.getBlock(blockHash);
            System.out.println("Waiting for node to send us the requested block: " + blockHash);
            Block block = future.get();
            System.out.println(block);
            System.out.println(String.format("Checkpointing block %s at height %d, time %s",
                    block.getHash(), Integer.parseInt(blk[0]), Utils.dateTimeFormat(block.getTime())));
            System.out.println(block);
            StoredBlock sBlock = new StoredBlock(block, block.getWork(), Integer.parseInt(blk[0]));
            checkpoints.put(Integer.parseInt(blk[0]), sBlock);
        }


        System.out.println("333");
        checkState(checkpoints.size() > 0);
        System.out.println("444");

        final File plainFile = new File("checkpoints" + suffix);
        final File textFile = new File("checkpoints" + suffix + ".txt");

        // Write checkpoint data out.
        writeBinaryCheckpoints(checkpoints, plainFile);
        writeTextualCheckpoints(checkpoints, textFile);

        peerGroup.stop();
        store.close();

        // Sanity check the created files.
        sanityCheck(plainFile, checkpoints.size());
        sanityCheck(textFile, checkpoints.size());

        return 0;
    }

    private static void writeBinaryCheckpoints(TreeMap<Integer, StoredBlock> checkpoints, File file) throws Exception {
        MessageDigest digest = Sha256Hash.newDigest();
        try (FileOutputStream fileOutputStream = new FileOutputStream(file, false);
                DigestOutputStream digestOutputStream = new DigestOutputStream(fileOutputStream, digest);
                DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream)) {
            digestOutputStream.on(false);
            dataOutputStream.writeBytes("CHECKPOINTS 1");
            dataOutputStream.writeInt(0); // Number of signatures to read. Do this later.
            digestOutputStream.on(true);
            dataOutputStream.writeInt(checkpoints.size());
            ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
            for (StoredBlock block : checkpoints.values()) {
                block.serializeCompact(buffer);
                dataOutputStream.write(buffer.array());
                ((Buffer) buffer).position(0);
            }
            Sha256Hash checkpointsHash = Sha256Hash.wrap(digest.digest());
            System.out.println("Hash of checkpoints data is " + checkpointsHash);
            System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
        }
    }

    private static void writeTextualCheckpoints(TreeMap<Integer, StoredBlock> checkpoints, File file)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.US_ASCII))) {
            writer.println("TXT CHECKPOINTS 1");
            writer.println("0"); // Number of signatures to read. Do this later.
            writer.println(checkpoints.size());
            ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
            for (StoredBlock block : checkpoints.values()) {
                block.serializeCompact(buffer);
                writer.println(CheckpointManager.BASE64.encode(buffer.array()));
                ((Buffer) buffer).position(0);
            }
            System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
        }
    }

    private static void sanityCheck(File file, int expectedSize) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        CheckpointManager manager;
        try {
            manager = new CheckpointManager(params, fis);
        } finally {
            fis.close();
        }

        checkState(manager.numCheckpoints() == expectedSize);

        /*
        if (params.getId().equals(NetworkParameters.ID_MAINNET)) {
            StoredBlock test = manager.getCheckpointBefore(1390500000); // Thu Jan 23 19:00:00 CET 2014
            checkState(test.getHeight() == 280224);
            checkState(test.getHeader().getHashAsString()
                    .equals("00000000000000000b5d59a15f831e1c45cb688a4db6b0a60054d49a9997fa34"));
        } else if (params.getId().equals(NetworkParameters.ID_TESTNET)) {
            StoredBlock test = manager.getCheckpointBefore(1390500000); // Thu Jan 23 19:00:00 CET 2014
            checkState(test.getHeight() == 167328);
            checkState(test.getHeader().getHashAsString()
                    .equals("0000000000035ae7d5025c2538067fe7adb1cf5d5d9c31b024137d9090ed13a9"));
        } else if (params.getId().equals(NetworkParameters.ID_SIGNET)) {
            StoredBlock test = manager.getCheckpointBefore(1642000000); // 2022-01-12
            checkState(test.getHeight() == 72576);
            checkState(test.getHeader().getHashAsString()
                    .equals("0000008f763bdf23bd159a21ccf211098707671d2ca9aa72d0f586c24505c5e7"));
        }
        */
    }

    private static void startPeerGroup(PeerGroup peerGroup, InetAddress ipAddress) {
        final PeerAddress peerAddress = new PeerAddress(params, ipAddress);
        System.out.println("Connecting to " + peerAddress + "...");
        peerGroup.addAddress(peerAddress);
        peerGroup.start();
    }
}
