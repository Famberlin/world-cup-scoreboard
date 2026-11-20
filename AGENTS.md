# Agent rules

Rules for any coding agent in this repo. Requirements and decisions: `README.md`. AI usage and prompt history: `AI.md`.

## Map

```
pom.xml                  parent: Java 25, ${revision} version, test deps (JUnit 6, AssertJ), all plugin config
                         (JaCoCo 90% line gate, Spotless, flatten); modules inherit, never repeat versions
scoreboard/pom.xml       library module (artifactId world-cup-scoreboard), no runtime deps
.mvn/maven.config        -B --no-transfer-progress for every mvn call
.mvn/jvm.config          JDK exports Spotless (palantir-java-format) needs
scripts/build.sh         quiet Maven wrapper: full log to target/build.log, prints a summary only
scoreboard/src/main/java/com/sportradar/scoreboard/   library code
scoreboard/src/test/java/com/sportradar/scoreboard/   unit tests
.github/workflows/build.yml   CI: mvn verify (Spotless check-only); Snyk scan only if SNYK_TOKEN secret is set
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
- Run from the repo root (parent POM). Summary not enough: `grep -n -A15 "<TestName>" target/build.log` or
  `<module>/target/surefire-reports/<Class>.txt`. Never read the whole log. Test stdout is in
  `<module>/target/surefire-reports/*-output.txt`.
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
- Build setup lives in the parent `pom.xml`: dependency and plugin versions, test dependencies, plugin config.
  A module POM only adds what is specific to it. Version is `${revision}${sha1}${changelist}`; change `revision`
  in the parent only, never hard-code a version in a module.

## Git

- Branch from latest `main` (`git fetch origin main && git checkout -b <branch> origin/main`); rebase if behind.
- Branch names: `{feature|bugfix}/{short-feature-summary}`, summary in kebab-case, max 5-6 words
  (`feature/maven-project-scaffold`, `bugfix/summary-tie-ordering`). No other prefixes.
- Small atomic commits, imperative messages. The additional feature from the task gets its own distinct commit.
- Update `README.md` (decisions, trade-offs) and `AI.md` (prompt history) in the same change.
