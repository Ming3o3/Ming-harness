package org.mingharness.tool;

/** 跨平台工作区命令测试夹具；通过 Java 可执行文件启动，不依赖 Unix shell 或 POSIX 权限。 */
final class WorkspaceExecFixture {

    private WorkspaceExecFixture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("缺少测试模式");
        }
        switch (args[0]) {
            case "echo" -> echo(args);
            case "slow" -> {
                Thread.sleep(2_000);
                System.out.println("late");
            }
            case "noisy" -> {
                for (int index = 0; index < 1_000; index++) {
                    System.out.print("0123456789");
                }
            }
            default -> throw new IllegalArgumentException("未知测试模式: " + args[0]);
        }
    }

    private static void echo(String[] args) {
        String value = args.length > 1 ? args[1] : "";
        String apiKey = System.getenv("MODEL_API_KEY");
        System.out.print("value:" + value + ":" + (apiKey == null || apiKey.isBlank() ? "missing" : apiKey));
    }
}
