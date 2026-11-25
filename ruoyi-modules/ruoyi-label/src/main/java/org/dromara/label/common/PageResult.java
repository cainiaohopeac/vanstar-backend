package org.dromara.label.common;

import java.util.List;
import lombok.Data;

/** Paginated response wrapper. */
@Data
public class PageResult<T> {

  private long total;
  private List<T> list;

  public PageResult() {}

  public PageResult(long total, List<T> list) {
    this.total = total;
    this.list = list;
  }

  public static <T> PageResult<T> of(List<T> list) {
    return new PageResult<>(list.size(), list);
  }
}
