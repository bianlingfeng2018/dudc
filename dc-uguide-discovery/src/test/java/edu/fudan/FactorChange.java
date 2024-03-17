package edu.fudan;

public class FactorChange {
    public static void main(String[] args) {
        double initialFactor = 0.001;
        double targetFactor = 0.000001;
        int numChanges = 50;

        // 计算总变化量
        double totalChange = targetFactor - initialFactor;

        // 计算每次变化的平均量
        double averageChange = totalChange / numChanges;

        // 计算每次变化的比率
        double rateOfChange = Math.pow(targetFactor / initialFactor, 1.0 / numChanges);

        System.out.println("每次变化的平均量: " + averageChange);
        System.out.println("每次变化的比率: " + rateOfChange);

        // 输出每次变化后的因子值
        double currentFactor = initialFactor;
        for (int i = 0; i < numChanges; i++) {
            currentFactor *= rateOfChange;
            System.out.println("第 " + (i + 1) + " 次变化后的因子值: " + currentFactor);
        }
    }
}
