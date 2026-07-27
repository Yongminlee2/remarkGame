import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 이어가기 방식별 안전성 측정.
 *
 * 끝말잇기는 "○로 시작하는 단어"를 찾지만, 앞말잇기는 "○로 끝나는 단어"를 찾는다.
 * 한국어는 단어가 시작하는 글자보다 끝나는 글자가 훨씬 편중돼 있어서
 * 앞말잇기가 훨씬 자주 막힐 수 있다. 실제로 얼마나 막히는지 센다.
 *
 * 기준: 앞 단어가 정해지면 다음에 맞춰야 할 음절이 하나 정해진다.
 * 그 음절로 이을 수 있는 단어가 0개면 그 판은 그 자리에서 끝난다.
 */
public class ChainSafetySim {

    public static void main(String[] args) throws Exception {
        List<String> words = new ArrayList<>(440000);
        for (String line : Files.readAllLines(
                Paths.get("app/src/main/assets/dict_all.txt"), StandardCharsets.UTF_8)) {
            String w = line.trim();
            if (w.length() >= 2) words.add(w);
        }

        Map<Character, Integer> startCount = new HashMap<>();
        Map<Character, Integer> endCount = new HashMap<>();
        for (String w : words) {
            startCount.merge(w.charAt(0), 1, Integer::sum);
            endCount.merge(w.charAt(w.length() - 1), 1, Integer::sum);
        }

        System.out.printf("사전 %,d단어%n", words.size());
        System.out.printf("첫 글자로 쓰이는 음절 %,d종 · 끝 글자로 쓰이는 음절 %,d종%n%n",
                startCount.size(), endCount.size());

        // ── 끝말잇기(TAIL): 앞 단어의 끝 글자로 시작하는 단어가 필요 ──
        // ── 같은글자(SAME_HEAD): 앞 단어의 첫 글자로 시작하는 단어가 필요 ──
        // ── 앞말잇기(HEAD): 앞 단어의 첫 글자로 끝나는 단어가 필요 ──
        check("끝말잇기   (끝글자 → 시작)", words, true, startCount);
        check("같은글자로 (첫글자 → 시작)", words, false, startCount);
        check("앞말잇기   (첫글자 → 끝)  ", words, false, endCount);
    }

    /**
     * @param useLastSyllable 앞 단어에서 끝 글자를 쓰는지(true) 첫 글자를 쓰는지(false)
     * @param supply          이어붙일 수 있는 단어 수를 담은 맵(시작 기준 또는 끝 기준)
     */
    static void check(String name, List<String> words, boolean useLastSyllable,
                      Map<Character, Integer> supply) {
        int dead = 0, thin = 0;
        long deadMass = 0;
        // "앞 단어"가 될 수 있는 모든 단어를 훑어, 그 다음 차례가 막히는지 본다.
        for (String w : words) {
            char anchor = useLastSyllable ? w.charAt(w.length() - 1) : w.charAt(0);
            int n = supply.getOrDefault(anchor, 0);
            if (n == 0) { dead++; deadMass++; }
            else if (n < 5) thin++;
        }
        double deadPct = 100.0 * dead / words.size();
        double thinPct = 100.0 * thin / words.size();
        String flag = deadPct >= 5 ? "  ← 위험" : (deadPct >= 1 ? "  ← 주의" : "  ← 양호");
        System.out.printf("%s  막힘 %,7d (%5.2f%%)   후보5개미만 %,7d (%5.2f%%)%s%n",
                name, dead, deadPct, thin, thinPct, flag);
    }
}
