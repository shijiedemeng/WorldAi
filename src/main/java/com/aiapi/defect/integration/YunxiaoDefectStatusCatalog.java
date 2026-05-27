package com.aiapi.defect.integration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class YunxiaoDefectStatusCatalog {

    private static final List<StatusOption> OPTIONS = List.of(
            new StatusOption("100005", "待处理"),
            new StatusOption("30", "再次打开"),
            new StatusOption("28", "待确认"),
            new StatusOption("34", "推迟修复"),
            new StatusOption("32", "已确认"),
            new StatusOption("625489", "已选择"),
            new StatusOption("154395", "分析中"),
            new StatusOption("165115", "分析完成"),
            new StatusOption("100010", "处理中"),
            new StatusOption("156603", "设计中"),
            new StatusOption("307012", "设计完成"),
            new StatusOption("142838", "开发中"),
            new StatusOption("100011", "开发完成"),
            new StatusOption("81a92359dc3fe5972f6ddb697b", "待开发"),
            new StatusOption("8cef8403e6a931e8968e3cb998", "內部測試不通過"),
            new StatusOption("7951ae737920a3aea9b6164e08", "待联调"),
            new StatusOption("8359be47f28d4e781b0e6291b6", "联调中"),
            new StatusOption("100012", "测试中"),
            new StatusOption("100013", "测试完成"),
            new StatusOption("f91b78b350e638b1787b7eda4c", "內部測試通過"),
            new StatusOption("a4f23e7124e8499f7ce69e3f65", "待测试"),
            new StatusOption("a8740ffc3d18733428399bad51", "內部測試"),
            new StatusOption("0f56e23acd8a2c18132a268181", "待修复"),
            new StatusOption("29", "已修复"),
            new StatusOption("31", "暂不修复"),
            new StatusOption("37", "无效缺陷"),
            new StatusOption("626216", "重复缺陷"),
            new StatusOption("a3b8a981c6846aaba83c05fb", "无法重现"),
            new StatusOption("06286b526d2dce6a65317390c1", "無效"),
            new StatusOption("69987134171de0812e10471811", "待验收"),
            new StatusOption("7cc042b7243376f0a43d411384", "验收中"),
            new StatusOption("ecc508b3efb9eb8fd1caec5227", "已阻塞"),
            new StatusOption("152767", "发布中"),
            new StatusOption("602481", "发布完成"),
            new StatusOption("100014", "已完成"),
            new StatusOption("33", "已关闭（已修复）"),
            new StatusOption("100085", "已关闭"),
            new StatusOption("9a445060f6f2bdd80f8f309cc8", "已撤回"),
            new StatusOption("141230", "已取消"),
            new StatusOption("013a823767244591639ea5a7", "已关闭（未修复）"));

    private static final Map<String, String> LABELS = OPTIONS.stream()
            .collect(Collectors.toUnmodifiableMap(StatusOption::value, StatusOption::label));

    private YunxiaoDefectStatusCatalog() {
    }

    public static String labelOf(String statusId) {
        return statusId == null ? null : LABELS.get(statusId);
    }

    public record StatusOption(String value, String label) {
    }
}
