import java.util.*;

/** 레벨 곡선·스테이지 곡선·코인 경제가 스펙의 수용 기준을 만족하는지 확인한다. */
public class BalanceSim {

    static int xpForNextLevel(int level) {
        return (int) (50.0 * Math.pow(level, 1.2));
    }

    static int levelForTotalXp(int totalXp) {
        int level = 1, remain = totalXp, need = xpForNextLevel(1);
        while (level < 99 && remain >= need) {
            remain -= need;
            level++;
            need = xpForNextLevel(level);
        }
        return level;
    }

    static int stageTimer(int n) { return Math.max(8, 30 - n / 3); }
    static int stageTarget(int n) { return 3 + n / 3; }
    static String stageAi(int n) {
        if (n <= 5) return "매우쉬움";
        if (n <= 15) return "쉬움";
        if (n <= 30) return "보통";
        return "어려움";
    }
    static int stageXp(int n, boolean boss) {
        int base = n * 10 + 50;
        return boss ? base * 3 : base;
    }
    static int stageCoins(int n, boolean boss) {
        int c = 10 + n * 2;
        return boss ? c * 3 : c;
    }

    public static void main(String[] args) {
        System.out.println("=== 스테이지 곡선 ===");
        System.out.println("스테이지\t제한시간\tAI\t목표라운드\t보스");
        for (int n : new int[]{1, 5, 10, 15, 20, 25, 30, 31, 40, 50, 60, 66, 80, 100}) {
            System.out.printf("%d\t%d초\t%s\t%d\t%s%n",
                n, stageTimer(n), stageAi(n), stageTarget(n), n % 5 == 0 ? "O" : "");
        }

        System.out.println("\n=== 레벨 곡선 (모험만 플레이했을 때) ===");
        System.out.println("도달레벨\t누적판수\t그때의스테이지");
        int xp = 0, games = 0, stage = 1, shownLevel = 1;
        int[] milestones = {10, 20, 30, 50, 70, 99};
        int mi = 0;
        while (mi < milestones.length && games < 100000) {
            boolean boss = stage % 5 == 0;
            xp += stageXp(stage, boss);
            games++;
            stage++;
            int lv = levelForTotalXp(xp);
            while (mi < milestones.length && lv >= milestones[mi]) {
                System.out.printf("Lv%d\t%d판\t%d스테이지%n", milestones[mi], games, stage);
                mi++;
            }
        }

        System.out.println("\n=== 코인 경제 (하루 5판 x 30일, 모험 진행) ===");
        int coins = 0; stage = 1;
        for (int day = 1; day <= 30; day++) {
            for (int g = 0; g < 5; g++) {
                boolean boss = stage % 5 == 0;
                coins += stageCoins(stage, boss);
                stage++;
            }
            coins += 120; // 일일 미션 3개 + 보너스 대략치
            if (day % 7 == 0) coins += 100; // 주간 출석
            if (day == 1 || day == 7 || day == 14 || day == 30) {
                System.out.printf("%d일차: %d코인 (%d스테이지) — 일반아바타 %d개 살 수 있음%n",
                    day, coins, stage, coins / 200);
            }
        }
    }
}
