package com.study.network.ip;

import com.study.network.ip.SubnetCalculator.SubnetInfo;
import com.study.network.packet.IpHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 子网划分计算器测试：验证掩码转换、网络/广播地址、主机范围、可用主机数、归属判断与等分子网。
 */
class SubnetCalculatorTest {

    // ---- 前缀长度 <-> 子网掩码 ----

    @Test
    @DisplayName("/24 的子网掩码是 255.255.255.0")
    void prefix24Mask() {
        assertEquals(0xFFFFFF00, SubnetCalculator.prefixToMask(24));
        assertEquals("255.255.255.0", SubnetCalculator.maskToString(SubnetCalculator.prefixToMask(24)));
    }

    @Test
    @DisplayName("常见前缀的掩码：/8 /16 /23 /30 /32 /0 全部正确")
    void commonPrefixMasks() {
        assertEquals("255.0.0.0", SubnetCalculator.maskToString(SubnetCalculator.prefixToMask(8)));
        assertEquals("255.255.0.0", SubnetCalculator.maskToString(SubnetCalculator.prefixToMask(16)));
        assertEquals("255.255.254.0", SubnetCalculator.maskToString(SubnetCalculator.prefixToMask(23)));
        assertEquals("255.255.255.252", SubnetCalculator.maskToString(SubnetCalculator.prefixToMask(30)));
        assertEquals("255.255.255.255", SubnetCalculator.maskToString(SubnetCalculator.prefixToMask(32)));
        assertEquals("0.0.0.0", SubnetCalculator.maskToString(SubnetCalculator.prefixToMask(0)));
    }

    @Test
    @DisplayName("非法前缀长度（-1、33）被拒绝")
    void invalidPrefixRejected() {
        assertThrows(IllegalArgumentException.class, () -> SubnetCalculator.prefixToMask(-1));
        assertThrows(IllegalArgumentException.class, () -> SubnetCalculator.prefixToMask(33));
    }

    // ---- 网络地址与广播地址 ----

    @Test
    @DisplayName("/24 网络地址为 192.168.1.0，广播地址为 192.168.1.255")
    void networkAndBroadcast24() {
        int ip = IpHeader.parseIp("192.168.1.100");
        assertEquals("192.168.1.0", IpHeader.toIpString(SubnetCalculator.networkAddress(ip, 24)));
        assertEquals("192.168.1.255", IpHeader.toIpString(SubnetCalculator.broadcastAddress(ip, 24)));
    }

    @Test
    @DisplayName("传入主机 IP 也能规整出网络地址（主机位清零）")
    void networkAddressNormalizesHostIp() {
        int ip = IpHeader.parseIp("10.20.30.40");
        assertEquals("10.20.30.0", IpHeader.toIpString(SubnetCalculator.networkAddress(ip, 24)));
        assertEquals("10.20.0.0", IpHeader.toIpString(SubnetCalculator.networkAddress(ip, 16)));
    }

    // ---- 可用主机数 ----

    @Test
    @DisplayName("可用主机数 = 2^(32-prefix) - 2：/24 为 254 台")
    void usableHosts24() {
        assertEquals(254, SubnetCalculator.usableHostCount(24));
        assertEquals(65534, SubnetCalculator.usableHostCount(16));
        assertEquals(16777214, SubnetCalculator.usableHostCount(8));
    }

    @Test
    @DisplayName("/30 只有 2 台可用主机（点对点链路经典段）")
    void usableHosts30() {
        assertEquals(2, SubnetCalculator.usableHostCount(30));
        SubnetInfo subnet = new SubnetInfo(IpHeader.parseIp("192.168.1.0"), 30);
        assertEquals("192.168.1.1", IpHeader.toIpString(subnet.firstUsable()));
        assertEquals("192.168.1.2", IpHeader.toIpString(subnet.lastUsable()));
        assertEquals("192.168.1.3", IpHeader.toIpString(subnet.broadcastAddress()));
    }

    @Test
    @DisplayName("/31 可用 2 台（RFC 3021 点对点）、/32 可用 1 台（单主机路由）")
    void usableHostsSpecial() {
        assertEquals(2, SubnetCalculator.usableHostCount(31));
        assertEquals(1, SubnetCalculator.usableHostCount(32));

        SubnetInfo p2p = new SubnetInfo(IpHeader.parseIp("10.0.0.0"), 31);
        assertEquals(2, p2p.usableHosts());
        assertEquals("10.0.0.0", IpHeader.toIpString(p2p.firstUsable()));
        assertEquals("10.0.0.1", IpHeader.toIpString(p2p.lastUsable()));

        SubnetInfo single = new SubnetInfo(IpHeader.parseIp("10.0.0.5"), 32);
        assertEquals(1, single.usableHosts());
        assertEquals("10.0.0.5", IpHeader.toIpString(single.firstUsable()));
        assertEquals("10.0.0.5", IpHeader.toIpString(single.lastUsable()));
    }

    // ---- 归属判断（路由表匹配） ----

    @Test
    @DisplayName("contains：192.168.1.100 属于 192.168.1.0/24，不属于 192.168.2.0/24")
    void containsJudge() {
        int network = IpHeader.parseIp("192.168.1.0");
        assertTrue(SubnetCalculator.contains(network, 24, IpHeader.parseIp("192.168.1.100")));
        assertTrue(SubnetCalculator.contains(network, 24, IpHeader.parseIp("192.168.1.1")));
        assertFalse(SubnetCalculator.contains(network, 24, IpHeader.parseIp("192.168.2.1")));
        assertFalse(SubnetCalculator.contains(network, 24, IpHeader.parseIp("10.0.0.1")));
    }

    @Test
    @DisplayName("contains：网络地址和广播地址本身也算属于本子网")
    void containsIncludesNetworkAndBroadcast() {
        int network = IpHeader.parseIp("192.168.1.0");
        assertTrue(SubnetCalculator.contains(network, 24, network));
        assertTrue(SubnetCalculator.contains(network, 24, IpHeader.parseIp("192.168.1.255")));
    }

    // ---- 等分子网划分 ----

    @Test
    @DisplayName("192.168.1.0/24 等分成 4 个 /26，每个 62 台可用主机")
    void split24Into26() {
        List<SubnetInfo> subnets = SubnetCalculator.split(
                IpHeader.parseIp("192.168.1.0"), 24, 26);
        assertEquals(4, subnets.size());
        assertEquals("192.168.1.0/26", subnets.get(0).cidr());
        assertEquals("192.168.1.64/26", subnets.get(1).cidr());
        assertEquals("192.168.1.128/26", subnets.get(2).cidr());
        assertEquals("192.168.1.192/26", subnets.get(3).cidr());
        for (SubnetInfo subnet : subnets) {
            assertEquals(62, subnet.usableHosts());
        }
        // 每个子网广播地址
        assertEquals("192.168.1.63", IpHeader.toIpString(subnets.get(0).broadcastAddress()));
        assertEquals("192.168.1.255", IpHeader.toIpString(subnets.get(3).broadcastAddress()));
    }

    @Test
    @DisplayName("192.168.1.0/26 等分成 2 个 /27，每个 30 台可用主机")
    void split26Into27() {
        List<SubnetInfo> subnets = SubnetCalculator.split(
                IpHeader.parseIp("192.168.1.0"), 26, 27);
        assertEquals(2, subnets.size());
        assertEquals("192.168.1.0/27", subnets.get(0).cidr());
        assertEquals("192.168.1.32/27", subnets.get(1).cidr());
        assertEquals(30, subnets.get(0).usableHosts());
        assertEquals(30, subnets.get(1).usableHosts());
    }

    @Test
    @DisplayName("划分输入非对齐 IP 时先规整到网络地址：192.168.1.100/24 划分结果不变")
    void splitNormalizesInput() {
        List<SubnetInfo> a = SubnetCalculator.split(IpHeader.parseIp("192.168.1.100"), 24, 26);
        List<SubnetInfo> b = SubnetCalculator.split(IpHeader.parseIp("192.168.1.0"), 24, 26);
        assertEquals(b.size(), a.size());
        assertEquals(b.get(0).cidr(), a.get(0).cidr());
    }

    @Test
    @DisplayName("非法划分被拒绝：目标前缀不能小于原前缀（只能切小不能合并）")
    void splitInvalidRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SubnetCalculator.split(IpHeader.parseIp("192.168.1.0"), 26, 24));
        assertThrows(IllegalArgumentException.class,
                () -> SubnetCalculator.split(IpHeader.parseIp("192.168.1.0"), 24, 33));
    }

    // ---- 私网地址判断（RFC 1918） ----

    @Test
    @DisplayName("私网判断：10/8、172.16/12、192.168/16 三段的典型地址为私网")
    void privateIpDetected() {
        assertTrue(SubnetCalculator.isPrivateIp("10.0.0.1"));
        assertTrue(SubnetCalculator.isPrivateIp("10.255.255.255"));
        assertTrue(SubnetCalculator.isPrivateIp("172.16.0.1"));
        assertTrue(SubnetCalculator.isPrivateIp("172.31.255.255"), "172.16.0.0/12 上限是 172.31.255.255");
        assertTrue(SubnetCalculator.isPrivateIp("192.168.0.1"));
        assertTrue(SubnetCalculator.isPrivateIp("192.168.255.255"));
    }

    @Test
    @DisplayName("公网判断：边界之外是公网（含 /12 段之外的 172.32、100.64 CGNAT 等）")
    void publicIpDetected() {
        assertFalse(SubnetCalculator.isPrivateIp("11.0.0.1"), "10/8 之外");
        assertFalse(SubnetCalculator.isPrivateIp("172.32.0.1"), "172.16/12 之外");
        assertFalse(SubnetCalculator.isPrivateIp("192.169.0.1"), "192.168/16 之外");
        assertFalse(SubnetCalculator.isPrivateIp("8.8.8.8"), "公网 DNS");
        assertFalse(SubnetCalculator.isPrivateIp("100.64.0.1"), "CGNAT 段不算 RFC 1918 私网");
    }

    // ---- 大网等分：10.0.0.0/8 -> 256 个 /16 ----

    @Test
    @DisplayName("10.0.0.0/8 等分成 256 个 /16：第一个 10.0.0.0/16、最后一个 10.255.0.0/16")
    void splitBigNetwork() {
        List<SubnetInfo> subnets = SubnetCalculator.split(
                IpHeader.parseIp("10.0.0.0"), 8, 16);
        assertEquals(256, subnets.size(), "2^(16-8) = 256 个子网");
        assertEquals("10.0.0.0/16", subnets.get(0).cidr());
        assertEquals("10.1.0.0/16", subnets.get(1).cidr());
        assertEquals("10.255.0.0/16", subnets.get(255).cidr(), "最后一个子网");
        for (SubnetInfo subnet : subnets) {
            assertEquals(65534, subnet.usableHosts());
        }
    }

    // ---- 汇总信息 ----

    @Test
    @DisplayName("SubnetInfo 汇总：掩码/广播/主机范围/可用数一次性给出")
    void subnetInfoSummary() {
        SubnetInfo subnet = new SubnetInfo(IpHeader.parseIp("192.168.1.0"), 24);
        assertEquals("255.255.255.0", subnet.maskString());
        assertEquals("192.168.1.255", IpHeader.toIpString(subnet.broadcastAddress()));
        assertEquals("192.168.1.1", IpHeader.toIpString(subnet.firstUsable()));
        assertEquals("192.168.1.254", IpHeader.toIpString(subnet.lastUsable()));
        assertEquals(254, subnet.usableHosts());
        String text = subnet.toString();
        assertTrue(text.contains("192.168.1.0/24"));
        assertTrue(text.contains("可用 254 台"));
    }
}
