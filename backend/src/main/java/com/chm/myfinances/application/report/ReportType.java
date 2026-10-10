package com.chm.myfinances.application.report;

/**
 * The eleven curated reports (F030 spec, PRD S6.9), replacing F013's all-entity export. Each value
 * maps to one {@link ReportGenerator} bean, resolved by {@link ReportService}. {@link #slug()} is
 * the kebab-case fragment {@code POST /api/reports} uses to name each generated file ({@code
 * my-finances-<slug>-YYYY-MM-DD.<ext>}).
 */
public enum ReportType {
  TRANSACTIONS_TRANSFERS("transactions-transfers"),
  ACCOUNT_BALANCES("account-balances"),
  FII_PORTFOLIO("fii-portfolio"),
  FUEL_HISTORY("fuel-history"),
  BUDGETS_SPENDING("budgets-spending"),
  INVESTMENTS("investments"),
  NET_WORTH_TREND("net-worth-trend"),
  RECURRING_TEMPLATES("recurring-templates"),
  INVESTMENT_ALLOCATION("investment-allocation"),
  DIVIDEND_INCOME("dividend-income"),
  ANNUAL_SUMMARY("annual-summary");

  private final String slug;

  ReportType(String slug) {
    this.slug = slug;
  }

  public String slug() {
    return slug;
  }
}
