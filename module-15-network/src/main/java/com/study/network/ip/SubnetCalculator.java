package com.study.network.ip;

import com.study.network.packet.IpHeader;

import java.util.ArrayList;
import java.util.List;

/**
 * IPv4 子网划分计算器（CIDR 记法）——计算机网络面试必考的计算题。
 *
 * 要解决的核心问题：一个大网络如何切分成多个小网络（子网），路由表如何紧凑地表示它们。
 * 传统分类地址（A/B/C 类）浪费地址且不灵活，CIDR（无类域间路由）用
 * 「网络地址 + 前缀长度」（如 192.168.1.0/24）精确描述任意规模的子网。
 *
 * 关键概念：
 * - 前缀长度 prefix（/24）：32 bit 地址中前 prefix 位是网络位，后 (32-prefix) 位是主机位。
 * - 子网掩码 subnet mask：前 prefix 位为 1、其余为 0 的 32 bit 数字，如 /24 = 255.255.255.0。
 *   路由/主机用「IP & 掩码」取出网络地址，判断目的 IP 是否在本子网。
 * - 网络地址 network address：主机位全 0，标识子网本身，**不能**分配给主机。
 * - 广播地址 broadcast address：主机位全 1，向子网内所有主机发送，**不能**分配给主机。
 * - 可用主机数 usable hosts = 2^(32-prefix) - 2（去掉网络地址和广播地址）。
 *
 * 经典边界：
 * - /30：主机位 2 bit -> 4 个地址，可用 2 台（点对点链路常用，如公网 IP 段）。
 * - /31：可用 2 台（RFC 3021 点对点，无网络/广播地址，用于 PPP 链路）。
 * - /32：可用 1 台（单主机路由，如云上给单台机器配 IP 常用）。
 *
 * 本类演示（复用 IpHeader 的 parseIp/toIpString 做点分十进制转换）：
 * - CIDR 前缀长度 <-> 子网掩码互转
 * - 网络地址 / 广播地址 / 主机范围 / 可用主机数计算
 * - 判断某 IP 是否属于某子网（路由表匹配）
 * - 等分子网划分（如把 /24 切成 4 个 /26）
 */
public class SubnetCalculator {

    private SubnetCalculator() {
    }

    /** 子网掩码 = 前 prefix 位为 1、其余为 0 的 32 bit 整数（可转为 255.255.255.0 形式）。 */
    public static int prefixToMask(int prefix) {
        checkPrefix(prefix);
        // ~0 = 全 1，左移 (32-prefix) 位后高 prefix 位保留为 1，主机位归 0。
        // 例：prefix=24 -> 0xFFFFFF00 -> 255.255.255.0；prefix=32 -> 0xFFFFFFFF。
        return prefix == 0 ? 0 : ~0 << (32 - prefix);
    }

    /** 把 32 bit 掩码格式化为点分十进制（如 255.255.255.0）。 */
    public static String maskToString(int mask) {
        return IpHeader.toIpString(mask);
    }

    /**
     * 私网地址判断（RFC 1918，面试常问三段）：
     * 10.0.0.0/8、172.16.0.0/12、192.168.0.0/16 是私网地址（公网路由器不路由，NAT 出口转换）。
     */
    public static boolean isPrivateIp(int ip) {
        return (ip & 0xFF000000) == 0x0A000000       // 10.x.x.x（10.0.0.0/8）
                || (ip & 0xFFF00000) == 0xAC100000   // 172.16~172.31（172.16.0.0/12）
                || (ip & 0xFFFF0000) == 0xC0A80000;  // 192.168.x.x（192.168.0.0/16）
    }

    /** 私网地址判断（字符串版，如 isPrivateIp("192.168.1.1") = true）。 */
    public static boolean isPrivateIp(String ip) {
        return isPrivateIp(IpHeader.parseIp(ip));
    }

    /** 网络地址 = IP 与上子网掩码（主机位清零）。 */
    public static int networkAddress(int ip, int prefix) {
        checkPrefix(prefix);
        return ip & prefixToMask(prefix);
    }

    /** 广播地址 = 网络地址 或上 主机位全 1（~掩码）。 */
    public static int broadcastAddress(int ip, int prefix) {
        checkPrefix(prefix);
        return ip | ~prefixToMask(prefix);
    }

    /**
     * 可用主机数 = 2^(32-prefix) - 2（去掉网络地址与广播地址）。
     * 特殊前缀：/31 与 /32 按 RFC 3021 / 单主机处理，分别可用 2、1 台。
     */
    public static long usableHostCount(int prefix) {
        checkPrefix(prefix);
        long addresses = 1L << (32 - prefix); // 用 long 避免 1<<32 溢出为 1
        if (prefix >= 31) {
            return prefix == 31 ? 2 : 1; // /31 点对点可用 2 台，/32 单主机可用 1 台
        }
        return addresses - 2;
    }

    /** 判断某 IP 是否属于该子网：IP & 掩码 == 网络地址（网络地址与广播地址也算属于本子网）。 */
    public static boolean contains(int ip, int prefix, int candidate) {
        return networkAddress(ip, prefix) == networkAddress(candidate, prefix);
    }

    /**
     * 一个子网的完整信息：网络地址 + 前缀长度，其余字段按需计算。
     * 便于一次性查看「掩码 / 广播地址 / 主机范围 / 可用主机数」。
     */
    public record SubnetInfo(int networkAddress, int prefix) {

        /** CIDR 记法，如 192.168.1.0/24。 */
        public String cidr() {
            return IpHeader.toIpString(networkAddress) + "/" + prefix;
        }

        public int mask() {
            return SubnetCalculator.prefixToMask(prefix);
        }

        public String maskString() {
            return maskToString(mask());
        }

        public int broadcastAddress() {
            return SubnetCalculator.broadcastAddress(networkAddress, prefix);
        }

        public long usableHosts() {
            return SubnetCalculator.usableHostCount(prefix);
        }

        /** 第一个可用主机地址（/31、/32 无网络/广播地址，直接返回网络地址）。 */
        public int firstUsable() {
            if (prefix >= 31) {
                return networkAddress;
            }
            return networkAddress + 1;
        }

        /** 最后一个可用主机地址（/31、/32 无网络/广播地址，直接返回广播地址）。 */
        public int lastUsable() {
            if (prefix >= 31) {
                return broadcastAddress();
            }
            return broadcastAddress() - 1;
        }

        @Override
        public String toString() {
            return cidr() + " [掩码 " + maskString() + ", 网络 " + IpHeader.toIpString(networkAddress)
                    + ", 广播 " + IpHeader.toIpString(broadcastAddress())
                    + ", 主机 " + IpHeader.toIpString(firstUsable()) + "~"
                    + IpHeader.toIpString(lastUsable())
                    + ", 可用 " + usableHosts() + " 台]";
        }
    }

    /**
     * 等分子网划分：把 network/prefix 平均切成 2^(targetPrefix-prefix) 个更小的子网。
     *
     * @param network      待划分网络的网络地址（主机位必须为 0，内部会规整）
     * @param prefix       原网络前缀长度（如 24）
     * @param targetPrefix 目标子网前缀长度（必须 ≥ prefix，如 26 -> 切成 4 个）
     * @return 依次排列的子网列表（每个子网跨度 = 2^(32-targetPrefix) 个地址）
     */
    public static List<SubnetInfo> split(int network, int prefix, int targetPrefix) {
        checkPrefix(prefix);
        checkPrefix(targetPrefix);
        if (targetPrefix < prefix) {
            throw new IllegalArgumentException("目标前缀 " + targetPrefix
                    + " 不能小于原前缀 " + prefix + "（子网只能切得更小，不能合并）");
        }
        long base = Integer.toUnsignedLong(networkAddress(network, prefix));
        long subnetSize = 1L << (32 - targetPrefix);
        long count = 1L << (targetPrefix - prefix);
        List<SubnetInfo> subnets = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            int networkAddress = (int) (base + i * subnetSize);
            subnets.add(new SubnetInfo(networkAddress, targetPrefix));
        }
        return subnets;
    }

    /** 打印子网划分演示（供 Main 调用）：/24 的完整信息 + 等分成 4 个 /26。 */
    public static void printSubnetDemo() {
        System.out.println("================ 子网划分（CIDR）演示 ================");
        SubnetInfo whole = new SubnetInfo(IpHeader.parseIp("192.168.1.0"), 24);
        System.out.println("  原网络: " + whole);
        System.out.println("  可用主机数公式: 2^(32-24) - 2 = 254 台");

        System.out.println("  等分成 4 个 /26（每个 64 个地址、可用 62 台）:");
        for (SubnetInfo subnet : split(whole.networkAddress(), 24, 26)) {
            System.out.println("    " + subnet);
        }

        // 归属判断演示：路由表匹配
        int target = IpHeader.parseIp("192.168.1.130");
        System.out.println("  归属判断: 192.168.1.130 属于 192.168.1.128/26 -> "
                + contains(IpHeader.parseIp("192.168.1.128"), 26, target)
                + "，属于 192.168.1.0/24 -> "
                + contains(IpHeader.parseIp("192.168.1.0"), 24, target));
        System.out.println();
    }

    private static void checkPrefix(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("前缀长度必须在 0~32 之间: " + prefix);
        }
    }
}
