# Agent rules

Rules for any coding agent in this repo. Requirements and decisions: `README.md`. AI usage and prompt history: `AI.md`.

## Map

```
pom.xml                  Java 25 library, no runtime deps; JUnit 6, AssertJ, JaCoCo (90% line gate), Spotless
.mvn/maven.config        -B --no-transfer-progress for every mvn call
.mvn/jvm.config          JDK exports Spotless (palantir-java-format) needs
scripts/build.sh         quiet Maven wrapper: full log to target/build.log, prints a summary only
src/main/java/com/sportradar/scoreboard/   library code
src/test/java/com/sportradar/scoreboard/   unit tests
.github/workflows/build.yml                CI: mvn verify, Spotless check-only
```

## Build and test (keep output out of context)

Always use the wrapper. Success prints one `OK` line; failure prints only failing tests with assertion messages,
or compiler/Spotless/JaCoCo error lines.

```bash
bash scripts/build.sh                                   # verify: tests, coverage gate, format check
bash scripts/build.sh test                              # unit tests only
bash scripts/build.sh test -Dtest=ScoreboardTest        # one class
bash scripts/build.sh test -Dtest='ScoreboardTest#finish*'   # matching methods
bash scripts/build.sh compile                           # compile only
```

- JDK older than 25: append `-Djava.version=<n>` (e.g. 21). Never change the pom for it.
- Summary not enough: `grep -n -A15 "<TestName>" target/build.log` or `target/surefire-reports/<Class>.txt`.
  Never read the whole log. Test stdout is in `target/surefire-reports/*-output.txt`.
- Iterate on the single failing test; run full `verify` once before committing.
- Don't re-run a build if nothing changed since it passed.
- Never read `target/`, `.idea/`, `*.iml`; search with `grep -r --exclude-dir={target,.idea,.git}`.
- Read files by line range once located; don't re-read files already in context.
- Replies: result plus failing test or error line, never full output.

## Code

- Plain Java, no frameworks, no runtime dependencies. Base package `com.sportradar.scoreboard`.
- Small, documented public API (Javadoc on public types and methods). Keep internals package-private.
- Formatting belongs to Spotless (palantir-java-format): `package`/`verify` reformat locally. Don't hand-format.
  CI is check-only (`-Dspotless.apply.skip=true`).
- Test first: add or extend a failing test before changing behaviour. Tests: JUnit 6 + AssertJ,
  method names describe behaviour (`finishedMatchIsRemovedFromSummary`).
- Coverage below 90% lines fails `verify`; add tests, never lower the threshold.

## Git

- Branch from latest `main` (`git fetch origin main && git checkout -b <branch> origin/main`); rebase if behind.
- Branch names: `{feature|bugfix}/{short-feature-summary}`, summary in kebab-case, max 5-6 words
  (`feature/maven-project-scaffold`, `bugfix/summary-tie-ordering`). No other prefixes.
- Small atomic commits, imperative messages. The additional feature from the task gets its own distinct commit.
- Update `README.md` (decisions, trade-offs) and `AI.md` (prompt history) in the same change.
