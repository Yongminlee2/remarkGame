import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 스테이지 규칙별 안전성 측정.
 *
 * 각 규칙에 대해 "그 글자로 시작하는 단어 중 규칙을 통과하는 게 하나도 없는" 시작 글자를 센다.
 * 통과 후보가 0이면 AI 항복으로 처리되므로 버그는 아니지만, 이 비율이 높으면
 * 판이 자주 허무하게 끝난다는 뜻이라 규칙을 빼거나 완화해야 한다.
 *
 * 글자 수 비율과 함께 "단어량 비율"도 같이 본다 — 막히는 글자가 전부 희귀 음절이면
 * 실제로 걸릴 일이 거의 없기 때문이다.
 */
public class RuleSafetySim {

    interface Rule { boolean ok(String w); }

    static boolean hasJong(char c) {
        int code = c - 0xAC00;
        if (code < 0 || code > 11171) return false;
        return code % 28 != 0;
    }

    public static void main(String[] args) throws Exception {
        Path dict = Paths.get("app/src/main/assets/dict_all.txt");
        List<String> words = new ArrayList<>(440000);
        for (String line : Files.readAllLines(dict, StandardCharsets.UTF_8)) {
            String w = line.trim();
            if (w.length() >= 2) words.add(w);
        }

        // 첫 글자별로 묶기
        Map<Character, List<String>> byFirst = new HashMap<>();
        for (String w : words) byFirst.computeIfAbsent(w.charAt(0), k -> new ArrayList<>()).add(w);

        LinkedHashMap<String, Rule> rules = new LinkedHashMap<>();
        rules.put("EXACT_LEN_2  두 글자만",        w -> w.length() == 2);
        rules.put("EXACT_LEN_3  세 글자만",        w -> w.length() == 3);
        rules.put("EXACT_LEN_4  네 글자만",        w -> w.length() == 4);
        rules.put("MIN_LEN_3    3글자 이상",       w -> w.length() >= 3);
        rules.put("MIN_LEN_4    4글자 이상",       w -> w.length() >= 4);
        rules.put("MIN_LEN_5    5글자 이상",       w -> w.length() >= 5);
        rules.put("ENDS_JONG    받침으로 끝",      w -> hasJong(w.charAt(w.length() - 1)));
        rules.put("NO_JONG      받침 없이 끝",     w -> !hasJong(w.charAt(w.length() - 1)));

        System.out.printf("사전 %,d단어 · 서로 다른 시작 글자 %,d개%n%n", words.size(), byFirst.size());
        System.out.println("규칙                          막힌글자   글자비율   단어량비율   전체통과단어");
        System.out.println("-".repeat(78));

        for (Map.Entry<String, Rule> e : rules.entrySet()) {
            Rule r = e.getValue();
            int deadSyllables = 0;
            long deadWordMass = 0;
            long passing = 0;

            for (Map.Entry<Character, List<String>> g : byFirst.entrySet()) {
                boolean any = false;
                for (String w : g.getValue()) {
                    if (r.ok(w)) { any = true; passing++; }
                }
                if (!any) {
                    deadSyllables++;
                    deadWordMass += g.getValue().size();
                }
            }

            double sylPct = 100.0 * deadSyllables / byFirst.size();
            double massPct = 100.0 * deadWordMass / words.size();
            String flag = massPct >= 2.0 ? "  ← 위험" : (massPct >= 0.5 ? "  ← 주의" : "");

            System.out.printf("%-28s %6d   %6.1f%%   %8.2f%%   %,10d%s%n",
                    e.getKey(), deadSyllables, sylPct, massPct, passing, flag);
        }

        System.out.println();
        System.out.println("판정 기준: 단어량 비율이 2% 이상이면 실제 플레이에서 자주 막힌다는 뜻.");
        System.out.println("0.5% 미만이면 해당 음절이 희귀·고어라 사실상 걸리지 않는다.");
    }
}
