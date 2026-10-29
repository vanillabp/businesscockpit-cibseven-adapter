![VanillaBP](./readme/vanillabp-headline.png)

# VanillaBP Business Cockpit adapter for CIB seven

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

Spring Boot [![Coverage](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fvanillabp.github.io%2Fbusinesscockpit-cibseven-adapter%2Fspring-boot-report%2Findex.html&search=Total.*%3F.([0-9]%2B)[^0-9]*%3F%25&replace=%241%25&flags=m&label=Coverage&color=green&cacheSeconds=60)](https://vanillabp.github.io/businesscockpit-cibseven-adapter/spring-boot-report)<br>
Quarkus [![Coverage](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fvanillabp.github.io%2Fbusinesscockpit-cibseven-adapter%2Fquarkus-report%2Findex.html&search=Total.*%3F.([0-9]%2B)[^0-9]*%3F%25&replace=%241%25&flags=m&label=Coverage&color=green&cacheSeconds=60)](https://vanillabp.github.io/businesscockpit-cibseven-adapter/quarkus-report)

This repository holds the [VanillaBP Business Cockpit](https://github.com/vanillabp/business-cockpit)
integration for [CIB seven](https://cibseven.org), built as an extension of
[VanillaBP](https://www.vanillabp.io) Version 2. The cockpit shows user tasks and business cases
to business staff, so it has to learn what happens inside the workflow engine. This adapter is the
half which runs in the workflow application. It watches the user-task and workflow lifecycle in the
embedded CIB seven engine, asks the application for the business details of what it saw, and hands
the result to the cockpit server.

CIB seven is a fork of Camunda 7. It kept the API and moved the packages from `org.camunda.bpm` to
`org.cibseven.bpm`. This repository is made from the
[Business Cockpit adapter for Camunda 7](https://github.com/vanillabp/businesscockpit-camunda7-adapter),
and it follows that repository by a merge, see [How the code is made](#how-the-code-is-made).

## Status

The extension runs on both platforms against a real embedded CIB seven engine. It builds on the
extension SPI of `adapter-platform-integration`, on the cockpit's `extensions-commons` and on the
[VanillaBP CIB seven adapter](https://github.com/vanillabp/cibseven-adapter). All of them are still
snapshots, so this repository is one too.

It is built and tested with `io.vanillabp:cibseven-adapter` 2.0.0-2.2-SNAPSHOT and CIB seven 2.2.0.
The adapter carries the CIB seven minor in its version, and this extension pins the same CIB seven
release the adapter pins (decision 2 in the repository's [`DECISIONS.md`](./DECISIONS.md)).

## What is here

The extension is split the way every VanillaBP adapter repository is split. The artifacts keep the
repository name as their prefix, so a jar of this repository is never mistaken for a jar of the
VanillaBP CIB seven adapter it plugs into.

|         Module         |                       Artifact                        |                                   What is in it                                    |
|------------------------|-------------------------------------------------------|------------------------------------------------------------------------------------|
| `core`                 | `businesscockpit-cibseven-adapter`                    | Everything which needs neither Spring nor Quarkus                                  |
| `spring-boot`          | `businesscockpit-cibseven-adapter-spring-boot`        | One auto-configuration, one bean registrar, the tests against a booted application |
| `quarkus/runtime`      | `businesscockpit-cibseven-adapter-quarkus`            | One CDI producer doing what the auto-configuration does                            |
| `quarkus/deployment`   | `businesscockpit-cibseven-adapter-quarkus-deployment` | One build step, and the tests running the extension inside a Quarkus application   |
| `migration-tests/...`  | not published                                         | This extension next to the Camunda 7 extension in one application, per platform    |
| `test-coverage-report` | not published                                         | The per-platform JaCoCo aggregation and the gate judging it                        |

What the core does, class by class:

- `CibSevenCockpitWiring` takes part in VanillaBP's deployment pipeline for a workflow module which
  runs on CIB seven, and remembers which BPMN processes were deployed.
- `CibSevenWorkflowProcesses` is that memory. It is also the way back: an engine reports
  identifiers of its own, and this class says which workflow module and which plain process id the
  application meant by them.
- `CibSevenCockpitCustomizer` is what the CIB seven adapter asks per configured adapter id. It
  contributes the parse listener and the history event handler of that engine. It also ends the boot
  of an engine whose history level writes none of what the cockpit reads back.
- `CibSevenUserTaskParseListener` attaches `CibSevenUserTaskListener` to every user task, as a
  built-in listener of all four task events.
- `CibSevenWorkflowHistoryHandler` turns the engine's process-instance history events into the
  cockpit's workflow events.
- `CibSevenCockpitEvents` is what both of them report through. It builds the identifiers and hands
  the event to the cockpit. The cockpit builds the report right away and writes one outbox entry.
- `CibSevenEventBeingReported` holds what the engine handed over while that report is built. It is
  how the bridge answers out of the event instead of querying an engine which cannot answer yet.
- `CibSevenCockpitBridge` answers everything the cockpit asks about a task or a workflow. At an
  event it answers out of the event. Where the application asks, through `BusinessCockpitService`,
  it queries the engine for the state of now.
- `CibSevenScope` is how the extension asks what an engine calls things, rather than building a
  prefix or a tenant of its own. Two of those answers come from the CIB seven adapter: the tenant a
  workflow module was deployed under, and whether the engine's work runs in the caller's
  transaction.

The reasons for these classes are the decisions of the Camunda 7 extension. This repository's
[`DECISIONS.md`](./DECISIONS.md) says so in its first entry and adds what is different here. What a
user of this extension has to know is in the
[wiki](https://github.com/vanillabp/businesscockpit-cibseven-adapter/wiki).

## What is deliberately absent

The same as in the Camunda 7 extension. This extension rewrites no model: a listener is attached
while the engine parses a model, so the BPMN in your repository and the BPMN the engine gets are the
same. It has no persistence of its own. What has to survive a crash is the outbox entry VanillaBP
already provides. And it has no configuration key of its own. What it has to know about an engine
it asks the CIB seven adapter. So the Spring Boot jar ships no configuration metadata.

## Running next to the Camunda 7 extension

An application moves from Camunda 7 to CIB seven with both adapters in one application: new
workflows start on CIB seven, and the old ones finish on Camunda 7. The cockpit then needs both
extensions, this one and the Camunda 7 one. They have different names everywhere, so they do not
get in each other's way (decision 4).

Give each engine a datasource of its own. Two engines on one set of tables are refused. On Quarkus
both datasources have to be XA datasources (`quarkus.datasource.<name>.jdbc.transactions: xa`),
because VanillaBP's outbox starts a workflow on the second engine in a transaction which also writes
the application's datasource (decision 5). Camunda 7.24 and CIB seven 2.2 bring the same FEEL
engine, so nothing has to be excluded.

The module `migration-tests` does exactly this, once per platform. `CibSevenNextToCamunda7Test` runs
a user task on each engine and checks that the cockpit hears of each of them once, through the
extension of its own engine.

An application can also switch the engine in place, on the same database. The
[README of the CIB seven adapter](https://github.com/vanillabp/cibseven-adapter#migrating-from-camunda-7)
describes that way. For the cockpit it means swapping the Camunda 7 extension for this one, together
with the adapter.

## Building

```bash
mvn install
```

`install` and not `install verify`: `install` runs every phase `verify` has, and naming both walks
two lifecycles per module. It has to be `install` rather than `package`, because the Quarkus tests
load the modules of this repository from the local Maven repository.

The tests boot real applications on both platforms: a Spring Boot context and a Quarkus application,
each with an embedded CIB seven engine on H2, a cockpit server the test runs itself and the outbox
in between. No BPMS double and no mock of the engine.

The build reads the snapshots of the platform, of the cockpit and of the CIB seven adapter from the
snapshot repository of Maven Central, `https://central.sonatype.com/repository/maven-snapshots/`.
The parent `io.vanillabp:release-parent` names that repository, and reading it needs no login.

`migration-tests` is the exception. It needs the Camunda 7 adapter, whose snapshots are only in the
GitHub Packages registry of the Camunda Community Hub. GitHub Packages asks for a token even for a
public package. [`CONTRIBUTING.md`](./CONTRIBUTING.md#snapshots) says how to give your build one,
and how to build without that module.

Snapshots of this repository go to the snapshot repository of Maven Central through the pipeline
described below, and releases go to Maven Central under the groupId `io.vanillabp.businesscockpit`,
like the rest of the Business Cockpit.

## How the code is made

Nearly all code of this repository is made by a script from the Business Cockpit adapter for
Camunda 7. `bin/sync-from-camunda7.sh` copies a commit of that repository and runs the OpenRewrite
recipe `rewrite/vanillabp-cibseven.yml` over it. The recipe renames the engine's packages and jar,
pins the CIB seven version and writes this extension's own names. The result goes to the branch
`generated`, and `main` merges it. The newest commit on `generated` names the commit of the Camunda 7
extension it was made from: `generated from BC C7 <sha>`. Whatever this repository does differently
sits on `main` on top. [`CONTRIBUTING.md`](./CONTRIBUTING.md) has the procedure, and decision 3 has
the reasons.

To make the code again from a commit of the Camunda 7 extension:

```bash
bin/sync-from-camunda7.sh <commit of businesscockpit-camunda7-adapter>
```

The workflow `sync-from-camunda7.yaml` does the same every night with `main` of the Camunda 7
extension, and opens a pull request when the result changed.

So a change to a file which came from the Camunda 7 extension belongs there first. A fix made only
here is lost at the next sync, or it meets a conflict.

## Test coverage

`mvn install` writes one aggregated JaCoCo report per platform:

1. Spring Boot, over `core` and `spring-boot`, into `test-coverage-report/spring-boot/report`
2. Quarkus, over `core`, `quarkus/runtime` and `quarkus/deployment`, into
   `test-coverage-report/quarkus/report`

Coverage is measured separately per platform, because the tests of one platform never run the
other platform's code. The core is in both reports. If one platform never reaches a class of the
core, that platform never runs the feature of that class. `migration-tests` measures nothing, see
decision 5.

The last module of the reactor is `test-coverage-report/coverage-gate`. It reads both reports and
breaks the build when a platform is below its threshold in the root POM:
`coverage.threshold.spring-boot` and `coverage.threshold.quarkus`, both 85 percent of covered
instructions. The rule is `coverage.rule`, 90 per platform, so a report between 85 and 90 passes
the build and still names a gap. The gate also checks that every module which writes coverage data
is part of a report. It prints what it measured on every run.

## What CI runs

`build.yaml` builds and tests a pull request, in a group per pull request.

`publish-snapshots.yaml` publishes the snapshot, and only for a push to `main`. It runs in a group
of its own, one publish at a time, and a publish which is already running is never cancelled. It
also publishes the two coverage reports to GitHub Pages, and the badges at the top of this page link
there.

`release.yaml` is started by hand and publishes to Maven Central from a release branch.

`nightly-build.yaml` runs `build.yaml` on `main` once a night, with `--update-snapshots`. So a
`main` which a new snapshot of the platform, of the cockpit or of the CIB seven adapter breaks is
seen, even when nobody pushed here. A red night becomes one issue with the label `nightly-build`.

`sync-from-camunda7.yaml` makes the code again from `main` of the Camunda 7 extension once a night.
When the result differs from `generated`, it pushes `generated`, merges it into a branch from
`main`, builds that branch and opens a pull request. It can also be started by hand for any commit
or tag.

Every workflow reads the Camunda 7 adapter for `migration-tests` with the repository secrets
`VANILLABP_USER_NAME` and `VANILLABP_USER_TOKEN`, a token with `read:packages`.

## Noteworthy & Contributors

[VanillaBP](https://www.github.com/vanillabp/spi-for-java) was developed by
[Phactum](https://www.phactum.at) with the intention of giving back to the community as it has
benefited the community in the past.

![Phactum](./readme/phactum.png)

## License

Copyright 2026 Phactum Softwareentwicklung GmbH

Licensed under the Apache License, Version 2.0
