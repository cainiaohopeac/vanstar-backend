package org.dromara.label.common;

/**
 * 文本情感分类的粒度（label_text_sentiment.level_type）。
 *
 * <p>条目级：整篇文本一个情感结论；句级：逐句各一个结论。
 * 两者并存——条目级用于快速归档，句级用于细粒度情感分析。
 */
public final class TextSentimentLevel {

  private TextSentimentLevel() {
  }

  /** 条目级（整篇文本） */
  public static final String ENTRY = "entry";
  /** 句级（按句边界划分） */
  public static final String SENTENCE = "sentence";
}
