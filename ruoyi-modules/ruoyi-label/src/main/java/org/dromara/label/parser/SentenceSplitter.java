package org.dromara.label.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 文本分句：把正文切成句，产出全文级字符偏移边界。
 *
 * <p>句边界有三个用途：前端按句分页渲染（长文本不可能整篇塞进 DOM）、
 * 句级情感标注定位、审核时的「句子跳转」。
 *
 * <p>规则：
 * <ul>
 *   <li>中文终止符 。！？；… 后断句（连续终止符合并为一句）；</li>
 *   <li>换行同样断句——多数语料没有规范标点，靠换行分句是主要手段；</li>
 *   <li>英文 . ! ? 后跟空白才断句，并跳过常见缩写（Mr. / e.g. / Inc. 等），
 *       否则 "Mr. Smith" 会被切成两句；</li>
 *   <li>末尾无终止符也补齐最后一句，保证边界覆盖全文。</li>
 * </ul>
 */
public final class SentenceSplitter {

  private SentenceSplitter() {
  }

  /** 中文终止符。 */
  private static final Set<Character> CN_TERMINATORS =
      Set.of('。', '！', '？', '；', '…', '‥');

  /** 英文终止符。 */
  private static final Set<Character> EN_TERMINATORS = Set.of('.', '!', '?');

  /** 常见英文缩写（小写比对）：这些点号不表示句末。 */
  private static final Set<String> ABBREVIATIONS =
      Set.of("mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
          "e.g", "i.e", "inc", "ltd", "co", "corp", "dept", "fig", "no", "vol");

  /**
   * 分句。
   *
   * @param text 正文（可为 null / 空）
   * @return 每个元素为 {@code [start(含), end(不含)]} 的全文级偏移，按句序排列
   */
  public static List<int[]> split(String text) {
    List<int[]> sentences = new ArrayList<>();
    if (text == null || text.isEmpty()) {
      return sentences;
    }
    int len = text.length();
    int start = 0;
    for (int i = 0; i < len; i++) {
      char c = text.charAt(i);
      if (CN_TERMINATORS.contains(c) || c == '\n') {
        // 吃掉紧随其后的连续终止符（如「！！」「。」），避免产生空句
        int end = i + 1;
        while (end < len && (CN_TERMINATORS.contains(text.charAt(end)) || text.charAt(end) == '\n'
            || text.charAt(end) == '\r')) {
          end++;
        }
        addIfNonBlank(sentences, text, start, end);
        start = end;
        i = end - 1;
      } else if (EN_TERMINATORS.contains(c)) {
        // 英文终止符：后面必须跟空白或到结尾，且不能是缩写
        boolean nextIsBoundary = i + 1 >= len || Character.isWhitespace(text.charAt(i + 1));
        if (nextIsBoundary && !isAbbreviation(text, i)) {
          int end = i + 1;
          while (end < len && (EN_TERMINATORS.contains(text.charAt(end))
              || Character.isWhitespace(text.charAt(end)))) {
            end++;
          }
          addIfNonBlank(sentences, text, start, end);
          start = end;
          i = end - 1;
        }
      }
    }
    // 补齐末尾没有终止符的剩余部分
    addIfNonBlank(sentences, text, start, len);
    return sentences;
  }

  /** 只收录含非空白字符的区间，跳过纯空白片段。 */
  private static void addIfNonBlank(List<int[]> sentences, String text, int start, int end) {
    if (end <= start) {
      return;
    }
    for (int i = start; i < end; i++) {
      if (!Character.isWhitespace(text.charAt(i))) {
        sentences.add(new int[] {start, end});
        return;
      }
    }
  }

  /**
   * 判断点号是否属于缩写：取点号前的连续字母（含内部点号以支持 e.g. 这类），
   * 长度 ≤ 4 且在缩写表中，或是单个大写字母（A. B. 这类人名缩写）。
   */
  private static boolean isAbbreviation(String text, int dotIndex) {
    int begin = dotIndex;
    while (begin > 0) {
      char prev = text.charAt(begin - 1);
      if (Character.isLetter(prev) || prev == '.') {
        begin--;
      } else {
        break;
      }
    }
    if (begin >= dotIndex) {
      return false;
    }
    String word = text.substring(begin, dotIndex);
    if (word.length() == 1 && Character.isUpperCase(word.charAt(0))) {
      return true;
    }
    return ABBREVIATIONS.contains(word.toLowerCase());
  }
}
