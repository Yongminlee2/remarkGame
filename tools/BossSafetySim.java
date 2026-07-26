import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 보스 단어제약 규칙별로, 사전에서 "그 규칙을 통과하는 단어가 하나도 없는 시작 글자"가
 * 몇 개인지 센다. app/src/main/assets/dict_all.txt (UTF-8, 한 줄에 한 단어)를 읽는다.
 *
 * 대상 규칙 (BossRule.kt와 동일한 판정):
 *   MIN_LEN_3            : word.length >= 3
 *   MIN_LEN_4            : word.length >= 4
 *   ENDS_WITH_JONGSEONG  : (마지막 글자 - 0xAC00) % 28 != 0
 *
 * 두음법칙(Dueum)은 여기서 모델링하지 않는다 — 사전에 이미 실려있는 표기 그대로,
 * 단순히 "시작 글자별" 카운트만 한다.
 *
 * GameEngine.hasAnyCandidate가 이미 보스 규칙을 인지해서, 후보가 0이면 AI가 항복
 * 처리된다(플레이어가 억울하게 지지 않음). 따라서 이 시뮬레이션이 세는 "0인 시작 글자
 * 수"는 버그가 아니라 "보스가 얼마나 자주 그냥 항복으로 싱겁게 끝나는지"를 보기 위한
 * 지표다.
 */
public class BossSafetySim {

    static boolean minLen3(String w) { return w.length() >= 3; }
    static boolean minLen4(String w) { return w.length() >= 4; }
    static boolean endsWithJongseong(String w) {
        char c = w.charAt(w.length() - 1);
        int code = c - 0xAC00;
        if (code < 0 || code > 11171) return false;
        return code % 28 != 0;
    }

    public static void main(String[] args) throws IOException {
        Path dictPath = Paths.get("app/src/main/assets/dict_all.txt");
        List<String> words = Files.readAllLines(dictPath, StandardCharsets.UTF_8);

        // 시작 글자별로 각 규칙을 통과하는 단어가 있는지 여부만 추적
        Map<Character, Boolean> passMinLen3 = new LinkedHashMap<>();
        Map<Character, Boolean> passMinLen4 = new LinkedHashMap<>();
        Map<Character, Boolean> passJongseong = new LinkedHashMap<>();

        int total = 0;
        for (String w : words) {
            if (w.isEmpty()) continue;
            total++;
            char first = w.charAt(0);

            passMinLen3.putIfAbsent(first, false);
            passMinLen4.putIfAbsent(first, false);
            passJongseong.putIfAbsent(first, false);

            if (minLen3(w)) passMinLen3.put(first, true);
            if (minLen4(w)) passMinLen4.put(first, true);
            if (endsWithJongseong(w)) passJongseong.put(first, true);
        }

        System.out.println("=== 보스 규칙 안전성 (사전 " + total + "단어) ===");
        report("MIN_LEN_3 (3글자 이상)", passMinLen3);
        report("MIN_LEN_4 (4글자 이상)", passMinLen4);
        report("ENDS_WITH_JONGSEONG (받침으로 끝남)", passJongseong);
    }

    static void report(String label, Map<Character, Boolean> byFirst) {
        int totalFirsts = byFirst.size();
        int zeroCount = 0;
        List<Character> zeroChars = new ArrayList<>();
        for (Map.Entry<Character, Boolean> e : byFirst.entrySet()) {
            if (!e.getValue()) {
                zeroCount++;
                zeroChars.add(e.getKey());
            }
        }
        double pct = totalFirsts == 0 ? 0.0 : (100.0 * zeroCount / totalFirsts);
        System.out.printf("%s: 시작글자 %d개 중 통과단어 0개인 글자 %d개 (%.2f%%)%n",
            label, totalFirsts, zeroCount, pct);
        if (zeroCount > 0 && zeroCount <= 40) {
            StringBuilder sb = new StringBuilder();
            for (char c : zeroChars) sb.append(c).append(' ');
            System.out.println("  -> " + sb.toString().trim());
        } else if (zeroCount > 40) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 40; i++) sb.append(zeroChars.get(i)).append(' ');
            System.out.println("  -> (첫 40개) " + sb.toString().trim() + " ...");
        }
    }
}
