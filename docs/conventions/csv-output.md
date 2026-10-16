# Report generators and CSV output (backend, F030)

Read when adding or changing a `ReportGenerator`, `CsvWriter`/`CsvSectionWriter` use, or a repository call that returns an unpaged list.

F030 supersedes F013's data export. Each `ReportGenerator` reads through the existing ports/queries only (no report-specific persistence).

- **Unpaged reads**: `TransactionRepository.findAll(filter, Pageable.unpaged())`-style calls need any `Fake*Repository` to handle `isUnpaged()`.
- **Amounts, dates and ids keep their own types**: `CsvWriter` (`application/report`) guards against spreadsheet formulas only for `String` cells (prefix `'`), so a pre-formatted string would turn a negative amount into text.
- **Nullable cells**: `CsvSectionWriter.Section.rows()` is `List<Object>` for exactly that reason. Build a row with `Arrays.asList(...)`, never `List.of(...)`, whenever any cell can be `null` (an optional amount, a snapshot date that may not exist yet): `List.of()` throws `NullPointerException` on a `null` element, which `Arrays.asList()` permits.
