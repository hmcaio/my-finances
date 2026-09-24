package com.chm.myfinances.application.networth;

/**
 * How the net worth trend is sampled (F010 spec): at every date where any underlying value changed,
 * or at each month-end (the current month at today).
 */
public enum NetWorthGranularity {
  CHANGE_DATE,
  MONTH
}
